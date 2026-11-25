package com.adiraimaji.amcallassistant.answering

import android.content.Context
import android.net.Uri
import com.adiraimaji.amcallassistant.audio.PcmDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ShortBuffer

/**
 * An audio file as the PCM the recorder plays: mono, 16-bit, 48 kHz.
 *
 * Android's own MediaCodec does the decoding, so anything it can read - WAV,
 * MP3, M4A/AAC, Ogg, FLAC and the rest - works without a library. A file over
 * the greeting's thirty-second limit is cut there, which is the same limit a
 * recorded take has.
 */
object AudioGreeting {

    /** A path a Tasker variable filled in, or a URI from the file picker. */
    fun uri(value: String): Uri {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("content://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) -> Uri.parse(trimmed)
            else -> Uri.fromFile(File(trimmed))
        }
    }

    /** Decode [source], or null when Android cannot read it as audio. */
    suspend fun pcm(context: Context, source: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            var rate = 0
            var channels = 0
            val chunks = ArrayList<ShortArray>()
            var total = 0
            PcmDecoder.decode(
                context = context,
                uri = source,
                onFormat = { r, c -> rate = r; channels = c },
                onPcm = { buffer: ShortBuffer, _ ->
                    val samples = ShortArray(buffer.remaining())
                    buffer.get(samples)
                    chunks.add(samples)
                    total += samples.size
                    // One more chunk past the limit is harmless: it is cut
                    // below. Most files report their duration up front, and
                    // the decoder stops as soon as it has enough.
                    total.toLong() * 1_000_000L / (rate.coerceAtLeast(1) * channels.coerceAtLeast(1)) <=
                        Greeting.MAX_SECONDS.toLong() * 1_000_000L
                },
            )
            if (rate <= 0 || channels <= 0 || total == 0) return@runCatching null
            val merged = ShortArray(total)
            var at = 0
            chunks.forEach { samples ->
                samples.copyInto(merged, at)
                at += samples.size
            }
            val mono = toMono(Wav(rate, channels, merged))
            val resampled = resample(mono, rate, Greeting.RATE)
            val limit = Greeting.MAX_SECONDS * Greeting.RATE
            Greeting.toBytes(if (resampled.size > limit) resampled.copyOf(limit) else resampled)
        }.getOrNull()
    }
}
