package com.adiraimaji.amcallassistant.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.capture.CaptureDaemon
import com.adiraimaji.amcallassistant.tasker.GreetingMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Places a scheduled Calling Machine call. The shell-side daemon does the
 *  dialling - the app holds no CALL_PHONE permission - and plays the greeting. */
class CallingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallingScheduler.ACTION) return
        val id = intent.getStringExtra(CallingScheduler.EXTRA_ID) ?: return
        val profile = CallingProfiles.get(context.applicationContext, id) ?: return
        if (!profile.enabled) return

        if (inCall(context)) {
            when (profile.busy) {
                CallingProfile.DISCARD -> {
                    CallingProfiles.save(context.applicationContext, profile.copy(enabled = false))
                    return
                }
                CallingProfile.WAIT -> {
                    val later = profile.copy(atMillis = System.currentTimeMillis() + 60_000L)
                    CallingProfiles.save(context.applicationContext, later)
                    CallingScheduler.schedule(context.applicationContext, later)
                    return
                }
            }
        }

        val app = context.applicationContext
        scope.launch {
            var seconds = 0
            runCatching {
                // A preview would keep playing on the loudspeaker into and
                // after the call. A previous call's restore must not land
                // while this call's greeting is being armed.
                CallingGreetings.begin()
                // This profile's own greeting, or the app's as a fallback for
                // profiles made before per-profile greetings existed.
                val pcm = CallingGreetings.pcm(app, profile.id)
                    ?: when {
                        profile.audioPath.isNotBlank() -> GreetingMaker.fromAudio(app, profile.audioPath)
                        profile.text.isNotBlank() -> GreetingMaker.make(app, profile.text, profile.voiceId)
                        else -> Greeting.file(app).takeIf { it.isFile && it.length() > 0 }?.readBytes()
                    }
                if (pcm == null || pcm.isEmpty()) {
                    CallingGreetings.end()
                    Log.w(TAG, "calling: no greeting for ${profile.number}; not placing the call")
                    return@runCatching
                }
                CaptureDaemon.uploadGreeting(app, pcm)
                seconds = when (profile.duration) {
                    "none" -> 0
                    "message" -> (pcm?.size?.toLong() ?: Greeting.file(app).length().toLong())
                        .div(2 * Greeting.RATE).toInt().coerceAtLeast(1)
                    else -> profile.duration.toIntOrNull() ?: 60
                }
                CallingMark.mark(app)
                val reply = CaptureDaemon.callNow(
                    app, profile.number, seconds, profile.greetingDelayMs,
                )
                Log.i(TAG, "calling: ${profile.number} -> $reply, ${seconds}s")
            }.onFailure { Log.w(TAG, "calling: the scheduled call failed", it) }
            // The recorder holds one greeting at a time: put the answering
            // machine's back once this call is done, unless a newer call has
            // armed its own greeting in the meantime.
            CallingGreetings.scheduleRestore(app, seconds)
            CallingProfiles.save(app, profile.copy(enabled = false))
        }
    }

    private fun inCall(context: Context): Boolean = runCatching {
        @Suppress("MissingPermission")
        context.getSystemService(TelecomManager::class.java)?.isInCall == true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "AMCallAssistant"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
