package com.adiraimaji.amcallassistant.tasker

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/**
 * The profile picker in a plugin action. [profiles] are the saved ones, by id
 * and name; Custom is always first and means "use the fields typed below".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileDropdown(
    label: String,
    profiles: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = profiles.firstOrNull { it.first == selectedId }?.second ?: "Custom"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((id, name) in listOf(PluginContract.PROFILE_CUSTOM to "Custom") + profiles) {
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}
