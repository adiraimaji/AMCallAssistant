package com.adiraimaji.amcallassistant.calling

import android.content.Context
import android.media.AudioTrack
import android.util.Log
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.capture.CaptureDaemon
import com.adiraimaji.amcallassistant.tasker.GreetingMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One greeting per Calling Machine profile, in its own file. The answering
 * machine's greeting and the daemon's copy are left alone: a profile's PCM is
 * uploaded only for the call it is about to make, and the app's greeting is
 * put back afterwards.
 */
object CallingGreetings {
    data class State(
        val id: String? = null,
        val seconds: Double? = null,
        val busy: Boolean = false,
        val recording: Boolean = false,
        val tookAt: Long = 0L,
        val error: String? = null,
    )

    private const val DIR = "calling_greetings"
    private const val TAG = "AMCallAssistant"

    /** True while a Calling Machine call is armed or under way. The recorder
     *  holds one greeting: a routine mode push must not delete the one this
     *  call is about to play just because the app's own greeting is absent. */
    @Volatile
    var callingActive = false
        private set

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    private var restoreJob: Job? = null
    private var takeToken: Any? = null
    private var previewTrack: AudioTrack? = null

    fun file(context: Context, id: String): File = File(File(context.filesDir, DIR), "$id.pcm")

    fun present(context: Context, id: String): Boolean = file(context, id).let { it.isFile && it.length() > 0 }

    fun seconds(context: Context, id: String): Double? = file(context, id).takeIf { it.isFile }?.let {
        it.length() / 2.0 / Greeting.RATE
    }

    /** The PCM to play for [id], or null when no greeting has been made. */
    fun pcm(context: Context, id: String): ByteArray? = runCatching {
        file(context, id).takeIf { it.isFile && it.length() > 0 }?.readBytes()
    }.getOrNull()

    fun load(context: Context, id: String) {
        _state.value = State(id = id, seconds = seconds(context, id))
    }

    private fun save(context: Context, id: String, pcm: ByteArray) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val next = File(dir, "$id.new")
        next.writeBytes(pcm)
        check(next.renameTo(file(context, id))) { "could not keep the calling greeting" }
        _state.update { it.copy(id = id, seconds = pcm.size / 2.0 / Greeting.RATE, error = null) }
    }

    fun useText(context: Context, id: String, text: String, voiceId: String?, done: (Boolean) -> Unit) {
        if (_state.value.busy || text.isBlank()) return
        busy(true)
        scope.launch {
            var made = false
            try {
                val pcm = GreetingMaker.make(context.applicationContext, text, voiceId)
                if (pcm == null) {
                    error("That text could not be spoken. Try different words.")
                } else {
                    withContext(Dispatchers.IO) { save(context, id, pcm) }
                    made = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "calling: written greeting failed", e)
                error("That text could not be spoken. Try different words.")
            } finally {
                busy(false)
                done(made)
            }
        }
    }

    fun useAudio(context: Context, id: String, path: String, done: (Boolean) -> Unit) {
        if (_state.value.busy || path.isBlank()) return
        busy(true)
        scope.launch {
            var made = false
            try {
                val pcm = GreetingMaker.fromAudio(context.applicationContext, path)
                if (pcm == null) {
                    error("That file could not be played. Try an MP3, M4A or WAV.")
                } else {
                    withContext(Dispatchers.IO) { save(context, id, pcm) }
                    made = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "calling: audio greeting failed", e)
                error("That file could not be played. Try an MP3, M4A or WAV.")
            } finally {
                busy(false)
                done(made)
            }
        }
    }

    /** Record through the shell-side recorder, but keep the take for this
     *  profile instead of overwriting the answering machine's greeting. */
    fun startTake(context: Context, id: String) {
        if (_state.value.busy || _state.value.recording) return
        val mine = Any()
        if (!Greeting.hold(mine)) return
        takeToken = mine
        stopPreview()
        _state.update { it.copy(id = id, recording = true, tookAt = System.currentTimeMillis(), error = null) }
        work = scope.launch {
            if (!CaptureDaemon.startTake(context.applicationContext)) {
                Greeting.letGo(mine)
                _state.update { it.copy(recording = false, error = "The recorder could not start recording. Is it running?") }
                return@launch
            }
            delay(Greeting.MAX_SECONDS * 1000L + 500L)
            if (_state.value.recording) stopTake(context, id)
        }
    }

    fun stopTake(context: Context, id: String) {
        if (!_state.value.recording) return
        _state.update { it.copy(recording = false, busy = true) }
        work?.cancel()
        val mine = takeToken ?: Any()
        Greeting.make(mine)
        scope.launch {
            try {
                val length = CaptureDaemon.stopTake(context.applicationContext)
                Greeting.letGo(mine)
                if (length == 0L) {
                    error("Nothing was recorded. Try again.")
                    return@launch
                }
                val pcm = CaptureDaemon.fetchTake(context.applicationContext)
                val processed = withContext(Dispatchers.Default) { pcm?.let(Greeting::process) }
                when (processed) {
                    is Greeting.Take.Usable -> withContext(Dispatchers.IO) { save(context, id, processed.pcm) }
                    Greeting.Take.Silent -> error("Nothing was heard. Hold the phone as for a call, and speak.")
                    Greeting.Take.TooShort -> error("That was too short for a greeting. Try again.")
                    null -> error("The recorder did not send the take. Try again.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "calling: take failed", e)
                error("The greeting could not be saved. Try again.")
            } finally {
                Greeting.made(mine)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun delete(context: Context, id: String) {
        runCatching { file(context, id).delete() }
        _state.update { it.copy(seconds = null) }
    }

    fun togglePreview(context: Context, id: String) {
        if (previewTrack != null) {
            stopPreview()
            return
        }
        val pcm = pcm(context, id) ?: return
        stopPreview()
        previewTrack = Greeting.preview(pcm)
    }

    fun stopPreview() {
        previewTrack?.let { runCatching { it.stop(); it.release() } }
        previewTrack = null
    }

    private fun busy(on: Boolean) = _state.update { it.copy(busy = on) }

    private fun error(message: String) = _state.update { it.copy(error = message) }

    /** Put the app's greeting back only when it has one. Never send the
     *  recorder's delete: after the app's data is cleared there is nothing to
     *  restore, and a delete during the next call leaves it with 0 bytes. */
    suspend fun restore(context: Context) {
        try {
            if (Greeting.present(context)) {
                runCatching { CaptureDaemon.syncGreeting(context.applicationContext) }
            }
        } finally {
            callingActive = false
        }
    }

    /** Mark the start of a call and clear anything that could interfere with
     *  the greeting being uploaded for it. */
    fun begin() {
        callingActive = true
        stopPreview()
        cancelRestore()
    }

    /** A restore belongs to one call. A new call cancels the old one's, so a
     *  late restore can never truncate the greeting just armed for this call. */
    fun scheduleRestore(context: Context, seconds: Int) {
        restoreJob?.cancel()
        val app = context.applicationContext
        val holdMs = (if (seconds > 0) seconds + 5 else 300) * 1000L
        restoreJob = scope.launch {
            delay(holdMs)
            restore(app)
        }
    }

    fun cancelRestore() {
        restoreJob?.cancel()
        restoreJob = null
    }

    fun end() {
        callingActive = false
    }
}
