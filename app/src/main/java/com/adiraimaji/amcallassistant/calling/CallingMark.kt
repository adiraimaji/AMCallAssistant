package com.adiraimaji.amcallassistant.calling

import android.content.Context
import com.adiraimaji.amcallassistant.Prefs

/**
 * The next phone recording to be saved belongs to a Calling Machine call.
 *
 * Set just before the daemon places the call, and taken by the saver when it
 * writes that recording, so the distinction travels in the file's own tags -
 * the same way a message or a kept recording does, and survives a reinstall.
 */
internal object CallingMark {
    const val KEY_PENDING = "calling_pending"

    fun mark(context: Context) {
        Prefs.of(context).edit().putBoolean(KEY_PENDING, true).commit()
    }

    fun take(context: Context): Boolean {
        val prefs = Prefs.of(context)
        val pending = prefs.getBoolean(KEY_PENDING, false)
        if (pending) prefs.edit().remove(KEY_PENDING).commit()
        return pending
    }
}
