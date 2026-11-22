package com.adiraimaji.amcallassistant.tasker

import androidx.core.content.edit
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class TemporaryTest {
    private val context = RuntimeEnvironment.getApplication()

    @After fun clean() {
        // Remove the snapshots the tests leave, so a test's default state is
        // not handed to the next one.
        com.adiraimaji.amcallassistant.Prefs.of(context).edit {
            remove("tasker_snapshot_main")
            remove("tasker_snapshot_answer")
        }
        AnsweringMachine.setEnabled(context, false)
        AnsweringMachine.setDelay(context, AnsweringMachine.DEFAULT_DELAY)
        AnsweringMachine.setDndShort(context, true)
        AnsweringMachine.setLongest(context, AnsweringMachine.DEFAULT_LONGEST)
        Greeting.delete(context)
        RecorderSwitch.set(context, true)
    }

    @Test fun theMainSwitchIsRestored() {
        RecorderSwitch.set(context, true)
        Temporary.snapshotMain(context)
        RecorderSwitch.set(context, false)
        assertFalse(RecorderSwitch.isOn(context))
        assertTrue(Temporary.revertMain(context))
        assertTrue(RecorderSwitch.isOn(context))
        assertFalse(Temporary.hasMain(context))
    }

    @Test fun aSecondSnapshotDoesNotOverwriteTheFirst() {
        RecorderSwitch.set(context, true)
        Temporary.snapshotMain(context)
        RecorderSwitch.set(context, false)
        Temporary.snapshotMain(context) // must keep the original "true"
        assertTrue(Temporary.revertMain(context))
        assertTrue(RecorderSwitch.isOn(context))
    }

    @Test fun revertingWithoutASnapshotIsANoOp() {
        assertFalse(Temporary.revertMain(context))
        assertFalse(Temporary.revertAnswer(context))
    }

    @Test fun allAnsweringSettingsAreRestored() {
        AnsweringMachine.setEnabled(context, true)
        AnsweringMachine.setDelay(context, 10)
        AnsweringMachine.setDndShort(context, false)
        AnsweringMachine.setLongest(context, 30)
        Greeting.save(context, Greeting.toBytes(shortArrayOf(1, 2, 3, 4)))
        Temporary.snapshotAnswer(context)

        AnsweringMachine.setEnabled(context, false)
        AnsweringMachine.setDelay(context, 25)
        AnsweringMachine.setDndShort(context, true)
        AnsweringMachine.setLongest(context, 120)
        Greeting.delete(context)

        assertTrue(Temporary.revertAnswer(context))
        assertTrue(AnsweringMachine.enabled(context))
        assertEquals(10, AnsweringMachine.delay(context))
        assertFalse(AnsweringMachine.dndShort(context))
        assertEquals(30, AnsweringMachine.longest(context))
        assertTrue(Greeting.present(context))
        assertFalse(Temporary.hasAnswer(context))
    }

    @Test fun anAbsentGreetingStaysAbsentAfterRevert() {
        Greeting.delete(context)
        Temporary.snapshotAnswer(context)
        Greeting.save(context, Greeting.toBytes(shortArrayOf(5, 6)))
        Temporary.revertAnswer(context)
        assertFalse(Greeting.present(context))
    }
}
