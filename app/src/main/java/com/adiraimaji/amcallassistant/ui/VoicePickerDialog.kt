package com.adiraimaji.amcallassistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.answering.VoiceOption

/**
 * The voice list, in the answering machine and in the Tasker action editor.
 * Phones can have dozens of voices, so the list is searchable by the name the
 * phone gives each one. The field stays put while the matches scroll.
 *
 * [default] is the entry a Tasker action offers above the phone's voices,
 * meaning "whatever the app has chosen"; the app's own picker has none.
 */
/** The entries to draw for a typed query: the optional default first, then
 *  the phone's voices whose label contains it, case-insensitively. */
internal fun matchingVoices(
    voices: List<VoiceOption>,
    query: String,
    default: Pair<String, String>?,
): List<VoiceOption> {
    val all = listOfNotNull(default?.let { VoiceOption(it.first, it.second) }) + voices
    val trimmed = query.trim()
    return if (trimmed.isEmpty()) all else all.filter { it.label.contains(trimmed, ignoreCase = true) }
}

@Composable
internal fun VoicePickerDialog(
    voices: List<VoiceOption>,
    selectedId: String?,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    intro: String? = null,
    default: Pair<String, String>? = null,
) {
    val (query, setQuery) = rememberSaveable { mutableStateOf("") }
    val shown = matchingVoices(voices, query, default)
    val trimmed = query.trim()
    // Choosing a voice closes the picker: the choice is made then, not only
    // after a separate Cancel tap. Both callers rely on onDismiss to go back.
    fun choose(id: String) {
        onChoose(id)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = setQuery,
                    label = { Text("Search voices") },
                    placeholder = { Text("Type a voice name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (shown.isEmpty()) {
                        Text(
                            "No voices match \"$trimmed\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        shown.forEach { voice ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { choose(voice.id) },
                            ) {
                                RadioButton(
                                    selected = voice.id == selectedId,
                                    onClick = { choose(voice.id) },
                                )
                                Text(voice.label, Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
