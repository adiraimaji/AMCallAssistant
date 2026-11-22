package com.adiraimaji.amcallassistant.tasker

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.answering.GreetingSpeaker
import com.adiraimaji.amcallassistant.answering.TextToSpeechGreeting
import com.adiraimaji.amcallassistant.answering.VoiceOption
import com.adiraimaji.amcallassistant.ui.theme.AmCallAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edit screen for the "AM Answering Machine" plugin action. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class AnswerEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE)
            ?.let { PluginContract.Config.parse(it.getString(PluginContract.FIELD_CONFIG)) }
        val initial = existing?.takeIf { it.kind == PluginContract.KIND_ANSWER }
            ?: PluginContract.Config(kind = PluginContract.KIND_ANSWER)

        setContent {
            AmCallAssistantTheme {
                val scope = rememberCoroutineScope()
                var dnd = rememberSaveable { mutableStateOf(initial.dnd) }
                var longest = rememberSaveable { mutableStateOf(initial.longest) }
                var text = rememberSaveable { mutableStateOf(initial.greetingText) }
                var voiceId = rememberSaveable { mutableStateOf(initial.voiceId) }
                var voices = rememberSaveable { mutableStateOf<List<VoiceOption>>(emptyList()) }
                var voiceDialog = rememberSaveable { mutableStateOf(false) }
                var dropdown = rememberSaveable { mutableStateOf(false) }
                var longestDialog = rememberSaveable { mutableStateOf(false) }
                var longestInput = rememberSaveable { mutableStateOf("") }
                var problem = rememberSaveable { mutableStateOf<String?>(null) }
                var previewing = rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val speaker: GreetingSpeaker = TextToSpeechGreeting(applicationContext)
                    val found = runCatching { speaker.voices() }.getOrDefault(emptyList())
                    voices.value = found
                    if (found.isEmpty()) problem.value = "This phone has no text-to-speech voice"
                    speaker.close()
                }

                Scaffold(topBar = { TopAppBar(title = { Text("Answering machine") }) }) { pad ->
                    Column(
                        Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            "Answer the call that is ringing right now, with these settings, just this once. " +
                                "Nothing in the app's answering machine is changed. " +
                                "App's Setting uses whatever you chose in the app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        SettingRow("With Do Not Disturb on", labelFor(PluginContract.DND_OPTIONS, dnd.value)) { dropdown.value = true }
                        SettingRow("Longest message", PluginContract.longestLabel(longest.value)) {
                            longestInput.value = longest.value.takeIf { it != PluginContract.DEFAULT } ?: ""
                            longestDialog.value = true
                        }

                        Text("Greeting", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                        OutlinedTextField(
                            value = text.value,
                            onValueChange = { text.value = it.take(300) },
                            label = { Text("What callers hear (%BATT or any Tasker variable)") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SettingRow(
                            "Voice",
                            voices.value.firstOrNull { it.id == voiceId.value }?.label
                                ?: voices.value.firstOrNull()?.label
                                ?: "The phone's default voice",
                        ) { voiceDialog.value = true }

                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                onClick = {
                                    previewing.value = true
                                    scope.launch {
                                        val pcm = GreetingMaker.make(
                                            applicationContext,
                                            text.value,
                                            voiceId.value,
                                        )
                                        previewing.value = false
                                        if (pcm != null) play(pcm) else problem.value = "Nothing was spoken. Try different words."
                                    }
                                },
                                enabled = text.value.isNotBlank() && !previewing.value,
                            ) { Text(if (previewing.value) "Speaking..." else "Play") }
                        }

                        problem.value?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }

                        Button(
                            onClick = {
                                EditResult.send(
                                    this@AnswerEditActivity,
                                    initial.copy(
                                        answerOn = true, revert = false,
                                        dnd = dnd.value, longest = longest.value,
                                        greetingText = text.value, voiceId = voiceId.value,
                                    ),
                                )
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("OK") }
                    }
                }

                if (dropdown.value) {
                    AlertDialog(
                        onDismissRequest = { dropdown.value = false },
                        title = { Text("Do Not Disturb") },
                        text = {
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                PluginContract.DND_OPTIONS.forEach { (value, label) ->
                                    Text(
                                        label,
                                        Modifier.fillMaxWidth().clickable {
                                            dnd.value = value
                                            dropdown.value = false
                                        }.padding(12.dp),
                                    )
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { dropdown.value = false }) { Text("Cancel") } },
                    )
                }

                if (longestDialog.value) {
                    AlertDialog(
                        onDismissRequest = { longestDialog.value = false },
                        title = { Text("Longest message") },
                        text = {
                            Column {
                                Text(
                                    "In seconds. Leave empty for App's Setting, or enter 0 for no limit. " +
                                        "A Tasker variable such as %LONGEST works too.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = longestInput.value,
                                    onValueChange = { typed ->
                                        longestInput.value = typed.filter { c ->
                                            c.isDigit() || c == '%' || c.isLetter() || c == '_'
                                        }.take(24)
                                    },
                                    label = { Text("Seconds or variable") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val typed = longestInput.value.trim()
                                longest.value = if (typed.isEmpty()) PluginContract.DEFAULT else typed
                                longestDialog.value = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { longestDialog.value = false }) { Text("Cancel") }
                        },
                    )
                }

                if (voiceDialog.value) {
                    AlertDialog(
                        onDismissRequest = { voiceDialog.value = false },
                        title = { Text("Voice") },
                        text = {
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                (listOf(PluginContract.DEFAULT to "App's Setting") +
                                    voices.value.map { it.id to it.label }).forEach { (id, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                        voiceId.value = id
                                        voiceDialog.value = false
                                    }) {
                                        RadioButton(selected = voiceId.value == id, onClick = {
                                            voiceId.value = id
                                            voiceDialog.value = false
                                        })
                                        Text(label, Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { voiceDialog.value = false }) { Text("Cancel") } },
                    )
                }
            }
        }
    }

    private fun labelFor(options: List<Pair<String, String>>, value: String): String =
        options.firstOrNull { it.first == value }?.second ?: value

    @Composable
    private fun SettingRow(title: String, summary: String, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
