package com.adiraimaji.amcallassistant.answering

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The WAV a text-to-speech engine writes, and its conversion to the PCM the
 *  recorder plays. Pure: no Android engine needed. */
class SpokenGreetingTest {

    /** A minimal RIFF/WAVE of 16-bit PCM, as Android's TTS writes one. */
    private fun wav(rate: Int, channels: Int, samples: ShortArray): ByteArray {
        val pcm = Greeting.toBytes(samples)
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(rate)
            .putInt(rate * channels * 2)
            .putShort((channels * 2).toShort())
            .putShort(16)
            .array()
        return ByteBuffer.allocate(12 + 8 + fmt.size + 8 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(4 + 8 + fmt.size + 8 + pcm.size)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(fmt.size)
            .put(fmt)
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcm.size)
            .put(pcm)
            .array()
    }

    private val samples = shortArrayOf(0, 1000, -2000, 30000, -32768)

    @Test fun aWavGivesBackItsHeaderAndSamples() {
        val decoded = decodeWav(wav(24_000, 1, samples))
        assertEquals(24_000, decoded.rate)
        assertEquals(1, decoded.channels)
        assertArrayEquals(samples, decoded.samples)
    }

    @Test fun twoChannelsBecomeOne() {
        val decoded = decodeWav(wav(48_000, 2, shortArrayOf(1000, 3000, 2000, 4000)))
        assertArrayEquals(shortArrayOf(2000, 3000), toMono(decoded))
    }

    @Test fun aMonoWavIsNotTouched() {
        val mono = Wav(48_000, 1, samples)
        assertTrue(toMono(mono) === mono.samples)
    }

    @Test fun theRecordersRateNeedsNoWork() {
        assertTrue(resample(samples, 48_000, 48_000) === samples)
    }

    @Test fun aLowerRateIsRaisedWithoutChangingThePitch() {
        val rate = 24_000
        val tone = ShortArray(rate / 10) { (sin(2.0 * PI * 1000 * it / rate) * 12000).toInt().toShort() }
        val out = resample(tone, rate, 48_000)
        assertEquals(tone.size * 2, out.size)
        // A 1 kHz tone keeps its amplitude when the rate is doubled: a sample
        // repeated instead of interpolated would not change it this cleanly.
        var peak = 0
        for (sample in out) peak = maxOf(peak, abs(sample.toInt()))
        assertTrue("peak was $peak", peak in 10_000..12_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aFileThatIsNotAWavIsRefused() {
        decodeWav("not a wav at all".toByteArray())
        Unit
    }
}
