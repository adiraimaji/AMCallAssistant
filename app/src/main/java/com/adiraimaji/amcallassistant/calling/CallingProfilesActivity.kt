package com.adiraimaji.amcallassistant.calling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.ui.theme.AmCallAssistantTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The saved Calling Machine profiles, each with its own edit and delete. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CallingProfilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmCallAssistantTheme {
                val context = LocalContext.current.applicationContext
                var profiles by remember { mutableStateOf(CallingProfiles.all(context)) }
                Scaffold(topBar = { TopAppBar(title = { Text("Calling machine profiles") }) }) { pad ->
                    if (profiles.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
                            Text("No profiles yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add one in Calling Machine. Saved profiles appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                            items(profiles, key = { it.id }) { profile ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            profile.name.ifBlank { profile.contactName.ifBlank { profile.number } },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text("To: ${profile.number}", style = MaterialTheme.typography.bodyMedium)
                                        Text("When: ${whenLabel(profile)}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Duration: ${durationLabel(profile.duration)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "Greeting: " + (
                                                CallingGreetings.seconds(context, profile.id)
                                                    ?.let { "${it.toInt()} s" } ?: "none"
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                CallingTarget.edit(profile.id)
                                                finish()
                                            }) { Text("Edit") }
                                            TextButton(onClick = {
                                                CallingScheduler.cancel(context, profile)
                                                CallingGreetings.delete(context, profile.id)
                                                CallingProfiles.remove(context, profile.id)
                                                profiles = CallingProfiles.all(context)
                                            }) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun whenLabel(p: CallingProfile): String = when (p.whenMode) {
    "minutes" -> "After ${p.delayAmount} minutes"
    "seconds" -> "After ${p.delayAmount} seconds"
    else -> SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(p.atMillis))
}

private fun durationLabel(value: String): String = when (value) {
    "message" -> "After greeting"
    "none" -> "No limit"
    else -> "$value seconds"
}
