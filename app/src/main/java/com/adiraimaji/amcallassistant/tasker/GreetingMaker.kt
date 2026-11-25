package com.adiraimaji.amcallassistant.tasker

import android.content.Context
import android.util.Log
import com.adiraimaji.amcallassistant.answering.AudioGreeting
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.answering.GreetingSpeaker
import com.adiraimaji.amcallassistant.answering.TextToSpeechGreeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns typed words into the PCM the recorder plays, off the main thread,
 * using the same engine and the same path a written greeting in the app uses.
 * Kept out of the screen so a Tasker action can use it too.
 */
object GreetingMaker {
    private const val TAG = "AMCallAssistant"

    /** An audio file as the greeting: its path may hold a Tasker variable that
     *  has already been replaced before the action fired. */
    suspend fun fromAudio(context: Context, path: String): ByteArray? {
        if (path.isBlank()) return null
        return runCatching {
            AudioGreeting.pcm(context.applicationContext, AudioGreeting.uri(path))
        }.onFailure { Log.w(TAG, "tasker: the audio greeting could not be read", it) }.getOrNull()
    }

    suspend fun make(context: Context, text: String, voiceId: String?): ByteArray? {
        if (text.isBlank()) return null
        val speaker: GreetingSpeaker = TextToSpeechGreeting(context.applicationContext)
        try {
            val voices = runCatching { speaker.voices() }.getOrDefault(emptyList())
            val chosen = voiceId?.takeIf { it.isNotEmpty() && it != PluginContract.DEFAULT }
                ?.let { id -> voices.firstOrNull { it.id == id } }
                ?: voices.firstOrNull()
            val pcm = speaker.speak(text, chosen?.id)
            return when (val processed = withContext(Dispatchers.Default) { Greeting.process(pcm) }) {
                is Greeting.Take.Usable -> processed.pcm
                else -> {
                    Log.w(TAG, "tasker: the typed greeting had no usable speech")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tasker: text to speech failed for the greeting", e)
            return null
        } finally {
            speaker.close()
        }
    }
}
