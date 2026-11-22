package com.adiraimaji.amcallassistant.recordings

import com.adiraimaji.amcallassistant.calls.VoipApps
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The names the recorder gives its files (Checks.recfileTag, messageRecfile,
 * takenRecfile), as every reader in the app reads them: when the call began,
 * which way it went, where a message or a call taken over starts, and which
 * messenger it was. One row per kind of name the recorder writes, and a few it
 * does not, read by each reader as it reads them today - quirks included: a
 * messenger file never reads as incoming by its "_in." tag, which it does not
 * have, and a start with nothing after it is no start.
 *
 * amcallassistant_rec_<ms>[_msg<ms>|_msgnone|_msgfail][_from<ms>]_<in|out>.dat for a
 * phone call; amcallassistant_rec_<ms>[_in|_out]_voip-<package>.dat for a messenger's.
 */
class RecorderFileNamesTest {

    private data class Read(
        val start: Long?,
        val incoming: Boolean,
        val message: Long?,
        val taken: Long?,
        val app: String?,
        val appLabel: String?,
        val appIncoming: Boolean?,
    )

    private fun read(name: String) = Read(
        start = RecordingSaver.startMillis(name),
        incoming = RecordingStore.isIncoming(name),
        message = RecordingStore.messageStartMs(name),
        taken = RecordingStore.takenStartMs(name),
        app = VoipApps.forRecfile(name)?.pkg,
        appLabel = VoipApps.forRecfile(name)?.label,
        appIncoming = VoipApps.directionOf(name),
    )

    private val at = 1_792_661_198_533L

    @Test fun eachNameTheRecorderWritesReadsAsItSays() {
        val rows = listOf(
            "amcallassistant_rec_${at}_in.dat" to Read(at, true, null, null, null, null, null),
            "amcallassistant_rec_${at}_out.dat" to Read(at, false, null, null, null, null, null),
            // A message: where it starts, after the greeting.
            "amcallassistant_rec_${at}_msg8120_in.dat" to Read(at, true, 8120, null, null, null, null),
            "amcallassistant_rec_${at}_msg0_in.dat" to Read(at, true, 0, null, null, null, null),
            "amcallassistant_rec_${at}_msgnone_in.dat" to Read(at, true, RecordingStore.MESSAGE_NONE, null, null, null, null),
            "amcallassistant_rec_${at}_msgfail_in.dat" to Read(at, true, RecordingStore.MESSAGE_FAILED, null, null, null, null),
            // A call taken over from the answering machine: where it starts.
            "amcallassistant_rec_${at}_from8120_in.dat" to Read(at, true, null, 8120, null, null, null),
            "amcallassistant_rec_${at}_from0_out.dat" to Read(at, false, null, 0, null, null, null),
            // A messenger's call; Google Meet's with the way Telecom told.
            "amcallassistant_rec_${at}_voip-com.whatsapp.dat" to
                Read(at, false, null, null, "com.whatsapp", "WhatsApp", null),
            "amcallassistant_rec_${at}_in_voip-com.google.android.apps.tachyon.dat" to
                Read(at, false, null, null, "com.google.android.apps.tachyon", "Google Meet", true),
            "amcallassistant_rec_${at}_out_voip-com.google.android.apps.tachyon.dat" to
                Read(at, false, null, null, "com.google.android.apps.tachyon", "Google Meet", false),
            // A package not on the list is still a messenger's.
            "amcallassistant_rec_${at}_voip-org.example.chat.dat" to
                Read(at, false, null, null, "org.example.chat", "Messenger", null),
        )
        rows.forEach { (name, expected) -> assertEquals(name, expected, read(name)) }
    }

    @Test fun namesTheRecorderDoesNotWriteReadAsNothing() {
        val nothing = Read(null, false, null, null, null, null, null)
        listOf("", "recording.dat", "amcallassistant_rec_.dat", "amcallassistant_rec_abc_out.dat").forEach {
            assertEquals(it, nothing, read(it))
        }
        // A start with no tag after it is no start.
        assertEquals(nothing, read("amcallassistant_rec_$at.dat"))
        // A direction is read from "_in." alone, wherever it is.
        assertEquals(nothing.copy(incoming = true), read("amcallassistant_rec_x_in.dat"))
        assertEquals(nothing.copy(start = at), read("amcallassistant_rec_${at}_input.dat"))
        // An empty package is no messenger.
        assertEquals(nothing.copy(start = at), read("amcallassistant_rec_${at}_voip-.dat"))
    }
}
