package com.adiraimaji.amcallassistant.tasker

import com.adiraimaji.amcallassistant.answering.AnsweringMachine
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
class OneShotTest {
    private val context = RuntimeEnvironment.getApplication()

    @After fun clean() {
        OneShot.clear()
        AnsweringMachine.setEnabled(context, false)
        AnsweringMachine.setDelay(context, AnsweringMachine.DEFAULT_DELAY)
        AnsweringMachine.setDndShort(context, true)
        AnsweringMachine.setLongest(context, AnsweringMachine.DEFAULT_LONGEST)
    }

    @Test fun anOverrideDoesNotTouchTheAppsPreferences() {
        AnsweringMachine.setEnabled(context, false)
        AnsweringMachine.setDelay(context, 25)
        AnsweringMachine.setDndShort(context, true)
        AnsweringMachine.setLongest(context, 120)

        OneShot.start(OneShot.Values(true, 5, false, 30, null, 0L))

        assertEquals(5, AnsweringMachine.delay(context))
        assertFalse(AnsweringMachine.dndShort(context))
        assertEquals(30, AnsweringMachine.longest(context))
        assertTrue(AnsweringMachine.enabled(context))

        // The real preference values are exactly as the user set them.
        assertEquals(25, AnsweringMachine.realDelay(context))
        assertTrue(AnsweringMachine.realDndShort(context))
        assertEquals(120, AnsweringMachine.realLongest(context))
        assertFalse(AnsweringMachine.realEnabled(context))
    }

    @Test fun clearingTheOverrideRestoresTheAppsValues() {
        AnsweringMachine.setDelay(context, 10)
        OneShot.start(OneShot.Values(true, 30, true, 60, null, 0L))
        assertEquals(30, AnsweringMachine.delay(context))
        OneShot.clear()
        assertFalse(OneShot.isActive())
        assertEquals(10, AnsweringMachine.delay(context))
    }

    @Test fun wireReportsTheOneShotValues() {
        AnsweringMachine.setEnabled(context, false)
        OneShot.start(OneShot.Values(true, 5, false, 30, byteArrayOf(1, 2), 0L))
        // On with a greeting supplied by this one call; longest from the config.
        assertEquals("1,30,music", AnsweringMachine.wire(context))
        OneShot.clear()
        // Back to the app's real off value.
        assertEquals("0,60,music", AnsweringMachine.wire(context))
    }
}
