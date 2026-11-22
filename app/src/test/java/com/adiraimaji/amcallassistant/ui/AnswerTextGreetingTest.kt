package com.adiraimaji.amcallassistant.ui

import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.answering.GreetingSpeaker
import com.adiraimaji.amcallassistant.answering.VoiceOption
import com.adiraimaji.amcallassistant.capture.FakeRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/** A written greeting goes down the very path a recorded take goes down:
 *  levelled, kept as the master copy, and sent to the recorder to play. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class AnswerTextGreetingTest {

    private val context = RuntimeEnvironment.getApplication()
    private val recorder = FakeRecorder(context).apply {
        replies["N"] = "ANSWER 1,60,music"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val said = CopyOnWriteArrayList<String>()
    /** Speech-like enough for Greeting.process: quiet, two seconds of tone,
     *  quiet. The recorder would have sent the same shape from its mic. */
    private val spoken = run {
        val quiet = ShortArray(Greeting.RATE / 5)
        val speech = ShortArray(Greeting.RATE * 2) {
            (sin(2.0 * PI * 1000 * it / Greeting.RATE) * 12_000).toInt().toShort()
        }
        Greeting.toBytes(quiet + speech + quiet)
    }
    private val speaker = object : GreetingSpeaker {
        @Volatile var fail = false
        @Volatile var closed = false
        override suspend fun voices() = listOf(VoiceOption("v1", "English, female"))
        override suspend fun speak(text: String, voiceId: String?): ByteArray {
            if (fail) throw IllegalStateException("no engine")
            return spoken
        }
        override fun close() { closed = true }
    }
    private val controller = AnswerController(context, scope, { said += it }, speaker = speaker)

    @After fun clean() {
        scope.cancel()
        recorder.close()
        Greeting.delete(context)
        Greeting.letGoOfAnyTake()
    }

    private fun eventually(what: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!done()) {
            if (System.nanoTime() > deadline) fail("never happened: $what")
            Thread.sleep(10)
        }
    }

    /** A voice, ready for the dialog. */
    @Test fun voicesAreListed() {
        controller.loadTtsVoices()
        eventually("the voices") { controller.state.value.ttsVoices.isNotEmpty() }
        assertEquals(VoiceOption("v1", "English, female"), controller.state.value.ttsVoices.single())
    }

    /** The written greeting is saved and uploaded exactly as a take's is. */
    @Test fun itIsKeptAndSentToTheRecorder() {
        val usable = Greeting.process(spoken) as Greeting.Take.Usable
        recorder.replies["GP"] = "GREETING ${usable.pcm.size}"
        val done = AtomicReference<Boolean?>()
        controller.useTextGreeting("  Hello, you have reached me.  ", "v1") { done.set(it) }
        eventually("the greeting to be made") { done.get() != null }
        assertEquals(true, done.get())
        assertTrue(Greeting.present(context))
        assertEquals(usable.seconds, controller.state.value.greetingSeconds!!, 0.01)
        eventually("the greeting to be uploaded") {
            recorder.heard.any { it.key == "GP" && it.payload.size > 5 }
        }
        assertFalse(said.toString(), said.any { "get the new greeting" in it })
    }

    /** An engine that fails leaves the greeting already there alone. */
    @Test fun aFailureKeepsTheOldGreeting() {
        Greeting.save(context, spoken)
        controller.reload()
        val before = controller.state.value.greetingSeconds
        speaker.fail = true
        val done = AtomicReference<Boolean?>()
        controller.useTextGreeting("Hello", "v1") { done.set(it) }
        eventually("the failure") { done.get() != null }
        assertEquals(false, done.get())
        assertEquals(before, controller.state.value.greetingSeconds)
        assertEquals("Text to speech could not make a greeting. Try another voice.", controller.state.value.ttsError)
    }

    /** Blank words make nothing, and need no engine. */
    @Test fun blankTextDoesNothing() {
        controller.useTextGreeting("   ", null) { fail("should not be called") }
        assertNull(controller.state.value.ttsError)
        assertFalse(Greeting.present(context))
    }

    /** Leaving the dialog shuts the engine down. */
    @Test fun closingTheDialogClosesTheSpeaker() {
        controller.closeTts()
        assertTrue(speaker.closed)
    }
}
