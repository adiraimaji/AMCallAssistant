package com.adiraimaji.amcallassistant.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.calls.CallLogLookup

/** One phone row, not one contact: a contact with two numbers appears twice. */
data class ContactChoice(
    val name: String,
    val number: String,
    /** Lookup URI as a string; the Whose calls screen parses it when chosen. */
    val uri: String,
)

internal fun matchingContacts(choices: List<ContactChoice>, query: String): List<ContactChoice> {
    val q = query.trim()
    if (q.isEmpty()) return choices
    return choices.filter { it.name.contains(q, true) || it.number.contains(q, true) }
}

internal fun loadContacts(context: Context): List<ContactChoice> {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
    )
    return runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC",
        )?.use { rows ->
            val out = mutableListOf<ContactChoice>()
            val numberCol = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameCol = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val idCol = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupCol = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            while (rows.moveToNext()) {
                val number = if (numberCol >= 0) rows.getString(numberCol).orEmpty() else ""
                if (number.isBlank()) continue
                val name = if (nameCol >= 0) rows.getString(nameCol).orEmpty() else ""
                val id = if (idCol >= 0) rows.getString(idCol).orEmpty() else ""
                val lookup = if (lookupCol >= 0) rows.getString(lookupCol).orEmpty() else ""
                val uri = if (lookup.isNotBlank()) {
                    ContactsContract.Contacts.getLookupUri(id.toLongOrNull() ?: 0L, lookup).toString()
                } else {
                    ContactsContract.Contacts.CONTENT_URI.toString()
                }
                out += ContactChoice(name.ifBlank { "Unnamed contact" }, number, uri)
            }
            out
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

/** The list itself: one row per number, and nothing to search with of its own. */
@Composable
private fun ContactResults(
    shown: List<ContactChoice>,
    query: String,
    onChoose: (ContactChoice) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        if (shown.isEmpty()) {
            Text(
                if (query.isBlank()) "No contacts found" else "No contacts match \"${query.trim()}\"",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        for (choice in shown) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChoose(choice) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(choice.name, style = MaterialTheme.typography.bodyLarge)
                    Text(choice.number, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The contact list, usable in a dropdown or inline.
 *
 * With [query] the caller's own field is the search box: all contacts are shown
 * until it has text, and then only the ones whose name or number matches.
 * Without it - Whose calls, adding several contacts - the panel keeps one field
 * of its own because there is no other field to type into.
 */
@Composable
internal fun ContactListPanel(
    onChoose: (ContactChoice) -> Unit,
    query: String? = null,
) {
    val context = LocalContext.current
    var ownQuery by rememberSaveable { mutableStateOf("") }
    var permissionTick by remember { mutableIntStateOf(0) }
    val permission = rememberPermissionRequest { result ->
        if (result[Manifest.permission.READ_CONTACTS] == true) permissionTick++
    }
    val granted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val choices = remember(permissionTick) { if (granted) loadContacts(context) else emptyList() }
    val filter = query ?: ownQuery
    val shown = matchingContacts(choices, filter)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (!granted) {
            Text(
                "Allow AM Call Assistant to read contacts so it can show their numbers.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            TextButton(onClick = { permission.launch(CallLogLookup.PERMISSIONS) }) {
                Text("Allow contacts")
            }
        } else {
            if (query == null) {
                CompactField(
                    value = ownQuery,
                    onValueChange = { ownQuery = it },
                    label = "Search contacts",
                    placeholder = "Name or number",
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            ContactResults(shown, filter, onChoose)
        }
    }
}

/** A number field whose contacts open as a dropdown attached to that field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    onChoose: (ContactChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        CompactField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            supporting = supporting,
            isError = isError,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            onFocusChanged = { focused -> if (focused) expanded = true },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // No fillMaxWidth here: the menu's own width matching takes the
            // field's width. A fill would force min=max=screen width instead.
        ) {
            ContactListPanel(
                query = value,
                onChoose = { chosen ->
                    onChoose(chosen)
                    expanded = false
                },
            )
        }
    }
}
