package com.adiraimaji.amcallassistant.audio

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

/** The path shown in the greeting field is a real path, not a content URI. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FilePathTest {

    @Test fun aFileUriIsShownAsAPath() {
        val uri = Uri.fromFile(File("/sdcard/Music/greeting.mp3"))
        val path = FilePath.fromUri(RuntimeEnvironment.getApplication(), uri)
        assertEquals("file", uri.scheme)
        assertTrue(path!!.endsWith("greeting.mp3"))
    }
}
