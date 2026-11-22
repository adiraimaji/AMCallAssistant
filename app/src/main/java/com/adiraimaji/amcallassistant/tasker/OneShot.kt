package com.adiraimaji.amcallassistant.tasker

import android.content.Context
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.Greeting

/**
 * The settings for one Tasker "answer now" action, held in memory only.
 *
 * Nothing here is written to SharedPreferences: the app's answering machine
 * keeps the values the user chose in it. While [active] is set, the answering
 * read path ([AnsweringMachine]'s getters and `wire`) uses these values, and
 * the daemon is pushed them with 'N'. When the call ends the override is
 * cleared and the daemon is pushed the real prefs again.
 */
object OneShot {
    data class Values(
        val enabled: Boolean,
        val delay: Int,
        val dndShort: Boolean,
        val longest: Int,
        /** The greeting bytes for this one call, or null to use the app's. */
        val greeting: ByteArray?,
        /** elapsedRealtime when the action fired, for the timeout guard. */
        val firedAt: Long,
    )

    @Volatile
    var active: Values? = null
        private set

    fun start(values: Values) {
        active = values
    }

    fun clear() {
        active = null
    }

    /** True while an override is in force. */
    fun isActive(): Boolean = active != null

    /** The answering values to use: the override when set, else the prefs.
     *  Used by [AnsweringMachine]'s read path so no preference is touched. */
    fun enabled(context: Context): Boolean = active?.enabled ?: AnsweringMachine.realEnabled(context)
    fun delay(context: Context): Int = active?.delay ?: AnsweringMachine.realDelay(context)
    fun dndShort(context: Context): Boolean = active?.dndShort ?: AnsweringMachine.realDndShort(context)
    fun longest(context: Context): Int = active?.longest ?: AnsweringMachine.realLongest(context)

    /** What to send the daemon in `wire`: on only with a greeting to play. */
    fun wireOn(context: Context): Boolean {
        val values = active
        return if (values != null) {
            values.enabled && (values.greeting != null || Greeting.present(context))
        } else {
            AnsweringMachine.realEnabled(context) && Greeting.present(context)
        }
    }
}
