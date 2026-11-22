package com.adiraimaji.amcallassistant

import android.app.Application
import android.util.Log
import com.adiraimaji.amcallassistant.recorder.BackgroundKill

/**
 * What runs at every start of AM Call Assistant's process, whatever started it, on a
 * thread of its own: the notification channels NotificationHealth reads, the
 * heartbeat from before 1.2 renewed (RecorderKeepAlive.renewOldHeartbeat),
 * Share's stale copies swept (ShareCopies), the Battery setting Restricted
 * said (BatteryHealth) - and first the background-stop
 * check (BackgroundKill.checkOwnRecords), because a stopped AM Call Assistant is often
 * started again with no activity at all. Measured on
 * the Honor (2026-10-25): a Force stop, and within a second Android had
 * started AM Call Assistant again for its notification listener - the start record read
 * SERVICE, COLD, wasForceStopped=true - and the stopped flag was already
 * clear when read, before the daemon's once-a-minute look could see it. The
 * process that knows it began stopped is that one, so it looks at once,
 * rather than waiting for the user to open AM Call Assistant, which could be days.
 * MainActivity looks again on each open.
 */
class AmCallAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Off the main thread: binder calls, and a call's wake may be what
        // started this process.
        val app = this
        Thread({
            runCatching { BackgroundKill.checkOwnRecords(app, "at process start") }
                .onFailure { Log.w("AMCallAssistant", "backgroundkill: start check failed", it) }
            // The channels NotificationHealth checks there from the start,
            // as Android's guide has it ("safe to call this code when
            // starting an app"): one made only when first used cannot be
            // found, or turned on, in Android's settings before then - nor
            // checked. The call's own, pairing's and playback's are still
            // made as they are first used.
            runCatching {
                com.adiraimaji.amcallassistant.calls.AfterCall.ensureChannels(app)
                com.adiraimaji.amcallassistant.calls.RecordPrompt.ensureChannel(app)
            }.onFailure { Log.w("AMCallAssistant", "notifications: channels not made at start", it) }
            // The heartbeat from before 1.2, put anew without waiting for
            // the app to be opened (RecorderKeepAlive.renewOldHeartbeat).
            runCatching { com.adiraimaji.amcallassistant.capture.RecorderKeepAlive.renewOldHeartbeat(app) }
                .onFailure { Log.w("AMCallAssistant", "keepalive: the old heartbeat not renewed at start", it) }
            // Share's stale copies of calls, also on a phone no longer set
            // up, where no keep-alive runs (ShareCopies).
            runCatching { com.adiraimaji.amcallassistant.recordings.ShareCopies.sweep(app) }
                .onFailure { Log.w("AMCallAssistant", "shares: stale copies not swept at start", it) }
            // The Battery setting Restricted, said once while it lasts: a
            // start of the process is then rare, and home is not seen
            // between calls (BatteryHealth).
            runCatching { com.adiraimaji.amcallassistant.system.BatteryHealth.tellIfRestricted(app) }
                .onFailure { Log.w("AMCallAssistant", "battery: Restricted not said at start", it) }
        }, "amcallassistant-startcheck").start()
    }
}
