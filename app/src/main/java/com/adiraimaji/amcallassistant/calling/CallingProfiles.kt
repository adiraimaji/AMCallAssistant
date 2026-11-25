package com.adiraimaji.amcallassistant.calling

import android.content.Context
import com.adiraimaji.amcallassistant.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One scheduled call. The greeting sources are the answering machine's: the
 * app's recorded greeting (empty [audioPath] and [text]), typed words, or an
 * audio file. [duration] is "message" (until the greeting ends), "none", or a
 * number of seconds. [busy] says what to do when a call is already up.
 */
data class CallingProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val number: String,
    val contactName: String = "",
    val atMillis: Long,
    /** How the time was chosen: "minutes", "seconds" or "date". */
    val whenMode: String = "date",
    /** The number typed for a relative time; ignored for "date". */
    val delayAmount: String = "",
    /** Milliseconds after outgoing off-hook before the greeting is played. */
    val greetingDelayMs: Int = 1000,
    val duration: String = "message",
    val text: String = "",
    val audioPath: String = "",
    val voiceId: String = "default",
    val busy: String = WAIT,
    val enabled: Boolean = true,
) {
    companion object {
        const val WAIT = "wait"
        const val DISCARD = "discard"
        const val NOW = "now"
    }
}

/** The profiles, kept as one JSON string per entry in the app's preferences,
 *  the same way the save journal keeps its entries. */
internal object CallingProfiles {
    const val KEY_PROFILES = "calling_profiles"

    fun all(context: Context): List<CallingProfile> = runCatching {
        val raw = Prefs.of(context).getStringSet(KEY_PROFILES, emptySet()).orEmpty()
        raw.mapNotNull(::decode).sortedBy { it.atMillis }
    }.getOrDefault(emptyList())

    fun save(context: Context, profile: CallingProfile) {
        val next = all(context).filterNot { it.id == profile.id } + profile
        put(context, next)
    }

    fun remove(context: Context, id: String) = put(context, all(context).filterNot { it.id == id })

    fun get(context: Context, id: String): CallingProfile? = all(context).firstOrNull { it.id == id }

    private fun put(context: Context, profiles: List<CallingProfile>) {
        Prefs.of(context).edit()
            .putStringSet(KEY_PROFILES, profiles.map(::encode).toSet())
            .commit()
    }

    private fun encode(p: CallingProfile): String = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("number", p.number)
        put("contactName", p.contactName)
        put("at", p.atMillis)
        put("whenMode", p.whenMode)
        put("delayAmount", p.delayAmount)
        put("greetingDelayMs", p.greetingDelayMs)
        put("duration", p.duration)
        put("text", p.text)
        put("audioPath", p.audioPath)
        put("voiceId", p.voiceId)
        put("busy", p.busy)
        put("enabled", p.enabled)
    }.toString()

    private fun decode(raw: String): CallingProfile? = runCatching {
        val j = JSONObject(raw)
        CallingProfile(
            id = j.optString("id", UUID.randomUUID().toString()),
            name = j.optString("name"),
            number = j.optString("number"),
            contactName = j.optString("contactName"),
            atMillis = j.optLong("at"),
            whenMode = j.optString("whenMode", "date"),
            delayAmount = j.optString("delayAmount"),
            greetingDelayMs = j.optInt("greetingDelayMs", 1000),
            duration = j.optString("duration", "message"),
            text = j.optString("text"),
            audioPath = j.optString("audioPath"),
            voiceId = j.optString("voiceId", "default"),
            busy = j.optString("busy", CallingProfile.WAIT),
            enabled = j.optBoolean("enabled", true),
        ).takeIf { it.number.isNotBlank() && it.atMillis > 0 }
    }.getOrNull()
}
