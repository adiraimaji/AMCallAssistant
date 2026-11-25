package com.adiraimaji.amcallassistant.tasker

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.answering.AnswerGreetings
import com.adiraimaji.amcallassistant.answering.AnswerProfiles
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.answering.GreetingSpeaker
import com.adiraimaji.amcallassistant.answering.TextToSpeechGreeting
import com.adiraimaji.amcallassistant.answering.VoiceOption
import com.adiraimaji.amcallassistant.audio.FilePath
import com.adiraimaji.amcallassistant.capture.AudioAccess
import com.adiraimaji.amcallassistant.ui.Capsules
import com.adiraimaji.amcallassistant.ui.CompactField
import com.adiraimaji.amcallassistant.ui.RadioRow
import com.adiraimaji.amcallassistant.ui.SettingsGroup
import com.adiraimaji.amcallassistant.ui.VoicePickerDialog
import com.adiraimaji.amcallassistant.ui.rememberPermissionRequest
import com.adiraimaji.amcallassistant.ui.tap
import com.adiraimaji.amcallassistant.ui.theme.AmCallAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Edit screen for the "AM Answering Machine" plugin action. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class AnswerEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE)
            ?.let { PluginContract.Config.parse(it.getString(PluginContract.FIELD_CONFIG)) }
        val initial = existing?.takeIf { it.kind == PluginContract.KIND_ANSWER }
            ?: PluginContract.Config(kind = PluginContract.KIND_ANSWER)
        // There is no App's Setting capsule any more: an old action that used
        // the sentinel opens with the ordinary minute value instead.
        val startLongest = if (initial.longest == PluginContract.DEFAULT) "60" else initial.longest

        setContent {
            AmCallAssistantTheme {
                val scope = rememberCoroutineScope()
                val haptics = LocalHapticFeedback.current
                val app = applicationContext
                var delay = rememberSaveable { mutableStateOf(initial.delay) }
                var dnd = rememberSaveable { mutableStateOf(initial.dnd) }
                var longest = rememberSaveable { mutableStateOf(startLongest) }
                var text = rememberSaveable { mutableStateOf(initial.greetingText) }
                var audioPath = rememberSaveable { mutableStateOf(initial.audioPath) }
                var voiceId = rememberSaveable { mutableStateOf(initial.voiceId) }
                // Old actions have no choice saved; they used the audio file
                // first, so keep that as their selection when they are opened.
                var greetingKind = rememberSaveable {
                    mutableStateOf(
                        initial.greetingKind.ifBlank {
                            if (initial.audioPath.isNotBlank()) PluginContract.GREETING_FILE
                            else PluginContract.GREETING_TEXT
                        },
                    )
                }
                // Not rememberSaveable: VoiceOption is not Parcelable, and a
                // save during the file picker's onStop crashed the activity.
                // The voices are loaded again on recreation.
                var voices = remember { mutableStateOf<List<VoiceOption>>(emptyList()) }
                var voiceDialog = rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current
                val answerProfiles = remember { AnswerProfiles.all(applicationContext) }
                var selectedProfile = rememberSaveable {
                    mutableStateOf(initial.answerProfileId.ifBlank { PluginContract.PROFILE_CUSTOM })
                }
                var customWarning = rememberSaveable { mutableStateOf(false) }
                fun custom() {
                    val chosen = selectedProfile.value
                    if (chosen != PluginContract.PROFILE_CUSTOM && AnswerGreetings.present(app, chosen)) {
                        // Warn before the dropdown stops being that profile:
                        // its recording cannot be used from Custom.
                        customWarning.value = true
                    }
                    selectedProfile.value = PluginContract.PROFILE_CUSTOM
                    if (greetingKind.value == PluginContract.GREETING_RECORD) {
                        greetingKind.value = PluginContract.GREETING_TEXT
                    }
                }
                val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        audioPath.value = FilePath.fromUri(context, uri) ?: uri.toString()
                        custom()
                    }
                }
                val audioPermission = rememberPermissionRequest { _ -> audioPicker.launch("audio/*") }
                // A typed duration keeps its field even if the number typed
                // happens to be one of the presets.
                var longestMode = rememberSaveable {
                    mutableStateOf(longest.value !in listOf("0", "30", "60", "120"))
                }
                var longestInput = rememberSaveable {
                    mutableStateOf(if (longestMode.value) longest.value else "")
                }
                var problem = rememberSaveable { mutableStateOf<String?>(null) }
                var previewing = rememberSaveable { mutableStateOf(false) }
                var customGreetingId = rememberSaveable {
                    mutableStateOf(initial.customGreetingId.ifBlank { UUID.randomUUID().toString() })
                }
                // No greeting is recorded in this editor: only a profile's
                // existing recording can be played.
                val recordingId = if (selectedProfile.value != PluginContract.PROFILE_CUSTOM) {
                    selectedProfile.value
                } else {
                    ""
                }
                val recorded = recordingId.isNotBlank() && AnswerGreetings.present(app, recordingId)
                val recordSeconds = if (recorded) AnswerGreetings.seconds(app, recordingId) else null
                LaunchedEffect(Unit) {
                    val speaker: GreetingSpeaker = TextToSpeechGreeting(applicationContext)
                    val found = runCatching { speaker.voices() }.getOrDefault(emptyList())
                    voices.value = found
                    if (found.isEmpty()) problem.value = "This phone has no text-to-speech voice"
                    speaker.close()
                }

                val canSave = when (greetingKind.value) {
                    PluginContract.GREETING_FILE -> audioPath.value.isNotBlank()
                    PluginContract.GREETING_RECORD -> recorded
                    else -> text.value.isNotBlank()
                }
                fun saveConfig() {
                    EditResult.send(
                        this@AnswerEditActivity,
                        initial.copy(
                            answerOn = true, revert = false,
                            delay = delay.value,
                            dnd = dnd.value, longest = longest.value,
                            greetingText = text.value,
                            voiceId = voiceId.value,
                            audioPath = audioPath.value,
                            greetingKind = greetingKind.value,
                            answerProfileId = selectedProfile.value,
                            customGreetingId = customGreetingId.value,
                        ),
                    )
                }
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text("Answering Machine") },
                        actions = {
                            TextButton(onClick = { saveConfig() }, enabled = canSave) {
                                Text("OK")
                            }
                        },
                    )
                }) { pad ->
                    Column(
                        Modifier.fillMaxSize().padding(pad).imePadding()
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        Text(
                            "Answer the call that is ringing right now, with these settings, just this once. " +
                                "Nothing in the app's answering machine is changed. " +
                                "App's Setting uses whatever you chose in the app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        SettingsGroup("Profile") {
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    ProfileDropdown(
                                        label = "Profile",
                                        profiles = answerProfiles.map { it.id to it.name.ifBlank { "Profile" } },
                                        selectedId = selectedProfile.value,
                                        onSelect = { id ->
                                            selectedProfile.value = id
                                            if (id != PluginContract.PROFILE_CUSTOM) {
                                                AnswerProfiles.get(app, id)?.let { p ->
                                                    longest.value = p.longest.toString()
                                                    longestMode.value = false
                                                    longestInput.value = p.longest.toString()
                                                    text.value = p.text
                                                    audioPath.value = p.audioPath
                                                    voiceId.value = p.voiceId
                                                    dnd.value = if (p.dndShort) {
                                                        PluginContract.Dnd.SHORT.wire
                                                    } else {
                                                        PluginContract.Dnd.SAME.wire
                                                    }
                                                    greetingKind.value = when {
                                                        AnswerGreetings.present(app, p.id) -> PluginContract.GREETING_RECORD
                                                        p.audioPath.isNotBlank() -> PluginContract.GREETING_FILE
                                                        p.text.isNotBlank() -> PluginContract.GREETING_TEXT
                                                        else -> greetingKind.value
                                                    }
                                                    delay.value = p.delay.toString()
                                                }
                                            } else if (greetingKind.value == PluginContract.GREETING_RECORD) {
                                                // Custom has no recording of its own.
                                                greetingKind.value = PluginContract.GREETING_TEXT
                                            }
                                        },
                                    )
                                    Text(
                                        "Picking a profile fills the fields below. Change any of them and this " +
                                            "goes back to Custom, leaving the saved profile alone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        SettingsGroup("With Do Not Disturb on") {
                            for ((value, label) in PluginContract.DND_OPTIONS) {
                                row {
                                    RadioRow(
                                        title = label,
                                        selected = dnd.value == value,
                                    ) { haptics.tap(); dnd.value = value; custom() }
                                }
                            }
                        }

                        SettingsGroup("Call duration") {
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        "In seconds. Leave empty for App's Setting, or enter 0 for no limit. " +
                                            "A Tasker variable such as %LONGEST works too.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val preset = when (longest.value) {
                                        "0" -> "No limit"
                                        "30" -> "30 seconds"
                                        "60" -> "1 minute"
                                        "120" -> "2 minutes"
                                        else -> ""
                                    }
                                    Capsules(
                                        labels = listOf("No limit", "30 seconds", "1 minute", "2 minutes", "Seconds"),
                                        selected = if (longestMode.value) "Seconds" else preset,
                                    ) { label ->
                                        custom()
                                        when (label) {
                                            "No limit" -> {
                                                longest.value = "0"
                                                longestInput.value = "0"
                                                longestMode.value = false
                                            }
                                            "Seconds" -> longestMode.value = true
                                            else -> {
                                                val value = when (label) {
                                                    "30 seconds" -> "30"
                                                    "1 minute" -> "60"
                                                    else -> "120"
                                                }
                                                longest.value = value
                                                longestInput.value = value
                                                longestMode.value = false
                                            }
                                        }
                                    }
                                    if (longestMode.value) {
                                        CompactField(
                                            value = longestInput.value,
                                            onValueChange = { typed ->
                                                val kept = typed.filter { c ->
                                                    c.isDigit() || c == '%' || c.isLetter() || c == '_'
                                                }.take(24)
                                                longestInput.value = kept
                                                longest.value = if (kept.isBlank()) PluginContract.DEFAULT else kept.trim()
                                                custom()
                                            },
                                            label = "Seconds or variable",
                                            supporting = "A Tasker variable such as %LONGEST works too.",
                                        )
                                    }
                                }
                            }
                        }

                        TaskerGreetingEditor(
                            text = text.value,
                            onText = { text.value = it; custom() },
                            audioPath = audioPath.value,
                            onAudioPath = { audioPath.value = it; custom() },
                            selectedKind = greetingKind.value,
                            onSelectKind = { greetingKind.value = it },
                            recordAvailable = recorded,
                            recorded = recorded,
                            recordSeconds = recordSeconds,
                            onPlay = {
                                when (greetingKind.value) {
                                    PluginContract.GREETING_RECORD ->
                                        AnswerGreetings.togglePreview(app, recordingId)
                                    PluginContract.GREETING_FILE -> {
                                        previewing.value = true
                                        scope.launch {
                                            val pcm = GreetingMaker.fromAudio(app, audioPath.value)
                                            previewing.value = false
                                            if (pcm != null) {
                                                play(pcm)
                                            } else {
                                                problem.value = "That file could not be played. Try an MP3, M4A or WAV."
                                            }
                                        }
                                    }
                                    else -> {
                                        previewing.value = true
                                        scope.launch {
                                            val pcm = GreetingMaker.make(app, text.value, voiceId.value)
                                            previewing.value = false
                                            if (pcm != null) play(pcm) else problem.value = "Nothing was spoken. Try different words."
                                        }
                                    }
                                }
                            },
                            voiceSummary = voices.value.firstOrNull { it.id == voiceId.value }?.label
                                ?: voices.value.firstOrNull()?.label
                                ?: "The phone's default voice",
                            onPickVoice = { voiceDialog.value = true; custom() },
                            busy = previewing.value,
                            problem = problem.value,
                            onPickFile = { audioPermission.launch(AudioAccess.PERMISSIONS) },
                            allowEmpty = false,
                        )

                    }
                }

                if (customWarning.value) {
                    AlertDialog(
                        onDismissRequest = { customWarning.value = false },
                        title = { Text("Profile has a recorded greeting") },
                        text = {
                            Text(
                                "Changing a field here switches this action to Custom. A recorded greeting " +
                                    "cannot be made in a Tasker action, so the greeting type goes back to Text. " +
                                    "Select the profile again to use its recorded greeting.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { customWarning.value = false }) { Text("OK") }
                        },
                    )
                }

                if (voiceDialog.value) {
                    VoicePickerDialog(
                        voices = voices.value,
                        selectedId = voiceId.value,
                        onChoose = { id -> voiceId.value = id },
                        onDismiss = { voiceDialog.value = false },
                        default = PluginContract.DEFAULT to "App's Setting",
                    )
                }
            }
        }
    }

    /** Speak a preview on the loudspeaker, as the app's greeting preview does. */
    private suspend fun play(pcm: ByteArray) {
        withContext(Dispatchers.IO) {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Greeting.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size.coerceAtLeast(AudioTrack.getMinBufferSize(
                    Greeting.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((pcm.size / 2.0 / Greeting.RATE * 1000).toLong() + 200)
            track.stop()
            track.release()
        }
    }
}
