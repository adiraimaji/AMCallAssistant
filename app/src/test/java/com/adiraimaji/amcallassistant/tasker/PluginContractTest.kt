package com.adiraimaji.amcallassistant.tasker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContractTest {

    @Test fun aConfigSurvivesARoundTrip() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_ANSWER,
            answerOn = true,
            delay = "10",
            dnd = PluginContract.Dnd.SAME.wire,
            longest = "120",
            greetingText = "Hello | you have | reached me %name%",
            voiceId = "en-US-voice",
        )
        val parsed = PluginContract.Config.parse(config.serialise())
        assertEquals(config, parsed)
    }

    @Test fun aMainConfigSurvivesARoundTrip() {
        val config = PluginContract.Config(kind = PluginContract.KIND_MAIN, mainOn = false, revert = true)
        assertEquals(config, PluginContract.Config.parse(config.serialise()))
    }

    @Test fun brokenConfigsAreRefused() {
        assertNull(PluginContract.Config.parse(null))
        assertNull(PluginContract.Config.parse("0|answer|0"))
        assertNull(PluginContract.Config.parse("garbage"))
    }

    @Test fun aDefaultDelayFollowsTheAppsCurrentValue() {
        assertEquals(20, PluginContract.resolveDelay(PluginContract.DEFAULT, 20))
        assertEquals(25, PluginContract.resolveDelay(PluginContract.DEFAULT, 25))
        assertEquals(10, PluginContract.resolveDelay("10", 25))
        // An out-of-range value falls back to what the app has.
        assertEquals(25, PluginContract.resolveDelay("99", 25))
    }

    @Test fun aDefaultLongestFollowsTheAppsCurrentValue() {
        assertEquals(60, PluginContract.resolveLongest(PluginContract.DEFAULT, 60))
        assertEquals(120, PluginContract.resolveLongest(PluginContract.DEFAULT, 120))
        assertEquals(60, PluginContract.resolveLongest("", 60))
        assertEquals(60, PluginContract.resolveLongest("  ", 60))
    }

    @Test fun longestIsAnyNumberOfSecondsOrNoLimit() {
        assertEquals(0, PluginContract.resolveLongest("0", 120))
        assertEquals(10, PluginContract.resolveLongest("10", 120))
        assertEquals(300, PluginContract.resolveLongest("300", 120))
        assertEquals(300, PluginContract.resolveLongest(" 300 ", 120))
    }

    @Test fun anInvalidLongestMeansNoLimit() {
        // Includes a Tasker variable that was never filled in.
        assertEquals(0, PluginContract.resolveLongest("bad", 120))
        assertEquals(0, PluginContract.resolveLongest("%LONGEST", 120))
        assertEquals(0, PluginContract.resolveLongest("-1", 120))
        assertEquals(0, PluginContract.resolveLongest("1.5", 120))
    }

    @Test fun longestLabelsReadAsPeopleSayThem() {
        assertEquals("App's Setting", PluginContract.longestLabel(""))
        assertEquals("App's Setting", PluginContract.longestLabel(PluginContract.DEFAULT))
        assertEquals("No limit", PluginContract.longestLabel("0"))
        assertEquals("10 seconds", PluginContract.longestLabel("10"))
        assertEquals("5 minutes", PluginContract.longestLabel("300"))
        assertEquals("%LONGEST", PluginContract.longestLabel("%LONGEST"))
    }

    @Test fun dndDefaultsFollowTheAppsCurrentValue() {
        assertTrue(PluginContract.resolveDnd(PluginContract.DEFAULT, true))
        assertFalse(PluginContract.resolveDnd(PluginContract.DEFAULT, false))
        assertTrue(PluginContract.resolveDnd(PluginContract.Dnd.SHORT.wire, false))
        assertFalse(PluginContract.resolveDnd(PluginContract.Dnd.SAME.wire, true))
    }

    @Test fun theSerialisedConfigKeepsVariablesVisibleToTasker() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_ANSWER,
            greetingText = "Battery %BATT and %NAME",
        )
        val wire = config.serialise()
        assertTrue("variables must stay plain for Tasker: $wire", "%BATT" in wire && "%NAME" in wire)
        assertEquals(config, PluginContract.Config.parse(wire))
        // An old version-1 config still parses.
        val old = "1|answer|0|1|1|default|default|default|SGVsbG8=|ZGVmYXVsdA=="
        assertEquals("Hello", PluginContract.Config.parse(old)!!.greetingText)
    }

    @Test fun taskerIsToldWhichBundleFieldMayContainVariables() {
        // Tasker's documented key (TaskerPlugin.Setting.setVariableReplaceKeys).
        assertEquals(
            "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS",
            PluginContract.EXTRA_VARIABLE_REPLACE_FIELDS,
        )
        // Tasker stores the list space-separated, so a field name must not
        // contain a space or Tasker silently ignores it.
        assertFalse(PluginContract.FIELD_CONFIG.contains(' '))
    }

    @Test fun taskerVariablesAreSubstituted() {
        val variables = mapOf("name" to "Alice", "city" to "London")
        assertEquals(
            "Hello Alice from London, and %missing%",
            PluginContract.substitute("Hello %name% from %city%, and %missing%", variables),
        )
        assertEquals("Hello %name%", PluginContract.substitute("Hello %name%", emptyMap<String, String>()))
        assertEquals("Hello %name%", PluginContract.substitute("Hello %name%", null))
        // Tasker's built-in variables are written without a closing %.
        assertEquals("Battery 87", PluginContract.substitute("Battery %BATT", mapOf("BATT" to "87")))
        assertEquals("Battery 87", PluginContract.substitute("Battery %BATT", mapOf("%BATT" to "87")))
    }

    @Test fun anAudioFilePathRoundTripsWithItsVariables() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_ANSWER,
            audioPath = "/sdcard/%FOLDER%/greeting.mp3",
        )
        val wire = config.serialise()
        assertTrue("Tasker must see the variable in the path: $wire", "%FOLDER%" in wire)
        assertEquals(config, PluginContract.Config.parse(wire))
        assertTrue(config.blurb().contains("audio file"))
    }

    @Test fun theChosenGreetingSurvivesWithBothFieldsFilled() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_ANSWER,
            greetingText = "Hello",
            audioPath = "/sdcard/greeting.mp3",
            greetingKind = PluginContract.GREETING_FILE,
        )
        val parsed = PluginContract.Config.parse(config.serialise())!!
        assertEquals(config, parsed)
        assertEquals(PluginContract.GREETING_FILE, parsed.greetingKind)
        assertTrue(parsed.blurb().contains("audio file"))
    }

    @Test fun theChosenProfilesRoundTripWithTheTypedValues() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_CALL,
            callNumber = "123",
            answerProfileId = "answer-id",
            callProfileId = "call-id",
            customGreetingId = "custom-id",
        )
        val parsed = PluginContract.Config.parse(config.serialise())!!
        assertEquals(config, parsed)
        assertEquals("answer-id", parsed.answerProfileId)
        assertEquals("call-id", parsed.callProfileId)
        assertEquals("custom-id", parsed.customGreetingId)
    }

    @Test fun anOldConfigHasNoChosenGreeting() {
        val wire = PluginContract.Config(kind = PluginContract.KIND_ANSWER, greetingText = "Hi")
            .serialise().split("|").dropLast(3).joinToString("|").replaceFirst("8|", "6|")
        val parsed = PluginContract.Config.parse(wire)!!
        assertEquals("", parsed.greetingKind)
        assertEquals("", parsed.answerProfileId)
        assertEquals("", parsed.callProfileId)
        assertEquals("Hi", parsed.greetingText)
    }

    @Test fun anOldTextOnlyConfigHasNoAudioFile() {
        val old = "2|answer|0|1|1|default|default|default|Hello|ZGVmYXVsdA=="
        val parsed = PluginContract.Config.parse(old)!!
        assertEquals("Hello", parsed.greetingText)
        assertEquals("", parsed.audioPath)
    }

    @Test fun blurbSaysWhatTheActionDoes() {
        assertEquals(
            "Toggle AM Call Assistant: off",
            PluginContract.Config(kind = PluginContract.KIND_MAIN, mainOn = false).blurb(),
        )
        assertTrue(
            PluginContract.Config(kind = PluginContract.KIND_ANSWER, answerOn = true, delay = "10")
                .blurb().contains("after 10s"),
        )
        assertTrue(
            PluginContract.Config(kind = PluginContract.KIND_ANSWER).blurb()
                .contains("answer's setting"),
        )
        assertTrue(
            PluginContract.Config(kind = PluginContract.KIND_ANSWER, longest = "300")
                .blurb().contains("longest 5 minutes"),
        )
    }

    @Test fun theDropdownOptionsAreWhatTheUserAskedFor() {
        assertEquals(
            listOf("default", "5", "10", "15", "20", "25", "30"),
            PluginContract.DELAY_OPTIONS.map { it.first },
        )
        assertEquals(
            listOf("default", "short", "same"),
            PluginContract.DND_OPTIONS.map { it.first },
        )
        // The call duration is no longer a dropdown: it is typed in seconds.
        // The sentinel still reads "App's Setting" in the remaining dropdowns.
        assertEquals("App's Setting", PluginContract.DELAY_OPTIONS.first().second)
        assertEquals("App's Setting", PluginContract.DND_OPTIONS.first().second)
    }
}
