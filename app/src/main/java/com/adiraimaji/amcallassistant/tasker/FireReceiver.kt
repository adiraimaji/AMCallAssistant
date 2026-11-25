package com.adiraimaji.amcallassistant.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.adiraimaji.amcallassistant.answering.AnswerGreetings
import com.adiraimaji.amcallassistant.answering.AnswerProfiles
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.calling.CallingGreetings
import com.adiraimaji.amcallassistant.calling.CallingProfiles
import com.adiraimaji.amcallassistant.calling.CallingMark
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

        /** The greeting the capsule in the edit screen chose. Both fields may
         *  hold a value; the chosen one wins. An old action has no choice
         *  saved, so it keeps its old order: audio file before written text.
         *  If the chosen field is blank after Tasker's substitution, the other
         *  one is still used rather than leaving the call without a greeting. */
        private suspend fun greetingPcm(
            context: Context,
            config: PluginContract.Config,
            text: String,
            audio: String,
        ): ByteArray? {
            val textFirst = config.greetingKind == PluginContract.GREETING_TEXT
            return when {
                textFirst && text.isNotBlank() -> GreetingMaker.make(context, text, config.voiceId)
                !textFirst && audio.isNotBlank() -> GreetingMaker.fromAudio(context, audio)
                text.isNotBlank() -> GreetingMaker.make(context, text, config.voiceId)
                else -> GreetingMaker.fromAudio(context, audio)
            }
        }

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
                    PluginContract.KIND_CALL -> callNow(app, config, intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "tasker: the action failed", e)
                OneShot.clear()
            } finally {
                pending.finish()
            }
        }
    }

    /** Calling Machine: place a call now, with this action's greeting and
     *  duration. Nothing in the app's own calling settings changes. */
    private suspend fun callNow(context: Context, config: PluginContract.Config, intent: Intent) {
        var effective = config
        var profilePcm: ByteArray? = null
        var delayMs = 0L
        val id = config.callProfileId
        if (id.isNotBlank() && id != PluginContract.PROFILE_CUSTOM) {
            val profile = CallingProfiles.get(context, id)
            if (profile == null) {
                Log.w(TAG, "tasker: calling machine profile $id no longer exists")
                return
            }
            if (profile.whenMode == "date") {
                // An absolute time means nothing when an action runs later.
                Log.w(TAG, "tasker: a picked date and time is not supported by the action")
                return
            }
            delayMs = when (profile.whenMode) {
                "minutes" -> (profile.delayAmount.toLongOrNull() ?: 0L) * 60_000L
                "seconds" -> (profile.delayAmount.toLongOrNull() ?: 0L) * 1_000L
                else -> 0L // "tasker" - place the call as the action fires
            }.coerceAtLeast(0L)
            profilePcm = CallingGreetings.pcm(context, profile.id)
            // Use the profile's values as they are now, not a copy made when
            // the action was edited: changing a field in the editor would have
            // switched the dropdown back to Custom anyway.
            effective = config.copy(
                callNumber = profile.number,
                callDuration = profile.duration,
                callDelayMs = profile.greetingDelayMs,
                greetingText = profile.text,
                audioPath = profile.audioPath,
                voiceId = profile.voiceId,
            )
        }
        // Relative times start when the action runs, not when it was saved.
        if (delayMs > 0L) delay(delayMs)
        val variables = readVariables(intent)
        val number = PluginContract.substitute(effective.callNumber, variables)
        if (number.isBlank()) {
            Log.w(TAG, "tasker: calling machine had no number")
            return
        }
        val text = PluginContract.substitute(effective.greetingText, variables)
        val audio = PluginContract.substitute(effective.audioPath, variables)
        CallingGreetings.begin()
        // A Tasker action cannot make a recording; only a chosen profile's
        // existing one is used.
        val recordedPcm = if (effective.greetingKind == PluginContract.GREETING_RECORD) profilePcm else null
        val pcm = recordedPcm ?: greetingPcm(context, effective, text, audio)
            ?: Greeting.file(context).takeIf { it.isFile && it.length() > 0 }?.readBytes()
        if (pcm == null || pcm.isEmpty()) {
            CallingGreetings.end()
            Log.w(TAG, "tasker: calling machine had no greeting for $number")
            return
        }
        CaptureDaemon.uploadGreeting(context, pcm)
        val seconds = when (effective.callDuration) {
            "none" -> 0
            "message" -> pcm.size.toLong().div(2 * Greeting.RATE).toInt().coerceAtLeast(1)
            else -> effective.callDuration.toIntOrNull() ?: 60
        }
        CallingMark.mark(context)
        val state = runCatching {
            CaptureDaemon.callNow(context, number, seconds, effective.callDelayMs)
        }.getOrNull()
        Log.i(TAG, "tasker: call now -> $number, $state, ${seconds}s")
        // Put the app's answering greeting back on the recorder afterwards,
        // unless a newer call has armed a greeting of its own by then.
        CallingGreetings.scheduleRestore(context, seconds)
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

        var effective = config
        var profilePcm: ByteArray? = null
        val id = config.answerProfileId
        if (id.isNotBlank() && id != PluginContract.PROFILE_CUSTOM) {
            val profile = AnswerProfiles.get(context, id)
            if (profile == null) {
                Log.w(TAG, "tasker: answering machine profile $id no longer exists")
                return
            }
            profilePcm = AnswerGreetings.pcm(context, profile.id)
            effective = config.copy(
                delay = profile.delay.toString(),
                dnd = if (profile.dndShort) PluginContract.Dnd.SHORT.wire else PluginContract.Dnd.SAME.wire,
                longest = profile.longest.toString(),
                greetingText = profile.text,
                audioPath = profile.audioPath,
                voiceId = profile.voiceId,
            )
        }

        // Tasker normally substitutes variables in the stored bundle before
        // firing, so the greeting text is already "Battery 49" here. The
        // variables bundle is only a fallback for hosts that pass values
        // separately; read it too so neither path can leave a %name behind.
        val variables = readVariables(intent)
        val text = PluginContract.substitute(effective.greetingText, variables)
        val audio = PluginContract.substitute(effective.audioPath, variables)

        // The capsule chosen in the edit screen says which greeting this
        // action uses, even when the other field also holds a value. It is
        // uploaded for this call only; the app's own greeting is left alone.
        // A Tasker action cannot make a recording; only a chosen profile's
        // existing one is used.
        val recordedPcm = if (effective.greetingKind == PluginContract.GREETING_RECORD) profilePcm else null
        val pcm = recordedPcm ?: greetingPcm(context, effective, text, audio)
        if (pcm != null) runCatching { CaptureDaemon.uploadGreeting(context, pcm) }

        // A profile may hold any positive number; the old sentinel and a
        // Tasker variable still follow the app's current setting.
        val delay = effective.delay.toIntOrNull()?.takeIf { it > 0 }
            ?: PluginContract.resolveDelay(effective.delay, AnsweringMachine.realDelay(context))
        val dnd = PluginContract.resolveDnd(effective.dnd, AnsweringMachine.realDndShort(context))
        val longest = effective.longest.toIntOrNull()?.takeIf { it >= 0 }
            ?: PluginContract.resolveLongest(effective.longest, AnsweringMachine.realLongest(context))

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
