package com.adiraimaji.amcallassistant.contract

import com.adiraimaji.amcallassistant.Prefs
import com.adiraimaji.amcallassistant.answering.AnswerDiagnostics
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.calls.AfterCall
import com.adiraimaji.amcallassistant.calls.CallRules
import com.adiraimaji.amcallassistant.calls.CallTracking
import com.adiraimaji.amcallassistant.calls.CoverageHistory
import com.adiraimaji.amcallassistant.calls.VoipCallNotes
import com.adiraimaji.amcallassistant.capture.DebugNotificationCleaner
import com.adiraimaji.amcallassistant.capture.RecordingMode
import com.adiraimaji.amcallassistant.playback.BothEarsSetting
import com.adiraimaji.amcallassistant.playback.PlayThrough
import com.adiraimaji.amcallassistant.playback.PlayThroughSetting
import com.adiraimaji.amcallassistant.recorder.DebugSwitch
import com.adiraimaji.amcallassistant.recorder.DebugSwitchesOff
import com.adiraimaji.amcallassistant.recorder.KeepPaired
import com.adiraimaji.amcallassistant.recorder.OffMode
import com.adiraimaji.amcallassistant.recorder.RecorderHealth
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import com.adiraimaji.amcallassistant.recorder.StartWithoutWifi
import com.adiraimaji.amcallassistant.recordings.FileNames
import com.adiraimaji.amcallassistant.recordings.QualitySetting
import com.adiraimaji.amcallassistant.recordings.RecordingQuality
import com.adiraimaji.amcallassistant.recordings.Retention
import com.adiraimaji.amcallassistant.ui.theme.LightDark
import com.adiraimaji.amcallassistant.ui.theme.Look
import com.adiraimaji.amcallassistant.ui.theme.LookChoice
import com.adiraimaji.amcallassistant.ui.theme.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * What the app keeps in its preferences, under the same names, read the same
 * way on a fresh install.
 *
 * Every setting lives with the object that owns it, and the 1.0 restructuring
 * moves those objects. A key renamed in a move compiles and loses every
 * user's setting on update; an enum constant renamed loses the setting stored
 * by its name; a default changed changes every fresh install. Nothing else
 * would notice: each owner's own tests write and read through the new name.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class PreferencesInventoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun everyKeyKeepsItsName() {
        val declared = Regex("""const val \w*KEY\w* = "([^"]+)"""").findAll(Repo.app).map { it.groupValues[1] }.toList()
        assertEquals(KEYS.toSortedSet(), declared.toSortedSet())
        // And none declared twice: two owners of one key write over each other.
        assertEquals(emptySet<String>(), declared.groupingBy { it }.eachCount().filterValues { it > 1 }.keys)
    }

    @Test
    fun theFilesKeepTheirNames() {
        assertEquals("amcallassistant", Prefs.FILE)
        assertTrue("private const val PREFS = \"amcallassistant_colours\"" in Repo.app)
        assertTrue("private const val READ_FILE = \"amcallassistant_tags_read\"" in Repo.app)
        assertTrue("private const val FILE = \"amcallassistant_starts\"" in Repo.app)
        // The one key that was written as a literal twice, with no owner of
        // its own, has one now (SelectHint), and is written nowhere else.
        assertEquals(1, Regex("\"select_hint_seen\"").findAll(Repo.app).count())
    }

    @Test
    fun everyEnumStoredByNameKeepsItsConstants() {
        assertEquals(listOf("MEMO", "DECK", "MULTITRACK"), Look.entries.map { it.name })
        assertEquals(listOf("PHONE", "LIGHT", "DARK"), LightDark.entries.map { it.name })
        assertEquals(listOf("AUTOMATIC", "ON_DEMAND"), RecordingMode.entries.map { it.name })
        assertEquals(listOf("DATE", "DATE_AND_CALLER", "DATE_AND_NUMBER", "HONOR"), FileNames.Format.entries.map { it.name })
        assertEquals(listOf("ALL", "ALL_BUT", "ONLY", "NONE"), CallRules.Contacts.entries.map { it.name })
        assertEquals(listOf("KEEP_DEBUGGING", "HIDE_DEVELOPER_OPTIONS", "ALL_DEBUGGING_OFF"), OffMode.entries.map { it.name })
        assertEquals(listOf("DEVELOPER_OPTIONS", "WIRELESS_DEBUGGING", "USB_DEBUGGING"), DebugSwitch.entries.map { it.name })
        assertEquals(listOf("OFF", "ASK_FIRST", "AUTOMATIC"), CoverageHistory.State.entries.map { it.name })
        assertEquals(
            listOf("NEEDS_WIFI", "NETWORK_NOT_ALLOWED", "CANNOT_CONNECT", "CANNOT_START"),
            RecorderHealth.Problem.entries.map { it.name },
        )
        assertEquals(listOf("WORKS", "LIKELY", "FAILS", "INCONCLUSIVE"), AnswerDiagnostics.Verdict.entries.map { it.name })
        assertEquals(listOf("VOLTE", "ROUTES"), AnswerDiagnostics.Cleared.entries.map { it.name })
        assertEquals(listOf("LOUDSPEAKER", "LOUDSPEAKER_AND_EAR", "EARPIECE"), PlayThrough.entries.map { it.name })
        assertEquals(listOf("SMALLEST", "STANDARD", "HIGH", "HIGHEST"), RecordingQuality.entries.map { it.name })
    }

    @Test
    fun aFreshInstallReadsTheDefaults() {
        assertTrue("recording on", RecorderSwitch.isOn(context))
        assertEquals(RecordingMode.AUTOMATIC, RecordingMode.of(context))
        assertTrue("exact call tracking on", CallTracking.enabled(context))
        assertEquals(CallRules.Contacts.ALL, CallRules.contacts(context))
        assertTrue("other numbers kept", CallRules.otherNumbers(context))
        assertEquals(false, AnsweringMachine.enabled(context))
        assertEquals(20, AnsweringMachine.delay(context))
        assertEquals(true, AnsweringMachine.dndShort(context))
        assertEquals(60, AnsweringMachine.longest(context))
        assertEquals("default", AnsweringMachine.sim(context))
        assertEquals("music", AnsweringMachine.route(context))
        assertEquals(FileNames.Format.DATE, FileNames.format(context))
        assertEquals(RecordingQuality.STANDARD, QualitySetting.of(context))
        assertEquals(OffMode.KEEP_DEBUGGING, DebugSwitchesOff.mode(context))
        assertEquals(emptySet<DebugSwitch>(), DebugSwitchesOff.switchedOff(context))
        assertTrue("after-call notice on", AfterCall.enabled(context))
        assertEquals(PlayThrough.LOUDSPEAKER_AND_EAR, PlayThroughSetting.of(context))
        assertTrue("both sides in both ears", BothEarsSetting.of(context))
        assertEquals(false, VoipCallNotes.enabled(context))
        assertEquals(false, StartWithoutWifi.enabled(context))
        assertEquals(0, Retention.maxAgeDays(context))
        assertEquals(0, Retention.minSeconds(context))
        // Looks reads the preferences once a process and keeps what it read:
        // forgotten first, so this is a fresh install's reading and not what
        // an earlier test in this JVM left, and again after, for the next.
        fun forgetTheLook() = Looks::class.java.getDeclaredField("flow").apply { isAccessible = true }.set(Looks, null)
        forgetTheLook()
        try {
            assertEquals(LookChoice(Look.MEMO, LightDark.PHONE), Looks.choice(context))
        } finally {
            forgetTheLook()
        }
        assertEquals(false, DebugNotificationCleaner.asked(context))
        // Asked, with Android's timeout on as a fresh phone has it, and
        // nothing waiting for a recorder.
        assertTrue("keep paired asked", KeepPaired.needsChoice(context))
        assertEquals(null, KeepPaired.waiting(context))
    }

    private companion object {
        /** Every `const val ...KEY... = "..."` in app/src/main on 2026-11-01
         *  (0.26), and those since: answer_test (0.30), answer_mixer_ and
         *  answer_routes_by_path (0.31), select_hint_seen (given its owner),
         *  answer_cleared_by_update and answer_volte_cleared (1.0),
         *  play_through, keep_paired_asked, keep_paired_refused and
         *  keep_paired_wish (1.1), recording_quality, both_ears,
         *  old_pages_done_in, notifications_off_dismissed,
         *  battery_restricted_dismissed_words, battery_restricted_told, save_write_failed,
         *  folder_lost_told, heartbeat_version, heartbeat_port, save_journal,
         *  call_parts, lost_messages, name_starts, not_recorded_for and
         *  part_recordings (1.2):
         *  preference keys, plus the few key-named constants that are file
         *  and extra names, which a rename breaks just the same. */
        val KEYS = listOf(
            "adb_private_key.der", "adb_refused_in_a_row", "after_call_notice", "answer_cleared_by_update", "answer_default_profile", "answer_default_sim",
            "answer_delay_s", "answer_dnd_short", "answer_longest_s", "answer_mixer_", "answer_on", "answer_profiles", "answer_profiles_migrated", "answer_result_",
            "answer_route", "answer_routes_by_path", "answer_sim", "answer_sim_count", "answer_stage_one", "answer_test",
            "answer_volte_cleared", "auto_delete_after_days",
            "auto_delete_last_run", "auto_delete_shorter_than", "background_kill_warned_ms", "battery_restricted_dismissed_words", "battery_restricted_told", "both_ears", "cable_stopped_adbd",
            "call_ledger", "call_parts", "call_tracking", "caller_banner_dismissed",
            "calling_pending", "calling_profiles", "calling_recordings",
            "calls_covered_since", "coverage_history",
            "daemon_token", "debug_notification_snoozed", "debug_off_pending", "debug_switched_off",
            "emergency_call_at", "emergency_resumed_at", "file_names", "folder_lost_told", "heartbeat_port", "heartbeat_version", "hide_debug_notification",
            "keep_paired_asked", "keep_paired_refused", "keep_paired_wish", "kept_recordings", "light_dark", "link_token", "look", "lost_messages", "message_recordings", "missed_seen_until",
            "name_callers", "name_directions", "name_starts", "names", "not_recorded_for", "notifications_off_dismissed", "off_mode", "old_pages_done_in", "pairing_code", "port_close_failed_boot",
            "part_recordings", "port_failed_boot", "port_opened_boot", "prompt_ticket", "play_through", "qs_tile_added", "recorder_down_alerted",
            "recorder_down_cause", "recorder_down_since", "recorder_enabled", "recording_mode", "recording_quality",
            "recordings_tree_uri", "rules_chosen", "rules_contacts", "rules_other_numbers", "save_failing", "save_journal", "save_write_failed",
            "select_hint_seen", "setup_background_asked", "setup_complete", "setup_dev_options_confirmed", "setup_paired",
            "setup_method", "start_without_wifi", "tasker_snapshot_answer", "tasker_snapshot_main", "tracked_calls",
            "voip_apps", "voip_call_notes", "voip_names",
            "wireless_debugging_refused_on", "wireless_debugging_switched_on",
        )
    }
}
