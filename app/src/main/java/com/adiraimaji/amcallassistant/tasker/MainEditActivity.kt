package com.adiraimaji.amcallassistant.tasker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.ui.theme.AmCallAssistantTheme

/** Edit screen for the "Toggle AM Call Assistant" plugin action. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE)
            ?.let { PluginContract.Config.parse(it.getString(PluginContract.FIELD_CONFIG)) }
        val initial = existing?.takeIf { it.kind == PluginContract.KIND_MAIN }
            ?: PluginContract.Config(kind = PluginContract.KIND_MAIN)

        setContent {
            AmCallAssistantTheme {
                var on = rememberSaveable { mutableStateOf(initial.mainOn) }
                var revert = rememberSaveable { mutableStateOf(initial.revert) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Main service") }) },
                ) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                        Text("Turn AM Call Assistant's recording on or off when this action runs.", style = MaterialTheme.typography.bodyMedium)
                        RowChoice("On", on.value) { on.value = true }
                        RowChoice("Off", !on.value) { on.value = false }
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Switch(checked = revert.value, onCheckedChange = { revert.value = it })
                            Text("Revert to the original setting", Modifier.padding(start = 8.dp))
                        }
                        Text(
                            "With Revert, this action puts back the value that was set before the first plugin action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = {
                                EditResult.send(
                                    this@MainEditActivity,
                                    initial.copy(mainOn = on.value, revert = revert.value),
                                )
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("OK") }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RowChoice(label: String, selected: Boolean, onClick: () -> Unit) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, Modifier.padding(start = 4.dp))
        }
    }
}
