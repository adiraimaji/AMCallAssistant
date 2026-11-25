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
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.answering.GreetingSpeaker
import com.adiraimaji.amcallassistant.answering.TextToSpeechGreeting
import com.adiraimaji.amcallassistant.answering.VoiceOption
import com.adiraimaji.amcallassistant.audio.FilePath
import com.adiraimaji.amcallassistant.capture.AudioAccess
import com.adiraimaji.amcallassistant.calling.CallingGreetings
import com.adiraimaji.amcallassistant.calling.CallingProfiles
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

/** Edit screen for the "AM Calling Machine" plugin action. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CallingEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE)
            ?.let { PluginContract.Config.parse(it.getString(PluginContract.FIELD_CONFIG)) }
        val initial = existing?.takeIf { it.kind == PluginContract.KIND_CALL }
            ?: PluginContract.Config(kind = PluginContract.KIND_CALL)
        // There is no App's Setting capsule any more: an old action that used
        // the sentinel opens with the ordinary minute value instead.
        val startDuration = if (initial.callDuration == PluginContract.DEFAULT) "60" else initial.callDuration

        setContent {
            AmCallAssistantTheme {
                val scope = rememberCoroutineScope()
                val haptics = LocalHapticFeedback.current
                val app = applicationContext
                val callingProfiles = remember { CallingProfiles.all(app) }
                var selectedProfile = rememberSaveable {
                    mutableStateOf(initial.callProfileId.ifBlank { PluginContract.PROFILE_CUSTOM })
                }
                var profileError = rememberSaveable { mutableStateOf<String?>(null) }
                var customWarning = rememberSaveable { mutableStateOf(false) }
                var number = rememberSaveable { mutableStateOf(initial.callNumber) }
                var duration = rememberSaveable { mutableStateOf(startDuration) }
                var busy = rememberSaveable { mutableStateOf(initial.callBusy) }
                var delayInput = rememberSaveable { mutableStateOf(initial.callDelayMs.toString()) }
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
                fun custom() {
                    val chosen = selectedProfile.value
                    if (chosen != PluginContract.PROFILE_CUSTOM && CallingGreetings.present(app, chosen)) {
                        // Warn before the dropdown stops being that profile:
                        // its recording cannot be used from Custom.
                        customWarning.value = true
                    }
                    selectedProfile.value = PluginContract.PROFILE_CUSTOM
                    profileError.value = null
                    if (greetingKind.value == PluginContract.GREETING_RECORD) {
                        greetingKind.value = PluginContract.GREETING_TEXT
                    }
                }
                var voices = remember { mutableStateOf<List<VoiceOption>>(emptyList()) }
                var problem = rememberSaveable { mutableStateOf<String?>(null) }
                var previewing = rememberSaveable { mutableStateOf(false) }
                var voiceDialog = rememberSaveable { mutableStateOf(false) }
                // A typed duration keeps its field even if the number typed is
                // one of the presets.
                var durationMode = rememberSaveable {
                    mutableStateOf(duration.value !in listOf("message", "none", "60", "300"))
                }
                var durationUnit = rememberSaveable {
                    mutableStateOf(
                        if (durationMode.value && duration.value.toIntOrNull()?.let { it >= 60 && it % 60 == 0 } == true) {
                            "minutes"
                        } else {
                            "seconds"
                        },
                    )
                }
                var durationInput = rememberSaveable {
                    mutableStateOf(
                        if (!durationMode.value) {
                            "60"
                        } else if (durationUnit.value == "minutes") {
                            (duration.value.toIntOrNull() ?: 60).div(60).toString()
                        } else {
                            duration.value
                        },
                    )
                }
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
                val recorded = recordingId.isNotBlank() && CallingGreetings.present(app, recordingId)
                val recordSeconds = if (recorded) CallingGreetings.seconds(app, recordingId) else null

                LaunchedEffect(Unit) {
                    val speaker: GreetingSpeaker = TextToSpeechGreeting(applicationContext)
                    val found = runCatching { speaker.voices() }.getOrDefault(emptyList())
                    voices.value = found
                    if (found.isEmpty()) problem.value = "This phone has no text-to-speech voice"
                    speaker.close()
                }

                val context = LocalContext.current
                val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        audioPath.value = FilePath.fromUri(context, uri) ?: uri.toString()
                        custom()
                    }
                }
                val audioPermission = rememberPermissionRequest { _ -> audioPicker.launch("audio/*") }

                val canSave = number.value.isNotBlank() && profileError.value == null
                fun saveConfig() {
                    EditResult.send(
                        this@CallingEditActivity,
                        initial.copy(
                            answerOn = true, revert = false,
                            callNumber = number.value,
                            callDuration = duration.value,
                            callDelayMs = delayInput.value.toIntOrNull()?.takeIf { it >= 0 } ?: 1000,
                            callBusy = busy.value,
                            greetingText = text.value,
                            voiceId = voiceId.value,
                            audioPath = audioPath.value,
                            greetingKind = greetingKind.value,
                            callProfileId = selectedProfile.value,
                            customGreetingId = customGreetingId.value,
                        ),
                    )
                }
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text("AM Calling Machine") },
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
                            "Place a call when this action runs, with these settings, just this once. " +
                                "Nothing in the app's calling settings is changed. A Tasker variable " +
                                "such as %NUMBER works in the number and the audio path.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        SettingsGroup("The call") {
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    ProfileDropdown(
                                        label = "Profile",
                                        profiles = callingProfiles.map { it.id to it.name.ifBlank { it.number } },
                                        selectedId = selectedProfile.value,
                                        onSelect = { id ->
                                            selectedProfile.value = id
                                            profileError.value = null
                                            if (id != PluginContract.PROFILE_CUSTOM) {
                                                CallingProfiles.get(app, id)?.let { p ->
                                                    if (p.whenMode == "date") {
                                                        profileError.value =
                                                            "A profile with a picked date and time cannot be used in a Tasker action. Choose Custom."
                                                    }
                                                    number.value = p.number
                                                    duration.value = p.duration
                                                    durationMode.value = p.duration.toIntOrNull() != null
                                                    durationUnit.value = if (durationMode.value && p.duration.toIntOrNull()!! >= 60 && p.duration.toInt() % 60 == 0) {
                                                        "minutes"
                                                    } else {
                                                        "seconds"
                                                    }
                                                    durationInput.value = if (durationMode.value) {
                                                        if (durationUnit.value == "minutes") {
                                                            (p.duration.toInt() / 60).toString()
                                                        } else {
                                                            p.duration
                                                        }
                                                    } else {
                                                        "60"
                                                    }
                                                    busy.value = p.busy
                                                    delayInput.value = p.greetingDelayMs.toString()
                                                    text.value = p.text
                                                    audioPath.value = p.audioPath
                                                    voiceId.value = p.voiceId
                                                    greetingKind.value = when {
                                                        CallingGreetings.present(app, p.id) -> PluginContract.GREETING_RECORD
                                                        p.audioPath.isNotBlank() -> PluginContract.GREETING_FILE
                                                        p.text.isNotBlank() -> PluginContract.GREETING_TEXT
                                                        else -> greetingKind.value
                                                    }
                                                }
                                            } else if (greetingKind.value == PluginContract.GREETING_RECORD) {
                                                // Custom has no recording of its own.
                                                greetingKind.value = PluginContract.GREETING_TEXT
                                            }
                                        },
                                    )
                                    Text(
                                        "Picking a profile fills the fields below. Change any of them and this goes back to Custom.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    CompactField(
                                        value = number.value,
                                        onValueChange = { number.value = it.take(32); custom() },
                                        label = "Number",
                                        placeholder = "Phone number or %variable",
                                        supporting = "A Tasker variable such as %NUMBER works here.",
                                    )
                                }
                            }
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text("Call duration", style = MaterialTheme.typography.bodyLarge)
                                    val preset = when (duration.value) {
                                        "message" -> "When greeting ends"
                                        "none" -> "No limit"
                                        else -> ""
                                    }
                                    Capsules(
                                        labels = listOf("When greeting ends", "No limit", "Minutes", "Seconds"),
                                        selected = if (durationMode.value) {
                                            if (durationUnit.value == "minutes") "Minutes" else "Seconds"
                                        } else {
                                            preset
                                        },
                                    ) { label ->
                                        custom()
                                        when (label) {
                                            "When greeting ends" -> {
                                                duration.value = "message"
                                                durationMode.value = false
                                            }
                                            "No limit" -> {
                                                duration.value = "none"
                                                durationMode.value = false
                                            }
                                            "Minutes" -> {
                                                durationMode.value = true
                                                durationUnit.value = "minutes"
                                                durationInput.value = duration.value.toIntOrNull()
                                                    ?.div(60)?.toString()?.takeIf { it != "0" } ?: "1"
                                                duration.value = "60"
                                            }
                                            "Seconds" -> {
                                                durationMode.value = true
                                                durationUnit.value = "seconds"
                                                durationInput.value = duration.value.takeIf { it.toIntOrNull() != null } ?: "60"
                                            }
                                        }
                                    }
                                    if (durationMode.value) {
                                        CompactField(
                                            value = durationInput.value,
                                            onValueChange = { typed ->
                                                val digits = typed.filter(Char::isDigit).take(5)
                                                durationInput.value = digits
                                                if (digits.isNotBlank()) {
                                                    val n = digits.toIntOrNull() ?: 0
                                                    duration.value = if (durationUnit.value == "minutes") {
                                                        (n * 60).toString()
                                                    } else {
                                                        digits
                                                    }
                                                }
                                                custom()
                                            },
                                            label = if (durationUnit.value == "minutes") "Minutes" else "Seconds",
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        )
                                    }
                                }
                            }
                            row {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    CompactField(
                                        value = delayInput.value,
                                        onValueChange = { typed ->
                                            delayInput.value = typed.filter(Char::isDigit).take(6)
                                            custom()
                                        },
                                        label = "Greeting delay (milliseconds)",
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        supporting = "After the call starts; 0 plays immediately.",
                                    )
                                }
                            }
                        }

                        SettingsGroup("If a call is already up") {
                            for ((value, label) in listOf(
                                "wait" to "Call when the line is free",
                                "discard" to "Discard this action",
                                "now" to "Call anyway",
                            )) {
                                row {
                                    RadioRow(
                                        title = label,
                                        selected = busy.value == value,
                                    ) { haptics.tap(); busy.value = value; custom() }
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
                                        CallingGreetings.togglePreview(app, recordingId)
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
                            allowEmpty = true,
                        )

                        profileError.value?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

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
                        onChoose = { voiceId.value = it },
                        onDismiss = { voiceDialog.value = false },
                        default = PluginContract.DEFAULT to "App's Setting",
                    )
                }
            }
        }
    }

    /** Speak a preview on the loudspeaker, as the answering action does. */
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
        }
    }
}
