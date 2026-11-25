package com.adiraimaji.amcallassistant.ui

import com.adiraimaji.amcallassistant.answering.VoiceOption
import org.junit.Assert.assertEquals
import org.junit.Test

/** The voice list on a phone with many engines: a typed word finds the right
 *  one, and the Tasker default stays first only where it belongs. */
class VoicePickerTest {

    private val voices = listOf(
        VoiceOption("en-us-female", "English (US), female"),
        VoiceOption("en-gb-male", "English (UK), male"),
        VoiceOption("ta-female", "Tamil, female"),
    )

    @Test fun anEmptyQueryShowsEveryVoice() {
        assertEquals(voices, matchingVoices(voices, "", null))
    }

    @Test fun aQueryNarrowsByLabelCaseInsensitively() {
        assertEquals(
            listOf(VoiceOption("en-us-female", "English (US), female")),
            matchingVoices(voices, "ENGLISH (us)", null),
        )
    }

    @Test fun aQueryWithNoMatchShowsNothing() {
        assertEquals(emptyList<VoiceOption>(), matchingVoices(voices, "French", null))
    }

    @Test fun theTaskerDefaultIsShownFirstAndCanBeFound() {
        val default = "" to "App's Setting"
        assertEquals(
            listOf(VoiceOption("ta-female", "Tamil, female")),
            matchingVoices(voices, "tamil", default),
        )
        assertEquals("App's Setting", matchingVoices(voices, "setting", default).single().label)
    }
}
