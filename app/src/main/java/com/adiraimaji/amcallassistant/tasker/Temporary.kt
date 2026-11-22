package com.adiraimaji.amcallassistant.tasker

import android.content.Context
import androidx.core.content.edit
import com.adiraimaji.amcallassistant.Prefs
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import java.io.File

/**
 * The values a plugin action found when it first fired, so a later Revert can
 * put them back. Kept in SharedPreferences, so a reboot does not lose the
 * ability to revert; cleared only by Revert itself. The changes are temporary
 * by design: the app's own settings are never overwritten for good.
 */
object Temporary {
    private const val KEY_MAIN = "tasker_snapshot_main"
    private const val KEY_ANSWER = "tasker_snapshot_answer"

    private data class Main(val on: Boolean)
    private data class Answer(
        val on: Boolean,
        val delay: Int,
        val dnd: Boolean,
        val longest: Int,
        /** The greeting's bytes, or null when there was no greeting. */
        val greeting: ByteArray?,
    )

    private fun saveJson(context: Context, key: String, json: String) {
        Prefs.of(context).edit { putString(key, json) }
    }

    private fun readJson(context: Context, key: String): String? =
        Prefs.of(context).getString(key, null)

    private fun clear(context: Context, key: String) {
        Prefs.of(context).edit { remove(key) }
    }

    private fun encode(on: Boolean) = if (on) "1" else "0"

    private fun encode(
        on: Boolean,
        delay: Int,
        dnd: Boolean,
        longest: Int,
        greeting: ByteArray?,
    ): String = listOf(
        encode(on),
        delay.toString(),
        encode(dnd),
        longest.toString(),
        greeting?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: "-",
    ).joinToString("|")

    private fun parseMain(raw: String?): Main? {
        if (raw == null) return null
        val f = raw.split("|")
        if (f.size != 1) return null
        return Main(f[0] == "1")
    }

    private fun parseAnswer(raw: String?): Answer? {
        if (raw == null) return null
        val f = raw.split("|", limit = 5)
        if (f.size != 5) return null
        return Answer(
            on = f[0] == "1",
            delay = f[1].toIntOrNull() ?: return null,
            dnd = f[2] == "1",
            longest = f[3].toIntOrNull() ?: return null,
            greeting = if (f[4] == "-") null else runCatching {
                java.util.Base64.getDecoder().decode(f[4])
            }.getOrNull(),
        )
    }

    /** Take a snapshot only if none exists yet: a second action of the same
     *  kind must not replace the state the first one is meant to restore. */
    fun snapshotMain(context: Context) {
        if (readJson(context, KEY_MAIN) != null) return
        saveJson(context, KEY_MAIN, encode(RecorderSwitch.isOn(context)))
    }

    fun snapshotAnswer(context: Context) {
        if (readJson(context, KEY_ANSWER) != null) return
        val greeting = runCatching {
            Greeting.file(context).takeIf { it.isFile }?.readBytes()
        }.getOrNull()
        saveJson(
            context, KEY_ANSWER,
            encode(
                AnsweringMachine.enabled(context),
                AnsweringMachine.delay(context),
                AnsweringMachine.dndShort(context),
                AnsweringMachine.longest(context),
                greeting,
            ),
        )
    }

    fun hasMain(context: Context): Boolean = readJson(context, KEY_MAIN) != null
    fun hasAnswer(context: Context): Boolean = readJson(context, KEY_ANSWER) != null

    /** Put back the main switch. Uses the same path the app and tile use, so
     *  the recorder actually follows the restored value. */
    fun revertMain(context: Context): Boolean {
        val value = parseMain(readJson(context, KEY_MAIN)) ?: return false
        RecorderSwitch.turnInBackground(context.applicationContext, value.on)
        clear(context, KEY_MAIN)
        return true
    }

    /** Put back the answering settings and the greeting file. The greeting is
     *  sent to the recorder by the caller (the receiver) after this returns. */
    fun revertAnswer(context: Context): Boolean {
        val value = parseAnswer(readJson(context, KEY_ANSWER)) ?: return false
        val app = context.applicationContext
        AnsweringMachine.setEnabled(app, value.on)
        AnsweringMachine.setDelay(app, value.delay)
        AnsweringMachine.setDndShort(app, value.dnd)
        AnsweringMachine.setLongest(app, value.longest)
        val file = Greeting.file(app)
        if (value.greeting != null) {
            file.writeBytes(value.greeting)
        } else if (file.isFile) {
            file.delete()
        }
        clear(context, KEY_ANSWER)
        return true
    }
}
