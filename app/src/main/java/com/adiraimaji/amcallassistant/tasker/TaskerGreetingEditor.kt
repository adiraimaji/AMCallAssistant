package com.adiraimaji.amcallassistant.tasker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.adiraimaji.amcallassistant.ui.Capsules
import com.adiraimaji.amcallassistant.ui.CompactField
import com.adiraimaji.amcallassistant.ui.InlineError
import com.adiraimaji.amcallassistant.ui.SettingsGroup
import com.adiraimaji.amcallassistant.ui.SettingsRow

/**
 * The greeting part of both plugin edit screens. The capsules choose the
 * greeting type; whatever is chosen is the greeting the action uses, so there
 * is no "use as greeting" step. One Play greeting button at the top plays that
 * chosen source, wherever it came from.
 */
@Composable
internal fun TaskerGreetingEditor(
    text: String,
    onText: (String) -> Unit,
    audioPath: String,
    onAudioPath: (String) -> Unit,
    selectedKind: String,
    onSelectKind: (String) -> Unit,
    /** A selected profile has a recorded greeting. Custom never has one. */
    recordAvailable: Boolean,
    recorded: Boolean,
    recordSeconds: Double?,
    onPlay: () -> Unit,
    voiceSummary: String,
    onPickVoice: () -> Unit,
    busy: Boolean,
    problem: String?,
    onPickFile: () -> Unit,
    allowEmpty: Boolean,
) {
    val fieldRequester = remember { BringIntoViewRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(fieldFocused, keyboardHeight > 0) {
        if (fieldFocused && keyboardHeight > 0) {
            delay(100)
            fieldRequester.bringIntoView()
        }
    }
    var showText = rememberSaveable { mutableStateOf(selectedKind == PluginContract.GREETING_TEXT) }
    var showFile = rememberSaveable { mutableStateOf(selectedKind == PluginContract.GREETING_FILE) }
    LaunchedEffect(selectedKind) {
        showText.value = selectedKind == PluginContract.GREETING_TEXT
        showFile.value = selectedKind == PluginContract.GREETING_FILE
    }

    SettingsGroup("Greeting") {
        row {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val textSet = text.isNotBlank()
                val fileSet = audioPath.isNotBlank()
                val playEnabled = !busy && when (selectedKind) {
                    PluginContract.GREETING_TEXT -> textSet
                    PluginContract.GREETING_FILE -> fileSet
                    PluginContract.GREETING_RECORD -> recorded
                    else -> false
                }
                TextButton(onClick = onPlay, enabled = playEnabled) {
                    Text("Play greeting", maxLines = 1)
                }
            }
        }
        row {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (allowEmpty) {
                        "Write the words the phone speaks, or choose an audio file it plays. " +
                            "The selected type below is the one this action uses. Leave it empty to " +
                            "use the greeting recorded in the app. A recorded greeting can only come " +
                            "from a profile, not be made here."
                    } else {
                        "Write the words the phone speaks, or choose an audio file it plays. " +
                            "The selected type below is the one this action uses. A recorded greeting " +
                            "can only come from a profile, not be made here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val textSet = text.isNotBlank()
                val fileSet = audioPath.isNotBlank()
                when {
                    selectedKind == PluginContract.GREETING_RECORD && recorded -> Text(
                        "Recorded greeting · ${recordSeconds?.toInt() ?: 0} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    !textSet && !fileSet && !recorded && !allowEmpty -> Text(
                        "Enter a greeting or choose an audio file to save this action.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    !textSet && !fileSet && !recorded -> Text(
                        "The greeting recorded in the app will be used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        when (selectedKind) {
                            PluginContract.GREETING_RECORD -> "The recorded greeting will be used."
                            PluginContract.GREETING_FILE -> "The audio file will be used."
                            else -> "The written greeting will be used."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Capsules(
                    labels = listOf("Text", "File", "Record", "Clear text", "Clear file"),
                    selected = when (selectedKind) {
                        PluginContract.GREETING_RECORD -> "Record"
                        PluginContract.GREETING_FILE -> "File"
                        PluginContract.GREETING_TEXT -> "Text"
                        else -> ""
                    },
                    disabled = buildSet {
                        if (!textSet) add("Clear text")
                        if (!fileSet) add("Clear file")
                        // Present, but unusable until a profile with a
                        // recording is chosen. Nothing can be recorded here.
                        if (!recordAvailable || busy) add("Record")
                    },
                ) { label ->
                    when (label) {
                        "Text" -> {
                            onSelectKind(PluginContract.GREETING_TEXT)
                            showText.value = true
                            showFile.value = false
                        }
                        "File" -> {
                            onSelectKind(PluginContract.GREETING_FILE)
                            showFile.value = true
                            showText.value = false
                        }
                        "Record" -> onSelectKind(PluginContract.GREETING_RECORD)
                        "Clear text" -> { onText(""); showText.value = true }
                        "Clear file" -> { onAudioPath(""); showFile.value = true }
                    }
                }
                if (showText.value) {
                    CompactField(
                        value = text,
                        onValueChange = { onText(it.take(300)) },
                        modifier = Modifier.bringIntoViewRequester(fieldRequester),
                        onFocusChanged = { fieldFocused = it },
                        label = "Written greeting",
                        placeholder = "What callers hear (%BATT or any Tasker variable)",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        supporting = "${text.length}/300 - the selected type is the one this action uses",
                    )
                    SettingsRow(
                        title = "Voice",
                        summary = voiceSummary,
                        icon = Icons.Filled.Edit,
                        onClick = onPickVoice,
                    )
                }
                if (showFile.value) {
                    CompactField(
                        value = audioPath,
                        onValueChange = onAudioPath,
                        modifier = Modifier.bringIntoViewRequester(fieldRequester),
                        onFocusChanged = { fieldFocused = it },
                        label = "Audio file path",
                        placeholder = "/storage/emulated/0/Music/greeting.mp3",
                        supporting = "A path, or a Tasker variable such as %FILE. The selected type is the one this action uses.",
                    )
                    FilledTonalButton(
                        onClick = onPickFile,
                        modifier = Modifier.widthIn(min = 120.dp),
                    ) { Text("Choose a file") }
                }
                InlineError(problem)
            }
        }
    }
}
