package com.adiraimaji.amcallassistant.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.adiraimaji.amcallassistant.diag.ops.AdbdOp
import com.adiraimaji.amcallassistant.diag.ops.AnswerOp
import com.adiraimaji.amcallassistant.diag.ops.AnsweredOp
import com.adiraimaji.amcallassistant.diag.ops.AppCallOp
import com.adiraimaji.amcallassistant.diag.ops.ArmAdbOp
import com.adiraimaji.amcallassistant.diag.ops.ArmTestOp
import com.adiraimaji.amcallassistant.diag.ops.AudioCheckOp
import com.adiraimaji.amcallassistant.diag.ops.BootstrapOp
import com.adiraimaji.amcallassistant.diag.ops.CallReportOp
import com.adiraimaji.amcallassistant.diag.ops.CheckOp
import com.adiraimaji.amcallassistant.diag.ops.ConnectOp
import com.adiraimaji.amcallassistant.diag.ops.DaemonOp
import com.adiraimaji.amcallassistant.diag.ops.DemoOp
import com.adiraimaji.amcallassistant.diag.ops.DisconnectOp
import com.adiraimaji.amcallassistant.diag.ops.ExecOp
import com.adiraimaji.amcallassistant.diag.ops.FinishOp
import com.adiraimaji.amcallassistant.diag.ops.ForgetKicksOp
import com.adiraimaji.amcallassistant.diag.ops.GrantListenerOp
import com.adiraimaji.amcallassistant.diag.ops.EarpieceOp
import com.adiraimaji.amcallassistant.diag.ops.KeepPairedOp
import com.adiraimaji.amcallassistant.diag.ops.HideSettingsOp
import com.adiraimaji.amcallassistant.diag.ops.HoldSaveOp
import com.adiraimaji.amcallassistant.diag.ops.ClearNotesOp
import com.adiraimaji.amcallassistant.diag.ops.MonitorOp
import com.adiraimaji.amcallassistant.diag.ops.MonitorStopOp
import com.adiraimaji.amcallassistant.diag.ops.PairNotifOp
import com.adiraimaji.amcallassistant.diag.ops.PairOp
import com.adiraimaji.amcallassistant.diag.ops.OldPagesOp
import com.adiraimaji.amcallassistant.diag.ops.PcmHashOp
import com.adiraimaji.amcallassistant.diag.ops.RepageOp
import com.adiraimaji.amcallassistant.diag.ops.PortOp
import com.adiraimaji.amcallassistant.diag.ops.PrefOp
import com.adiraimaji.amcallassistant.diag.ops.ProbeOp
import com.adiraimaji.amcallassistant.diag.ops.RefuseGrantOp
import com.adiraimaji.amcallassistant.diag.ops.ReviveLaterOp
import com.adiraimaji.amcallassistant.diag.ops.SelfTestOp
import com.adiraimaji.amcallassistant.diag.ops.SettingsOp
import com.adiraimaji.amcallassistant.diag.ops.StatusOp
import com.adiraimaji.amcallassistant.diag.ops.StoreOp
import com.adiraimaji.amcallassistant.diag.ops.SwitchOp
import com.adiraimaji.amcallassistant.diag.ops.TalkOp
import com.adiraimaji.amcallassistant.diag.ops.TestResultOp
import com.adiraimaji.amcallassistant.diag.ops.TrashOp
import com.adiraimaji.amcallassistant.diag.ops.VoipAnswerOp
import com.adiraimaji.amcallassistant.diag.ops.VolumeOp
import com.adiraimaji.amcallassistant.diag.ops.WaveformOp
import com.adiraimaji.amcallassistant.diag.ops.report
import com.adiraimaji.amcallassistant.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * Debug-only entry point that makes the transport checks scriptable from a
 * USB shell, so the three network states that matter can be tested without
 * anyone tapping through the UI in each one.
 *
 * WHY THERE IS A TOKEN
 *
 * This receiver runs arbitrary shell commands through an ADB connection that
 * already holds shell privilege. Exported, that is a local privilege
 * escalation: any installed app could broadcast to it. It is exported anyway
 * because `am broadcast` cannot reach a non-exported component, and a
 * non-scriptable diagnostic defeats the purpose.
 *
 * The guard is a random token stored on first use and printed to logcat.
 * Since Android 10 an app can read only its OWN logcat, while adb can read all
 * of it - so possession of the token is a decent proxy for "is a USB shell".
 * Send any command without a token and the receiver logs the expected value and
 * refuses; that bootstraps the caller without ever exposing the token to
 * another app.
 *
 *   adb shell am broadcast -n com.adiraimaji.amcallassistant/.diag.SpikeReceiver --es op selftest
 *   adb logcat -d -s AM Call Assistant | grep 'diag token'
 *   adb shell am broadcast -n com.adiraimaji.amcallassistant/.diag.SpikeReceiver \
 *       --es token <t> --es op selftest
 *
 * BY COMPONENT, not by action: this receiver declares no intent-filter, so
 * `am broadcast -a com.adiraimaji.amcallassistant.DIAG` resolves to nothing and reports
 * success while delivering to no one.
 *
 * AND IT IS NOT RELIABLE ON THIS PHONE. Honor's iAware drops broadcasts to a
 * manifest receiver whose app it has decided is idle - measured here as
 * "Enqueued broadcast ... : 0" in the system log with the receiver never
 * running, the same behaviour that stops the app being woken for a call. Start
 * the app first and it usually lands. Anything that must not be flaky writes
 * the preference directly with `run-as` instead; tools/shoot.py does.
 */
class SpikeReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "AMCallAssistant"
        const val KEY_TOKEN = "token"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        val expected = tokenFor(appContext)
        if (intent.getStringExtra("token") != expected) {
            Log.i(TAG, "diag token: $expected")
            Log.w(TAG, "diag rejected: wrong or missing token; resend with --es token <value>")
            return
        }

        val op = intent.getStringExtra("op").orEmpty()
        // goAsync keeps the receiver alive past onReceive's return. The budget
        // is around ten seconds; pair's search for its port (PairOp) may take
        // up to 15 s, past it.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (op) {
                    "pair" -> PairOp.run(appContext, intent)
                    "connect" -> ConnectOp.run(appContext, intent)
                    "exec" -> ExecOp.run(appContext, intent)
                    "answer" -> AnswerOp.run(appContext, intent)
                    "talk" -> TalkOp.run(appContext, intent)
                    "selftest" -> SelfTestOp.run(appContext, intent)
                    "callreport" -> CallReportOp.run(appContext, intent)
                    "voipanswer" -> VoipAnswerOp.run(appContext, intent)
                    "appcall" -> AppCallOp.run(appContext, intent)
                    "answered" -> AnsweredOp.run(appContext, intent)
                    "pref" -> PrefOp.run(appContext, intent)
                    "probe" -> ProbeOp.run(appContext, intent)
                    "armadb" -> ArmAdbOp.run(appContext, intent)
                    "bootstrap" -> BootstrapOp.run(appContext, intent)
                    "port" -> PortOp.run(appContext, intent)
                    "adbd" -> AdbdOp.run(appContext, intent)
                    "revivelater" -> ReviveLaterOp.run(appContext, intent)
                    "settings" -> SettingsOp.run(appContext, intent)
                    "hidesettings" -> HideSettingsOp.run(appContext, intent)
                    "earpiece" -> EarpieceOp.run(appContext, intent)
                    "keeppaired" -> KeepPairedOp.run(appContext, intent)
                    "refusegrant" -> RefuseGrantOp.run(appContext, intent)
                    "disconnect" -> DisconnectOp.run(appContext, intent)
                    "grantlistener" -> GrantListenerOp.run(appContext, intent)
                    "armtest" -> ArmTestOp.run(appContext, intent)
                    "testresult" -> TestResultOp.run(appContext, intent)
                    "finish" -> FinishOp.run(appContext, intent)
                    "daemon" -> DaemonOp.run(appContext, intent)
                    "switch" -> SwitchOp.run(appContext, intent)
                    "volume" -> VolumeOp.run(appContext, intent)
                    "monitor" -> MonitorOp.run(appContext, intent)
                    "forgetkicks" -> ForgetKicksOp.run(appContext, intent)
                    "trash" -> TrashOp.run(appContext, intent)
                    "store" -> StoreOp.run(appContext, intent)
                    "demo" -> DemoOp.run(appContext, intent)
                    "check" -> CheckOp.run(appContext, intent)
                    "pairnotif" -> PairNotifOp.run(appContext, intent)
                    "monitorstop" -> MonitorStopOp.run(appContext, intent)
                    "status" -> StatusOp.run(appContext, intent)
                    "audiocheck" -> AudioCheckOp.run(appContext, intent)
                    "waveform" -> WaveformOp.run(appContext, intent)
                    "pcmhash" -> PcmHashOp.run(appContext, intent)
                    "repage" -> RepageOp.run(appContext, intent)
                    "oldpages" -> OldPagesOp.run(appContext, intent)
                    "holdsave" -> HoldSaveOp.run(appContext, intent)
                    "clearnotes" -> ClearNotesOp.run(appContext, intent)
                    else -> report(appContext, "?", "unknown op '$op'")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "diag op '$op' threw", t)
                report(appContext, op, "threw ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun tokenFor(context: Context): String {
        val prefs = DiagPrefs.of(context)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val token = hex(bytes)
        prefs.edit { putString(KEY_TOKEN, token) }
        return token
    }
}
