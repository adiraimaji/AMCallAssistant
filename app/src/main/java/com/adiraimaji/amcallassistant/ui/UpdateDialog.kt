package com.adiraimaji.amcallassistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adiraimaji.amcallassistant.update.Updater

/**
 * The one dialog about an update: the release notes as GitHub wrote them, then
 * Install or Cancel. Scrollable, because a long change list is common. Install
 * downloads first and shows its progress, so the tap means "get and install".
 */
@Composable
internal fun UpdateDialog(
    release: Updater.Release,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Version ${release.version} is available") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading $progress%")
                }
                if (error != null) Text(error)
                Text(release.notes.ifBlank { "No release notes were given." })
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !downloading) {
                Text(if (downloading) "Downloading" else "Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !downloading) { Text("Cancel") }
        },
    )
}
