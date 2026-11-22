package com.adiraimaji.amcallassistant.answering

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.roundToInt

/** A voice the phone's text-to-speech offers, by the engine's own id. */
data class VoiceOption(val id: String, val label: String)

/** Turns written words into the PCM the recorder already knows how to play.
 *  A test supplies one that needs no Android TTS engine. */
interface GreetingSpeaker {
    /** The offline voices available now; empty if this phone has no TTS. */
    suspend fun voices(): List<VoiceOption>

    /** [text] spoken as raw 16-bit LE mono PCM at [Greeting.RATE]. */
    suspend fun speak(text: String, voiceId: String?): ByteArray

    /** The engine may go when the dialog that needed it goes. */
    fun close()
}

/**
 * Android's own text-to-speech, writing a WAV into the app's files. It needs
 * no microphone permission and no help from the recorder: the WAV is decoded,
 * resampled to the rate the recorder plays, and handed to [Greeting.process]
 * like a recorded take. Nothing on the call path has to change.
 */
class TextToSpeechGreeting(private val context: Context) : GreetingSpeaker {
    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var said: CompletableDeferred<Unit>? = null

    /** One engine per dialog. The init callback may come on any thread, so
     *  the promise is made before the engine exists. */
    private fun prepared(): CompletableDeferred<Boolean> = synchronized(lock) {
        ready?.let { return it }
        val init = CompletableDeferred<Boolean>()
        ready = init
        val tts = TextToSpeech(context.applicationContext) { status ->
            init.complete(status == TextToSpeech.SUCCESS)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                said?.complete(Unit)
            }

            @Deprecated("The engine calls the overload with a code instead.")
            override fun onError(utteranceId: String?) {
                said?.completeExceptionally(IllegalStateException("the greeting could not be spoken"))
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                said?.completeExceptionally(IllegalStateException("the greeting could not be spoken ($errorCode)"))
            }
        })
        engine = tts
        init
    }

    private suspend fun awaitEngine(): Boolean = withTimeout(INIT_MS) { prepared().await() }

    override suspend fun voices(): List<VoiceOption> {
        if (!awaitEngine()) return emptyList()
        val tts = engine ?: return emptyList()
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val offline = all.filter { !it.isNetworkConnectionRequired }
            .sortedWith(compareBy<Voice> { it.locale.displayName }.thenBy { it.name })
            .map { VoiceOption(it.name, label(it)) }
        // An engine with no named voices still speaks with its default one.
        return offline.ifEmpty { listOf(VoiceOption("", "The phone's default voice")) }
    }

    private fun label(voice: Voice): String = voice.locale.displayName

    override suspend fun speak(text: String, voiceId: String?): ByteArray {
        if (!awaitEngine()) throw IllegalStateException("this phone has no text-to-speech engine")
        val tts = engine ?: throw IllegalStateException("this phone has no text-to-speech engine")
        voiceId?.takeIf { it.isNotEmpty() }?.let { id ->
            tts.voices.orEmpty().firstOrNull { it.name == id }?.let(tts::setVoice)
        }
        val wav = File(context.filesDir, "greeting-written.wav")
        runCatching { wav.delete() }
        val done = CompletableDeferred<Unit>().also { said = it }
        val asked = tts.synthesizeToFile(text, Bundle(), wav, UTTERANCE)
        if (asked != TextToSpeech.SUCCESS) {
            said = null
            throw IllegalStateException("text to speech refused the greeting ($asked)")
        }
        withTimeout(SYNTHESIS_MS) { done.await() }
        said = null
        val decoded = decodeWav(wav.readBytes())
        runCatching { wav.delete() }
        return Greeting.toBytes(resample(toMono(decoded), decoded.rate, Greeting.RATE))
    }

    override fun close() = synchronized(lock) {
        ready?.takeIf { !it.isCompleted }?.complete(false)
        said?.takeIf { !it.isCompleted }?.completeExceptionally(CancellationException())
        said = null
        ready = null
        engine?.shutdown()
        engine = null
        Unit
    }

    private companion object {
        const val UTTERANCE = "amcallassistant-written-greeting"
        const val INIT_MS = 10_000L
        const val SYNTHESIS_MS = 30_000L
    }
}

/** The WAV the TTS engine writes: header fields and the raw samples. */
internal data class Wav(val rate: Int, val channels: Int, val samples: ShortArray)

private fun le16(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

private fun le32(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or
        ((bytes[at + 2].toInt() and 0xff) shl 16) or ((bytes[at + 3].toInt() and 0xff) shl 24)

private fun four(bytes: ByteArray, at: Int): String = String(bytes, at, 4, Charsets.US_ASCII)

/** Read a RIFF/WAVE of 16-bit PCM. Android's engines write one, but its bits
 *  per sample and rate are the engine's, not ours, so they are read here. */
internal fun decodeWav(bytes: ByteArray): Wav {
    require(bytes.size >= 12 && four(bytes, 0) == "RIFF" && four(bytes, 8) == "WAVE") { "not a WAV file" }
    var at = 12
    var format = 0
    var channels = 0
    var rate = 0
    var bits = 0
    var data: ByteArray? = null
    while (at + 8 <= bytes.size) {
        val id = four(bytes, at)
        val size = le32(bytes, at + 4)
        require(size in 0..bytes.size) { "a broken WAV chunk" }
        val body = at + 8
        require(body + size <= bytes.size) { "a WAV chunk past the end of the file" }
        when (id) {
            "fmt " -> if (size >= 16) {
                format = le16(bytes, body)
                channels = le16(bytes, body + 2)
                rate = le32(bytes, body + 4)
                bits = le16(bytes, body + 14)
            }
            "data" -> data = bytes.copyOfRange(body, body + size)
        }
        at = body + size + (size and 1)
    }
    val pcm = data ?: throw IllegalArgumentException("no audio in the WAV")
    require(channels in 1..8 && rate > 0) { "a broken WAV header" }
    // WAVE_FORMAT_EXTENSIBLE (0xFFFE) still carries PCM here.
    require(bits == 16 && (format == 1 || format == 0xFFFE)) { "not 16-bit PCM WAV" }
    return Wav(rate, channels, ShortArray(pcm.size / 2) { i -> le16(pcm, 2 * i).toShort() })
}

/** One channel: a stereo engine's two sides summed, not one thrown away. */
internal fun toMono(wav: Wav): ShortArray {
    if (wav.channels == 1) return wav.samples
    val out = ShortArray(wav.samples.size / wav.channels)
    for (i in out.indices) {
        var sum = 0
        for (c in 0 until wav.channels) sum += wav.samples[i * wav.channels + c].toInt()
        out[i] = (sum / wav.channels).toShort()
    }
    return out
}

/** Linear interpolation to the recorder's rate. Speech is not a pure tone,
 *  and the engine's rate is usually below 48 kHz, so a sample cannot just be
 *  repeated: that would ring at the wrong pitch. */
internal fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
    if (from == to || samples.isEmpty()) return samples
    val out = ShortArray(samples.size.toLong().times(to).div(from).toInt())
    for (i in out.indices) {
        val at = i.toDouble() * from / to
        val a = minOf(at.toInt(), samples.lastIndex)
        val b = minOf(a + 1, samples.lastIndex)
        val part = at - a
        val value = samples[a].toDouble() * (1.0 - part) + samples[b].toDouble() * part
        out[i] = value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
}
