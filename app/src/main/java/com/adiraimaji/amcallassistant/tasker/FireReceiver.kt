package com.adiraimaji.amcallassistant.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.capture.CaptureDaemon
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Locale plugin's fire end. Tasker sends the config we wrote in the edit
 * activity; we apply it and let the app's own paths do the work.
 *
 * The main action uses the disk-backed snapshot (Temporary) so Revert works.
 * The answering action is a one-shot: it answers the call ringing now with
 * in-memory settings (OneShot) and never writes the app's real settings.
 */
class FireReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AMCallAssistant"
        private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** If the call never reports an end, clear the override anyway. */
        private const val TIMEOUT_MS = 5 * 60_000L

        /** Clear the override and restore the daemon from the app's real
         *  settings. Called when the one-shot call ends (or the guard fires). */
        fun clearOneShot(context: Context) {
            if (!OneShot.isActive()) return
            OneShot.clear()
            work.launch {
                // The real greeting from the app's file, not the typed one.
                runCatching {
                    Greeting.file(context).takeIf { it.isFile }?.readBytes()?.let {
                        CaptureDaemon.uploadGreeting(context, it)
                    }
                }
                runCatching { CaptureDaemon.setAnswering(context) }
                Log.i(TAG, "tasker: one-shot answer finished; app settings untouched")
            }
        }
    }

    /**
     * Read the variables Tasker sends with the fire intent. It uses
     * `net.dinglisch.android.tasker.extras.VARIABLES` (a Bundle of bare names
     * to values); older Locale hosts may use the twofortyfouram name. Return
     * a single Map so [PluginContract.substitute] can work on it regardless.
     */
    private fun readVariables(intent: Intent): Map<String, String> {
        val out = HashMap<String, String>()
        for (key in listOf(PluginContract.EXTRA_VARIABLES, PluginContract.EXTRA_VARIABLES_LEGACY)) {
            val raw = intent.extras?.let { extras ->
                @Suppress("DEPRECATION")
                runCatching { extras.get(key) }.getOrNull()
            } ?: continue
            when (raw) {
                is android.os.Bundle -> {
                    // Bundle.get works on a real phone; keySet is not reliable
                    // in Robolectric, but this code runs on the device.
                    val keys = runCatching { raw.keySet() }.getOrNull().orEmpty()
                    for (name in keys) {
                        val value = runCatching { raw.get(name) }.getOrNull()
                            ?: runCatching { raw.getString(name) }.getOrNull()
                        if (value != null) out[PluginContract.normalise(name)] = value.toString()
                    }
                }
                is Map<*, *> -> for ((k, v) in raw) {
                    if (k is String && v != null) out[PluginContract.normalise(k)] = v.toString()
                }
                is Array<*> -> for (item in raw) {
                    // Some hosts send "NAME=value" strings.
                    val text = item?.toString() ?: continue
                    val at = text.indexOf('=')
                    if (at > 0) out[PluginContract.normalise(text.substring(0, at))] = text.substring(at + 1)
                }
            }
        }
        return out
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PluginContract.ACTION_FIRE) return
        val app = context.applicationContext
        val bundle: Bundle = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE) ?: return
        val config = PluginContract.Config.parse(bundle.getString(PluginContract.FIELD_CONFIG)) ?: return
        val pending = goAsync()
        work.launch {
            try {
                when (config.kind) {
                    PluginContract.KIND_MAIN -> main(app, config)
                    PluginContract.KIND_ANSWER -> answerNow(app, config, intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "tasker: the action failed", e)
                OneShot.clear()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun main(context: Context, config: PluginContract.Config) {
        if (config.revert) {
            Temporary.revertMain(context)
            return
        }
        Temporary.snapshotMain(context)
        RecorderSwitch.turnInBackground(context, config.mainOn)
    }

    /** Answer the ringing call now, with the config's values, once. Nothing
     *  here writes the app's answering settings. */
    private suspend fun answerNow(context: Context, config: PluginContract.Config, intent: Intent) {
        // A second fire supersedes the first: clear any earlier one-shot.
        OneShot.clear()

        // Tasker normally substitutes variables in the stored bundle before
        // firing, so config.greetingText is already "Battery 49" here. The
        // variables bundle is only a fallback for hosts that pass values
        // separately; read it too so neither path can leave a %name behind.
        val variables = readVariables(intent)
        val text = PluginContract.substitute(config.greetingText, variables)
        if (config.greetingText.contains('%') || variables.isNotEmpty()) {
            Log.i(TAG, "tasker: greeting '${config.greetingText}' -> '$text'")
        }

        // A typed greeting is uploaded to the daemon for this call only; the
        // app's own greeting file is left alone.
        val pcm = if (text.isNotBlank()) GreetingMaker.make(context, text, config.voiceId) else null
        if (pcm != null) runCatching { CaptureDaemon.uploadGreeting(context, pcm) }

        val delay = PluginContract.resolveDelay(config.delay, AnsweringMachine.realDelay(context))
        val dnd = PluginContract.resolveDnd(config.dnd, AnsweringMachine.realDndShort(context))
        val longest = PluginContract.resolveLongest(config.longest, AnsweringMachine.realLongest(context))

        OneShot.start(
            OneShot.Values(
                enabled = true,
                delay = delay,
                dndShort = dnd,
                longest = longest,
                greeting = pcm,
                firedAt = SystemClock.elapsedRealtime(),
            ),
        )

        // The dedicated 'M' command accepts the ring in progress at once,
        // even though the app's own machine was off when the ring began (the
        // ordinary 'Y' go-ahead would be ignored as refused in that case).
        val state = runCatching { CaptureDaemon.answerNow(context, longest) }.getOrNull()
        Log.i(TAG, "tasker: answer now -> $state")

        if (state == null || state == "GO none") {
            // No recorder, or no call: nothing to revert, leave no override.
            clearOneShot(context)
            return
        }

        // Watch for the call ending, then put the daemon back on the real
        // settings and the app's real greeting. The timeout is a guard for a
        // call that never reports an end.
        val fired = SystemClock.elapsedRealtime()
        work.launch {
            while (OneShot.isActive() && SystemClock.elapsedRealtime() - fired < TIMEOUT_MS) {
                delay(500)
            }
            if (OneShot.isActive()) clearOneShot(context)
        }
    }
}
