package com.adiraimaji.amcallassistant.answering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** A path typed in a Tasker action, or a URI from the file picker, both reach
 *  the decoder as something Android can open. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AudioGreetingTest {

    @Test fun aPlainPathIsAFileUri() {
        val uri = AudioGreeting.uri("  /sdcard/Music/greeting.mp3  ")
        assertEquals("file", uri.scheme)
        assertTrue(uri.path!!.endsWith("greeting.mp3"))
    }

    @Test fun aContentUriFromThePickerIsKept() {
        assertTrue(AudioGreeting.uri("content://media/external/audio/12").toString().startsWith("content://"))
    }
}
