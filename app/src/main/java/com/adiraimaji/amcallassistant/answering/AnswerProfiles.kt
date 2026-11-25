package com.adiraimaji.amcallassistant.answering

import android.content.Context
import com.adiraimaji.amcallassistant.Prefs
import org.json.JSONObject
import java.util.UUID

/**
 * One answering machine profile. These are the settings that may differ from
 * caller to caller; the master switch, the SIM choice and the tests stay
 * app-wide. The greeting sources are the same as everywhere else: a recording
 * kept per profile by [AnswerGreetings], typed [text], an [audioPath], or the
 * app's own greeting when none of those is set.
 */
data class AnswerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Seconds of ringing before the machine answers. */
    val delay: Int = AnsweringMachine.DEFAULT_DELAY,
    /** True: about two rings when Do Not Disturb would silence the call. */
    val dndShort: Boolean = true,
    /** Seconds before it hangs up, or [AnsweringMachine.NO_LIMIT]. */
    val longest: Int = AnsweringMachine.DEFAULT_LONGEST,
    val text: String = "",
    val audioPath: String = "",
    val voiceId: String = "default",
)

/**
 * The answering profiles, one JSON string each in the app's preferences, as
 * the Calling Machine keeps its own. One of them is the default: the one
 * whose settings and greeting are used when a call is answered.
 */
internal object AnswerProfiles {
    const val KEY_PROFILES = "answer_profiles"
    const val KEY_DEFAULT = "answer_default_profile"
    const val KEY_MIGRATED = "answer_profiles_migrated"
    private const val LEGACY_ID = "legacy"

    @Synchronized
    fun all(context: Context): List<AnswerProfile> {
        val prefs = Prefs.of(context)
        val raw = prefs.getStringSet(KEY_PROFILES, null)
        if (raw == null) {
            if (!prefs.getBoolean(KEY_MIGRATED, false)) {
                // One time, at the first opening after the update: only an
                // install that had its own settings gets a profile. A fresh
                // install starts with an empty list.
                val hadSettings = AnsweringMachine.realDelay(context) != AnsweringMachine.DEFAULT_DELAY ||
                    !AnsweringMachine.realDndShort(context) ||
                    AnsweringMachine.realLongest(context) != AnsweringMachine.DEFAULT_LONGEST ||
                    Greeting.present(context)
                if (hadSettings) {
                    val legacy = AnswerProfile(
                        id = LEGACY_ID,
                        name = "My greeting",
                        delay = AnsweringMachine.realDelay(context),
                        dndShort = AnsweringMachine.realDndShort(context),
                        longest = AnsweringMachine.realLongest(context),
                    )
                    prefs.edit().putStringSet(KEY_PROFILES, setOf(encode(legacy))).commit()
                    prefs.edit().putString(KEY_DEFAULT, LEGACY_ID).commit()
                }
                prefs.edit().putBoolean(KEY_MIGRATED, true).commit()
            }
            return emptyList()
        }
        return runCatching {
            raw.mapNotNull(::decode)
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: String): AnswerProfile? = all(context).firstOrNull { it.id == id }

    /** The selected default, if it still exists. */
    fun default(context: Context): AnswerProfile? {
        val id = Prefs.of(context).getString(KEY_DEFAULT, "") ?: ""
        val found = if (id.isBlank()) null else get(context, id)
        if (found != null) return found
        // Nothing selected (or it was deleted): the first profile stands in.
        return all(context).firstOrNull().also { select(context, it?.id) }
    }

    fun select(context: Context, id: String?) {
        Prefs.of(context).edit().putString(KEY_DEFAULT, id.orEmpty()).commit()
    }

    @Synchronized
    fun save(context: Context, profile: AnswerProfile) {
        val next = all(context).filterNot { it.id == profile.id } + profile
        put(context, next)
        if (defaultId(context).isBlank()) select(context, profile.id)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        AnswerGreetings.delete(context, id)
        val next = all(context).filterNot { it.id == id }
        put(context, next)
        if (defaultId(context) == id) select(context, next.firstOrNull()?.id)
    }

    private fun defaultId(context: Context): String =
        Prefs.of(context).getString(KEY_DEFAULT, "") ?: ""

    private fun put(context: Context, profiles: List<AnswerProfile>) {
        Prefs.of(context).edit()
            .putStringSet(KEY_PROFILES, profiles.map(::encode).toSet())
            .commit()
    }

    private fun encode(p: AnswerProfile): String = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("delay", p.delay)
        put("dndShort", p.dndShort)
        put("longest", p.longest)
        put("text", p.text)
        put("audioPath", p.audioPath)
        put("voiceId", p.voiceId)
    }.toString()

    private fun decode(raw: String): AnswerProfile? = runCatching {
        val j = JSONObject(raw)
        AnswerProfile(
            id = j.optString("id", UUID.randomUUID().toString()),
            name = j.optString("name"),
            delay = j.optInt("delay", AnsweringMachine.DEFAULT_DELAY).coerceAtLeast(1),
            dndShort = j.optBoolean("dndShort", true),
            longest = j.optInt("longest", AnsweringMachine.DEFAULT_LONGEST).coerceAtLeast(0),
            text = j.optString("text"),
            audioPath = j.optString("audioPath"),
            voiceId = j.optString("voiceId", "default"),
        )
    }.getOrNull()
}
