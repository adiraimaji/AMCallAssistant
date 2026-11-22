package com.adiraimaji.amcallassistant.tasker

import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.VoiceOption

/** Android's Locale plugin protocol, which Tasker's "Plugin" action speaks.
 *  No third-party library: these strings are the whole contract. */
object PluginContract {
    const val ACTION_EDIT = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val EXTRA_SETTING = "com.twofortyfouram.locale.intent.extra.SETTING"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    /** A Bundle of name -> value that a plugin returns to the host, and some
     *  Locale hosts send on fire. Tasker itself does not send variable values
     *  here: it replaces variables inside the stored bundle instead (see
     *  [EXTRA_VARIABLE_REPLACE_FIELDS]). Read as a harmless fallback. */
    const val EXTRA_VARIABLES = "net.dinglisch.android.tasker.extras.VARIABLES"
    const val EXTRA_VARIABLES_LEGACY = "com.twofortyfouram.locale.intent.extra.VARIABLES"
    /** Inside our bundle: a space-separated list of the bundle entries whose
     *  values may contain %variables. This is Tasker's documented protocol
     *  (TaskerPlugin.Setting.setVariableReplaceKeys) and is what makes Tasker
     *  replace %BATT, %LOC and any other variable before the action fires. */
    const val EXTRA_VARIABLE_REPLACE_FIELDS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS"
    const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    /** The Bundle field holding our own config string, inside EXTRA_BUNDLE.
     *  Named FIELD, not KEY, so the preferences-inventory test (which scans
     *  for `KEY` constants) does not mistake a Bundle field for a pref. */
    const val FIELD_CONFIG = "com.adiraimaji.amcallassistant.tasker.config"

    /** Written by the edit activity for Tasker to show in the action line. */
    const val FIELD_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    /** Sentinel in a config: read the current app value when the action fires. */
    const val DEFAULT = "default"

    const val KIND_MAIN = "main"
    const val KIND_ANSWER = "answer"

    /** Which field of the answering action a dropdown chose. All four use
     *  [DEFAULT] to mean "keep the value the user chose in the app". */
    enum class Dnd(val wire: String) {
        DEFAULT("default"), SHORT("short"), SAME("same");

        companion object {
            fun of(wire: String?): Dnd = entries.firstOrNull { it.wire == wire } ?: DEFAULT
        }
    }

    /** One plugin action's config, serialised to a single string so the
     *  Locale protocol's Bundle stays small and self-contained. */
    data class Config(
        val kind: String,
        val revert: Boolean = false,
        val mainOn: Boolean = true,
        val answerOn: Boolean = false,
        /** Seconds, or [DEFAULT]. */
        val delay: String = DEFAULT,
        /** [Dnd] wire value. */
        val dnd: String = DEFAULT,
        /** Seconds, or [DEFAULT]. */
        val longest: String = DEFAULT,
        val greetingText: String = "",
        val voiceId: String = DEFAULT,
    ) {
        /** The voice choices offered by the edit screen, as a stable string. */
        fun blurb(): String = when (kind) {
            KIND_MAIN -> "Toggle AM Call Assistant: ${if (revert) "revert" else if (mainOn) "on" else "off"}"
            else -> buildString {
                append("AM Answering Machine: ")
                append(if (delay == DEFAULT) "answer's setting" else "after ${delay}s")
                if (dnd != DEFAULT) append(", DND ${Dnd.of(dnd).name.lowercase()}")
                if (longest != DEFAULT) append(", longest ${longestLabel(longest)}")
                if (greetingText.isNotBlank()) append(", greeting")
            }
        }

        fun serialise(): String = listOf(
            "2", // version 2: the greeting text is plain so Tasker can see and
            // replace its %variables before the fire broadcast.
            kind,
            if (revert) "1" else "0",
            if (mainOn) "1" else "0",
            if (answerOn) "1" else "0",
            delay,
            dnd,
            longest,
            // Escape only our separator and newlines; %BATT stays visible.
            greetingText.replace("|", "%7C").replace("\n", "%0A").replace("\r", ""),
            java.util.Base64.getEncoder().encodeToString(voiceId.toByteArray(Charsets.UTF_8)),
        ).joinToString("|")

        companion object {
            fun parse(value: String?): Config? {
                if (value == null) return null
                val f = value.split("|")
                if (f.size != 10) return null
                return when (f[0]) {
                    "2" -> Config(
                        kind = f[1],
                        revert = f[2] == "1",
                        mainOn = f[3] == "1",
                        answerOn = f[4] == "1",
                        delay = f[5],
                        dnd = f[6],
                        longest = f[7],
                        greetingText = f[8].replace("%0A", "\n").replace("%7C", "|"),
                        voiceId = String(java.util.Base64.getDecoder().decode(f[9]), Charsets.UTF_8),
                    )
                    "1" -> Config( // old saved actions
                        kind = f[1],
                        revert = f[2] == "1",
                        mainOn = f[3] == "1",
                        answerOn = f[4] == "1",
                        delay = f[5],
                        dnd = f[6],
                        longest = f[7],
                        greetingText = String(java.util.Base64.getDecoder().decode(f[8]), Charsets.UTF_8),
                        voiceId = String(java.util.Base64.getDecoder().decode(f[9]), Charsets.UTF_8),
                    )
                    else -> null
                }
            }
        }
    }

