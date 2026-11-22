package com.adiraimaji.amcallassistant.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The part of the update check that can be wrong without a phone: order and
 *  the one asset name the updater looks for. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class UpdaterTest {

    @Test fun versionOrder() {
        assertTrue(Updater.newer("v1.1", "1.0"))
        assertTrue(Updater.newer("v1.10", "1.9"))
        assertTrue(Updater.newer("1.0.1", "1.0"))
        assertFalse(Updater.newer("v1.0", "1.0"))
        assertFalse(Updater.newer("v1.0", "1.1"))
        assertFalse(Updater.newer("v0.9", "1.0"))
    }

    @Test fun readsTheFixedAssetAndTheReleaseNotes() {
        val json = """{"tag_name":"v1.1","body":"A change list",
            "assets":[{"name":"AM-Call-Assistant.apk",
            "browser_download_url":"https://example.test/AM-Call-Assistant.apk"}]}"""
        val result = Updater.read(json, "1.0")
        assertTrue(result is Updater.Result.Available)
        val release = (result as Updater.Result.Available).release
        assertEquals("1.1", release.version)
        assertEquals("A change list", release.notes)
        assertEquals("https://example.test/AM-Call-Assistant.apk", release.downloadUrl)
    }

    @Test fun anOlderReleaseIsNothingToOffer() {
        val json = """{"tag_name":"v0.9","body":"old",
            "assets":[{"name":"AM-Call-Assistant.apk",
            "browser_download_url":"https://example.test/AM-Call-Assistant.apk"}]}"""
        assertEquals(Updater.Result.Current, Updater.read(json, "1.0"))
    }

    @Test fun aReleaseWithoutTheAssetCannotBeInstalled() {
        val json = """{"tag_name":"v1.1","body":"notes",
            "assets":[{"name":"something-else.apk",
            "browser_download_url":"https://example.test/other.apk"}]}"""
        assertEquals(Updater.Result.Unavailable, Updater.read(json, "1.0"))
    }
}