    /** Delays shown in the edit screen. The sentinel reads "App's Setting":
     *  it means use whatever the user chose in the app, not a fixed default.
     *  The wire value stays "default" so saved configs keep working. */
    val DELAY_OPTIONS: List<Pair<String, String>> =
        listOf(DEFAULT to "App's Setting") +
            AnsweringMachine.DELAYS.map { it.toString() to "$it seconds" }

    val DND_OPTIONS: List<Pair<String, String>> = listOf(
        DEFAULT to "App's Setting",
        Dnd.SHORT.wire to "After 2 rings (about 5 seconds)",
        Dnd.SAME.wire to "The same delay",
    )

    /** How a typed longest-message value reads in the edit screen and blurb.
     *  The input is always seconds; a Tasker variable is shown as typed. */
    fun longestLabel(wire: String): String {
        val value = wire.trim()
        return when {
            value.isEmpty() || value == DEFAULT -> "App's Setting"
            value == AnsweringMachine.NO_LIMIT.toString() -> "No limit"
            else -> {
                val seconds = value.toLongOrNull()
                when {
                    seconds == null -> value
                    seconds >= 60 && seconds % 60 == 0L -> {
                        val minutes = seconds / 60
                        "$minutes minute${if (minutes == 1L) "" else "s"}"
                    }
                    else -> "$seconds seconds"
                }
            }
        }
    }

    /** Resolve the default sentinel against the value the user chose in the
     *  app at fire time, never a hard-coded copy of it. */
    fun resolveDelay(wire: String, current: Int): Int =
        if (wire == DEFAULT) current else wire.toIntOrNull()?.takeIf { it in AnsweringMachine.DELAYS }
            ?: current

    /**
     * The longest message is a free-text number of seconds, or a Tasker
     * variable which Tasker has already replaced in the config by the time
     * this runs. Blank (or the sentinel) means follow the app's setting, 0 is
     * no limit, and any other number is used as seconds however large. An
     * invalid value - including a variable that was not filled in - means no
     * limit, never a silent fallback to some other length.
     */
    fun resolveLongest(wire: String, current: Int): Int {
        val value = wire.trim()
        if (value.isEmpty() || value == DEFAULT) return current
        val seconds = value.toLongOrNull() ?: return AnsweringMachine.NO_LIMIT
        if (seconds <= 0 || seconds > Int.MAX_VALUE) return AnsweringMachine.NO_LIMIT
        return seconds.toInt()
    }

    fun resolveDnd(wire: String, current: Boolean): Boolean = when (Dnd.of(wire)) {
        Dnd.DEFAULT -> current
        Dnd.SHORT -> true
        Dnd.SAME -> false
    }

    /**
     * Substitute Tasker's %variables in the greeting text. Tasker may send
     * the variables extra as a Map or a Bundle, with names either bare (BATT)
     * or with the percent (%BATT); be tolerant of all of those. A name may
     * also be written with both ends (%BATT%) or one (%BATT), as in Tasker
     * itself. A missing variable is left as written rather than blanked.
     */
    fun substitute(text: String, variables: Any?): String {
        if (text.isEmpty() || variables == null) return text
        // %BATT, %BATT% and %Battery are all the variable BATT to Tasker.
        val pattern = Regex("%([A-Za-z_][A-Za-z0-9_]*)%?")
        return pattern.replace(text) { match ->
            val name = normalise(match.groupValues[1])
            val found = when (variables) {
                is Map<*, *> -> variables.entries.firstOrNull { entry ->
                    entry.key is String && normalise(entry.key as String) == name
                }?.value
                is android.os.Bundle -> {
                    // Robolectric's Bundle.keySet() and get() may not work, so
                    // ask for the names the text uses in every way Tasker and
                    // the test doubles might have stored them.
                    @Suppress("DEPRECATION")
                    val bare = runCatching { variables.get(name) }.getOrNull()
                    @Suppress("DEPRECATION")
                    val pct = runCatching { variables.get("%$name") }.getOrNull()
                    val asString = runCatching { variables.getString(name) }.getOrNull()
                    val pctString = runCatching { variables.getString("%$name") }.getOrNull()
                    bare ?: pct ?: asString ?: pctString
                }
                else -> null
            }
            found?.toString() ?: match.value
        }
    }

    fun normalise(name: String): String = name.trim().removePrefix("%").removeSuffix("%")

    /** Voice list labels from the app's own dialog, as asked. */
    fun voiceLabel(voice: VoiceOption): String = voice.label
}
