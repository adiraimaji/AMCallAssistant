package com.adiraimaji.amcallassistant.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.audio.FilePath
import com.adiraimaji.amcallassistant.calling.CallingGreetings
import com.adiraimaji.amcallassistant.calling.CallingProfile
import com.adiraimaji.amcallassistant.calling.CallingProfiles
import com.adiraimaji.amcallassistant.calling.CallingScheduler
import com.adiraimaji.amcallassistant.calling.CallingTarget
import com.adiraimaji.amcallassistant.capture.AudioAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.adiraimaji.amcallassistant.tasker.GreetingMaker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calling Machine, as two panes of one screen. The saved profiles are what the
 * screen opens on; tapping a card - or the New profile floating button - opens
 * the editor. Back from the editor (the bar or the system's) returns to the
 * list, and Back from the list leaves the screen.
 *
 * This is a presentation change only. The fields, the save and delete logic,
 * the preference keys and the alarm calls are the ones the screen always used;
 * the capsules, the cards and the inline fields just offer the same values in
 * a more compact place. The draft is saved with the composition so an edit in
 * progress survives a turn of the phone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CallingMachineScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext
    val haptics = LocalHapticFeedback.current
    val locale = LocalLocale.current.platformLocale
    val a by vm.answer.state.collectAsState()
    val cg by CallingGreetings.state.collectAsState()
    val editorOpen by vm.callingEditorOpen.collectAsState()

    // Read again after anything that can change a profile (save, delete, the
    // switch) rather than holding a copy that would go stale.
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { CallingProfiles.all(app) }

    var draft by rememberSaveable(stateSaver = CallingProfileSaver) { mutableStateOf(newDraft()) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var errorField by rememberSaveable { mutableStateOf<CallingErrorField?>(null) }
    var listError by rememberSaveable { mutableStateOf<String?>(null) }

    // Duration is stored as a plain number of seconds ("message", "none" or a
    // count), as it always was. Which unit the editor shows, and the number
    // typed into it, are the editor's own - not a new field on the profile.
    var durationMode by rememberSaveable { mutableStateOf(durationModeOf(draft.duration)) }
    var durationAmount by rememberSaveable { mutableStateOf(durationAmountOf(draft.duration)) }

    // The written greeting and its voice, exactly as the answering machine
    // keeps them across a trip to the voice list and back.
    var writtenText by rememberSaveable { mutableStateOf("") }
    var writtenVoice by rememberSaveable { mutableStateOf<String?>(null) }
    var audioPath by rememberSaveable { mutableStateOf("") }
    var showWrite by rememberSaveable { mutableStateOf(false) }
    var showFile by rememberSaveable { mutableStateOf(false) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var greetingKind by rememberSaveable { mutableStateOf("") }
    // The values that were actually accepted as a greeting. Comparing them
    // with the current fields keeps Save gray while typing, without a state
    // write on every key (which made the field lose focus and wiggle).
    var validatedText by rememberSaveable { mutableStateOf<String?>(null) }
    var validatedAudio by rememberSaveable { mutableStateOf<String?>(null) }
    var showVoice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val greetingFieldRequester = remember { BringIntoViewRequester() }
    var greetingFieldFocused by remember { mutableStateOf(false) }
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(greetingFieldFocused, keyboardHeight > 0) {
        if (greetingFieldFocused && keyboardHeight > 0) {
            delay(100)
            greetingFieldRequester.bringIntoView()
        }
    }
    var greetingNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(draft.id) { CallingGreetings.load(context, draft.id) }
    LaunchedEffect(draft.id, draft.text.trim(), draft.voiceId, greetingKind) {
        val text = draft.text.trim()
        if (greetingKind == "text" && text.isNotBlank()) {
            delay(700)
            CallingGreetings.useText(context, draft.id, text, draft.voiceId) { made ->
                if (made) validatedText = text
            }
        }
    }
    LaunchedEffect(draft.id, draft.audioPath, greetingKind) {
        val path = draft.audioPath
        if (greetingKind == "file" && path.isNotBlank()) {
            delay(700)
            CallingGreetings.useAudio(context, draft.id, path) { made ->
                if (made) validatedAudio = path
            }
        }
    }
    val callingSaveBlocked = editorOpen && (cg.busy || when (greetingKind) {
        "text" -> draft.text.isBlank() || validatedText != draft.text.trim()
        "file" -> draft.audioPath.isBlank() || validatedAudio != draft.audioPath
        "record" -> !CallingGreetings.present(app, draft.id)
        else -> false
    })
    LaunchedEffect(callingSaveBlocked) {
        if (callingSaveBlocked) delay(700)
        vm.setCallingSaveEnabled(!callingSaveBlocked)
    }

    // Voices belong to the open Write panel, not to the whole screen.
    LaunchedEffect(showWrite) {
        if (showWrite) vm.answer.loadTtsVoices() else vm.answer.closeTts()
    }

    // The New profile button and a card in the companion list both ask for the
    // editor through the view model, so the app bar can open the same editor
    // the cards do. One-shot: a turn of the phone keeps the draft, not a stale
    // request to load it again.
    LaunchedEffect(Unit) {
        vm.callingEditRequests.collect { request ->
            if (request.id == null) {
                draft = newDraft()
                editing = null
            } else {
                CallingProfiles.get(app, request.id)?.let { profile ->
                    draft = profile
                    editing = profile.id
                }
            }
            durationMode = durationModeOf(draft.duration)
            durationAmount = durationAmountOf(draft.duration)
            error = null
            errorField = null
            greetingNotice = null
            showWrite = false
            showFile = false
            showRecord = false
            greetingKind = kindOf(draft, app)
            showWrite = greetingKind == "text"
            showFile = greetingKind == "file"
            showRecord = greetingKind == "record"
            showVoice = false
        }
    }
    LaunchedEffect(Unit) {
        CallingTarget.request.collect { id -> vm.editCallingProfile(id) }
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val (number, name) = contactDetails(context, uri)
            draft = draft.copy(number = number.ifBlank { draft.number }, contactName = name)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            audioPath = FilePath.fromUri(context, uri) ?: uri.toString()
            draft = draft.copy(audioPath = audioPath)
        }
    }
    val audioPermission = rememberPermissionRequest { _ -> audioPicker.launch("audio/*") }

    fun save() {
        val amount = draft.delayAmount.trim().toLongOrNull()
        when (draft.whenMode) {
            "minutes", "seconds" -> {
                if (amount == null || amount <= 0) {
                    error = "Enter a number greater than zero."
                    errorField = CallingErrorField.WHEN
                    return
                }
                val millis = if (draft.whenMode == "minutes") amount * 60_000L else amount * 1_000L
                draft = draft.copy(atMillis = System.currentTimeMillis() + millis)
            }
            "tasker" -> Unit // only the Tasker action starts this one
            else -> if (draft.atMillis <= System.currentTimeMillis()) {
                error = "Choose a date and time in the future."
                errorField = CallingErrorField.WHEN
                return
            }
        }
        if (draft.number.isBlank()) {
            error = "Choose who to call first."
            errorField = CallingErrorField.NUMBER
            return
        }
        if (durationMode == "minutes" || durationMode == "seconds") {
            val typed = durationAmount.trim().toLongOrNull()
            if (typed == null || typed <= 0 || (durationMode == "minutes" && typed > Int.MAX_VALUE / 60L)) {
                error = "Enter a number greater than zero."
                errorField = CallingErrorField.DURATION
                return
            }
            val seconds = if (durationMode == "minutes") typed * 60L else typed
            draft = draft.copy(duration = seconds.toString())
        }
        // The selected greeting type is the one that counts: clear the other
        // source so it cannot win at fire time.
        draft = when (greetingKind) {
            "text" -> draft.copy(audioPath = "")
            "file" -> draft.copy(text = "")
            else -> draft
        }
        // Saving a profile arms it again: a profile that fired once was
        // disabled, and editing it must not leave it dead.
        val armed = draft.copy(enabled = true)
        draft = armed
        CallingProfiles.save(context, armed)
        if (armed.whenMode != "tasker" && !CallingScheduler.schedule(context, armed)) {
            error = "Allow exact alarms in Android's settings, then save this profile again."
            errorField = CallingErrorField.ALARM
            return
        }
        error = null
        errorField = null
        revision++
        vm.showCallingProfiles()
        draft = newDraft()
        editing = null
        durationMode = "message"
        durationAmount = "60"
        greetingKind = ""
    }

    // The bar's Save reaches this same function, so there is one save path.
    LaunchedEffect(Unit) {
        vm.callingSaveRequests.collect { save() }
    }

    fun toggleEnabled(profile: CallingProfile, on: Boolean) {
        if (!on) {
            CallingScheduler.cancel(app, profile)
            CallingProfiles.save(app, profile.copy(enabled = false))
            revision++
            return
        }
        // A Tasker-trigger profile is never armed by the app: only the plugin
        // action starts it.
        if (profile.whenMode == "tasker") {
            CallingProfiles.save(app, profile.copy(enabled = true))
            revision++
            return
        }
        // Re-arm from what the profile already says: a relative time is read
        // from the same number the editor saves, and a date keeps its time of
        // day on the next day that is still ahead of us.
        val at = when (profile.whenMode) {
            "minutes" -> System.currentTimeMillis() +
                (profile.delayAmount.trim().toLongOrNull() ?: 10L) * 60_000L
            "seconds" -> System.currentTimeMillis() +
                (profile.delayAmount.trim().toLongOrNull() ?: 30L) * 1_000L
            else -> {
                var millis = profile.atMillis
                val now = System.currentTimeMillis()
                while (millis <= now) millis += 24 * 60 * 60 * 1_000L
                millis
            }
        }
        val armed = profile.copy(enabled = true, atMillis = at)
        if (!CallingScheduler.schedule(app, armed)) {
            listError = "Allow exact alarms in Android's settings, then switch this profile on again."
            return
        }
        listError = null
        CallingProfiles.save(app, armed)
        revision++
    }

    fun deleteProfile(profile: CallingProfile) {
        CallingScheduler.cancel(app, profile)
        CallingGreetings.delete(app, profile.id)
        CallingProfiles.remove(app, profile.id)
        revision++
    }

    fun openProfile(id: String) {
        CallingProfiles.get(app, id)?.let { profile ->
            draft = profile
            editing = profile.id
        }
        durationMode = durationModeOf(draft.duration)
        durationAmount = durationAmountOf(draft.duration)
        error = null
        errorField = null
        greetingNotice = null
        showWrite = false
        showFile = false
        showRecord = false
        greetingKind = kindOf(draft, app)
        showWrite = greetingKind == "text"
        showFile = greetingKind == "file"
        showRecord = greetingKind == "record"
        showVoice = false
        listError = null
        vm.editCallingProfile(id)
    }

    val greetingSeconds = cg.seconds?.toInt()?.coerceAtLeast(1)
    val greetingHasSound = CallingGreetings.present(app, draft.id)

    if (!editorOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Profiles you have saved. Tap one to edit it, or use the switch to turn it on or off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineError(listError)
            if (profiles.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No profiles yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use the New profile button to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                for (profile in profiles) {
                    // The whole card is the way into the editor - the switch
                    // keeps its own tap, so turning a profile on or off never
                    // opens it by accident.
                    Card(
                        onClick = { openProfile(profile.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    profile.name.ifBlank { profile.contactName.ifBlank { profile.number } },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text("To: ${profile.number}", style = MaterialTheme.typography.bodyMedium)
                                Text("When: ${profileWhenLabel(profile)}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Duration: ${profileDurationLabel(profile.duration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val greetingType = when {
                                    CallingGreetings.present(app, profile.id) -> "Record"
                                    profile.text.isNotBlank() -> "Text"
                                    profile.audioPath.isNotBlank() -> "File"
                                    else -> "None"
                                }
                                val greetingLength = CallingGreetings.seconds(app, profile.id)
                                    ?.let { " · ${it.toInt()} s" } ?: ""
                                Text(
                                    "Greeting: $greetingType$greetingLength",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = profile.enabled,
                                onCheckedChange = { toggleEnabled(profile, it) },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                // Clear of the floating New profile button over the last card.
                Spacer(Modifier.height(72.dp))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup("Profile") {
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        CompactField(
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it.take(60)) },
                            label = "Profile name",
                            placeholder = "What this profile is for",
                        )
                    }
                }
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        CompactField(
                            value = draft.number,
                            onValueChange = { draft = draft.copy(number = it, contactName = "") },
                            label = "Call to",
                            placeholder = "Contact or phone number",
                            isError = errorField == CallingErrorField.NUMBER && error != null,
                            supporting = draft.contactName.takeIf { it.isNotBlank() }
                                ?.let { "Contact: $it" } ?: "A number, or pick a contact below",
                        )
                        TextButton(onClick = { contactPicker.launch(null) }) {
                            Text(if (draft.contactName.isBlank()) "Pick a contact" else "Pick another contact")
                        }
                        InlineError(error?.takeIf { errorField == CallingErrorField.NUMBER })
                    }
                }
            }

            SettingsGroup("When to call") {
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Capsules(
                            labels = listOf("Minutes", "Seconds", "Pick date and time", "Tasker trigger"),
                            selected = when (draft.whenMode) {
                                "minutes" -> "Minutes"
                                "seconds" -> "Seconds"
                                "tasker" -> "Tasker trigger"
                                else -> "Pick date and time"
                            },
                        ) { label ->
                            val mode = when (label) {
                                "Minutes" -> "minutes"
                                "Seconds" -> "seconds"
                                "Tasker trigger" -> "tasker"
                                else -> "date"
                            }
                            draft = draft.copy(
                                whenMode = mode,
                                delayAmount = when (mode) {
                                    "minutes" -> draft.delayAmount.ifBlank { "10" }
                                    "seconds" -> draft.delayAmount.ifBlank { "30" }
                                    else -> draft.delayAmount
                                },
                                atMillis = draft.atMillis.takeIf { it > System.currentTimeMillis() }
                                    ?: (System.currentTimeMillis() + 10 * 60_000L),
                            )
                            error = null
                            errorField = null
                        }
                        if (draft.whenMode == "minutes" || draft.whenMode == "seconds") {
                            CompactField(
                                value = draft.delayAmount,
                                onValueChange = { draft = draft.copy(delayAmount = it.filter(Char::isDigit)) },
                                label = if (draft.whenMode == "minutes") "Minutes from now" else "Seconds from now",
                                keyboardType = KeyboardType.Number,
                                isError = errorField == CallingErrorField.WHEN && error != null,
                                supportingError = true,
                                supporting = error?.takeIf { errorField == CallingErrorField.WHEN },
                            )
                        }
                        if (draft.whenMode == "tasker") {
                            Text(
                                "The AM Calling Machine Tasker action places this call when it runs. " +
                                    "The app never starts it on its own.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (draft.whenMode != "minutes" && draft.whenMode != "seconds" && draft.whenMode != "tasker") {
                    row {
                        SettingsRow(
                            title = "Date and time",
                            summary = formatWhen(draft.atMillis),
                            icon = SettingsIcons.Timer,
                            onClick = { dateDialog = true },
                        )
                    }
                    row {
                        SettingsRow(
                            title = "Time of day",
                            summary = SimpleDateFormat("HH:mm", locale).format(Date(draft.atMillis)),
                            icon = SettingsIcons.Timer,
                            onClick = { timeDialog = true },
                        )
                    }
                    row {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            InlineError(error?.takeIf { errorField == CallingErrorField.WHEN })
                        }
                    }
                }
            }

            SettingsGroup("Call duration") {
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Capsules(
                            labels = listOf("When greeting ends", "No limit", "Minutes", "Seconds"),
                            selected = when (durationMode) {
                                "message" -> "When greeting ends"
                                "none" -> "No limit"
                                "minutes" -> "Minutes"
                                else -> "Seconds"
                            },
                        ) { label ->
                            val previous = durationMode
                            when (label) {
                                "When greeting ends" -> {
                                    durationMode = "message"
                                    draft = draft.copy(duration = "message")
                                }
                                "No limit" -> {
                                    durationMode = "none"
                                    draft = draft.copy(duration = "none")
                                }
                                "Minutes" -> {
                                    durationMode = "minutes"
                                    if (previous != "minutes") durationAmount = "1"
                                }
                                else -> {
                                    durationMode = "seconds"
                                    if (previous != "seconds") durationAmount = "30"
                                }
                            }
                            error = null
                            errorField = null
                        }
                        if (durationMode == "minutes" || durationMode == "seconds") {
                            CompactField(
                                value = durationAmount,
                                onValueChange = { durationAmount = it.filter(Char::isDigit) },
                                label = if (durationMode == "minutes") "Minutes" else "Seconds",
                                keyboardType = KeyboardType.Number,
                                isError = errorField == CallingErrorField.DURATION && error != null,
                                supportingError = true,
                                supporting = error?.takeIf { errorField == CallingErrorField.DURATION },
                            )
                        }
                    }
                }
            }
            SettingsGroup("If a call is already going on") {
                for (value in listOf(CallingProfile.WAIT, CallingProfile.DISCARD, CallingProfile.NOW)) {
                    row {
                        SettingsRow(
                            title = busyLabel(value),
                            icon = Icons.Filled.Call,
                            modifier = Modifier.selectable(selected = draft.busy == value, role = Role.RadioButton) {
                                haptics.tap()
                                draft = draft.copy(busy = value)
                            },
                            trailing = { RadioButton(selected = draft.busy == value, onClick = null) },
                        )
                    }
                }
            }

            SettingsGroup("Greeting") {
                row {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val canPlay = !cg.busy && when (greetingKind) {
                            "record" -> CallingGreetings.present(app, draft.id)
                            "file" -> draft.audioPath.isNotBlank()
                            "text" -> draft.text.isNotBlank()
                            else -> false
                        }
                        TextButton(onClick = {
                            when (greetingKind) {
                                "record" -> CallingGreetings.togglePreview(context, draft.id)
                                "file" -> scope.launch {
                                    GreetingMaker.fromAudio(context, draft.audioPath)?.let(Greeting::preview)
                                }
                                "text" -> scope.launch {
                                    GreetingMaker.make(context, draft.text, draft.voiceId)?.let(Greeting::preview)
                                }
                                else -> Unit
                            }
                        }, enabled = canPlay) { Text("Play greeting", maxLines = 1) }
                    }
                }
                row {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            when {
                                cg.recording -> "Recording your greeting"
                                cg.busy -> "Preparing your greeting"
                                greetingSeconds != null -> "Your greeting · ${seconds(greetingSeconds)}"
                                else -> "No greeting yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (cg.error != null && !cg.recording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        cg.error?.takeIf { !cg.recording }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Capsules(
                            labels = listOf("Text", "File", "Record", "Clear greeting"),
                            selected = when (greetingKind) {
                                "text" -> "Text"
                                "file" -> "File"
                                "record" -> "Record"
                                else -> ""
                            },
                            enabled = !cg.busy || cg.recording,
                        ) { label ->
                            when (label) {
                                "Text" -> {
                                    greetingKind = "text"
                                    showWrite = true
                                    showFile = false
                                    showRecord = false
                                    greetingNotice = null
                                }
                                "File" -> {
                                    greetingKind = "file"
                                    showFile = true
                                    showWrite = false
                                    showRecord = false
                                    greetingNotice = null
                                }
                                "Record" -> {
                                    greetingKind = "record"
                                    showRecord = true
                                    showWrite = false
                                    showFile = false
                                    greetingNotice = null
                                }
                                "Clear greeting" -> {
                                    CallingGreetings.stopPreview()
                                    CallingGreetings.delete(context, draft.id)
                                    draft = draft.copy(text = "", audioPath = "", voiceId = "default")
                                    writtenText = ""
                                    writtenVoice = null
                                    audioPath = ""
                                    showWrite = false
                                    showFile = false
                                    showRecord = false
                                    greetingKind = ""
                                    greetingNotice = "No greeting yet"
                                }
                            }
                        }
                        if (showRecord) {
                            Text(
                                when {
                                    cg.recording -> "Recording your greeting"
                                    cg.seconds != null || greetingSeconds != null ->
                                        "Recorded greeting · ${(cg.seconds ?: 0.0).toInt()} s"
                                    else -> "No greeting recorded yet"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (cg.recording) {
                                TakeTimer(cg.tookAt)
                                Text(
                                    "Speak now. Tap Stop when you are done (${Greeting.MAX_SECONDS} seconds at most).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val recordLabel = if (cg.seconds != null || greetingSeconds != null) "Record again" else "Record"
                            FilledTonalButton(
                                onClick = {
                                    if (cg.recording) {
                                        CallingGreetings.stopTake(context, draft.id)
                                    } else {
                                        CallingGreetings.startTake(context, draft.id)
                                    }
                                },
                                enabled = !cg.busy || cg.recording,
                                modifier = Modifier.widthIn(min = 104.dp),
                            ) { Text(if (cg.recording) "Stop" else recordLabel, maxLines = 1) }
                        }
                        if (showWrite) {
                            CompactField(
                                value = writtenText,
                                onValueChange = {
                                    writtenText = it.take(300)
                                    draft = draft.copy(text = writtenText)
                                },
                                modifier = Modifier.bringIntoViewRequester(greetingFieldRequester),
                                onFocusChanged = { greetingFieldFocused = it },
                                label = "What callers hear",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 5,
                                supporting = "${writtenText.length}/300",
                            )
                            SettingsRow(
                                title = "Voice",
                                summary = a.ttsVoices.firstOrNull { it.id == writtenVoice }?.label
                                    ?: a.ttsVoices.firstOrNull()?.label
                                    ?: (a.ttsError ?: "Loading voices..."),
                                icon = Icons.Filled.Edit,
                                onClick = { showVoice = true },
                                enabled = a.ttsVoices.isNotEmpty(),
                            )
                            a.ttsError?.takeIf { a.ttsVoices.isNotEmpty() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (showFile) {
                            CompactField(
                                value = audioPath,
                                onValueChange = {
                                    audioPath = it
                                    draft = draft.copy(audioPath = it)
                                },
                                modifier = Modifier.bringIntoViewRequester(greetingFieldRequester),
                                onFocusChanged = { greetingFieldFocused = it },
                                label = "Audio file path",
                                placeholder = "/storage/emulated/0/Music/greeting.mp3",
                            )
                            FilledTonalButton(
                                onClick = { audioPermission.launch(AudioAccess.PERMISSIONS) },
                                enabled = !cg.busy,
                            ) { Text("Choose a file") }
                        }
                        CompactField(
                            value = draft.greetingDelayMs.toString(),
                            onValueChange = { typed ->
                                val ms = typed.filter(Char::isDigit).take(6).toIntOrNull()
                                // Zero is allowed: play the greeting as soon as the call starts.
                                draft = draft.copy(greetingDelayMs = ms ?: 1000)
                            },
                            label = "Greeting delay (milliseconds)",
                            keyboardType = KeyboardType.Number,
                            supporting = "After the call starts; 0 plays immediately.",
                        )
                        greetingNotice?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    editing?.let { id ->
                        CallingProfiles.get(app, id)?.let(::deleteProfile)
                    }
                    vm.showCallingProfiles()
                    draft = newDraft()
                    editing = null
                    error = null
                    errorField = null
                },
                enabled = editing != null,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete profile") }

            InlineError(error?.takeIf { errorField == CallingErrorField.ALARM })
        }
    }

    if (dateDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.atMillis)
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day -> draft = draft.copy(atMillis = withDay(draft.atMillis, day)) }
                    dateDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Cancel") } },
        ) { DatePicker(state = state, title = { Text("Choose a date") }) }
    }
    if (timeDialog) {
        val now = Calendar.getInstance().apply { timeInMillis = draft.atMillis }
        val state = rememberTimePickerState(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        TimePickerDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Choose a time") },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(atMillis = withTime(draft.atMillis, state.hour, state.minute))
                    timeDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } },
        ) { TimePicker(state = state) }
    }
    if (showVoice) {
        VoicePickerDialog(
            voices = a.ttsVoices,
            selectedId = writtenVoice ?: a.ttsVoices.firstOrNull()?.id ?: "",
            onChoose = {
                writtenVoice = it
                draft = draft.copy(voiceId = it)
            },
            onDismiss = { showVoice = false },
        )
    }
}

private enum class CallingErrorField { WHEN, NUMBER, DURATION, ALARM }

/** An edit in progress is saved with the composition, not held in a copy the
 *  next composition would throw away. Every field is a primitive, so the
 *  saver is just a map of them. */
private val CallingProfileSaver = mapSaver(
    save = { p: CallingProfile ->
        mapOf(
            "id" to p.id,
            "name" to p.name,
            "number" to p.number,
            "contactName" to p.contactName,
            "atMillis" to p.atMillis,
            "whenMode" to p.whenMode,
            "delayAmount" to p.delayAmount,
            "greetingDelayMs" to p.greetingDelayMs,
            "duration" to p.duration,
            "text" to p.text,
            "audioPath" to p.audioPath,
            "voiceId" to p.voiceId,
            "busy" to p.busy,
            "enabled" to p.enabled,
        )
    },
    restore = { map ->
        CallingProfile(
            id = map["id"] as String,
            name = map["name"] as String,
            number = map["number"] as String,
            contactName = map["contactName"] as String,
            atMillis = map["atMillis"] as Long,
            whenMode = map["whenMode"] as String,
            delayAmount = map["delayAmount"] as String,
            greetingDelayMs = map["greetingDelayMs"] as Int,
            duration = map["duration"] as String,
            text = map["text"] as String,
            audioPath = map["audioPath"] as String,
            voiceId = map["voiceId"] as String,
            busy = map["busy"] as String,
            enabled = map["enabled"] as Boolean,
        )
    },
)

private fun kindOf(p: CallingProfile, context: Context): String = when {
    CallingGreetings.present(context, p.id) -> "record"
    p.audioPath.isNotBlank() -> "file"
    p.text.isNotBlank() -> "text"
    else -> ""
}

private fun newDraft() = CallingProfile(
    number = "",
    contactName = "",
    atMillis = System.currentTimeMillis() + 10 * 60_000L,
    whenMode = "minutes",
    delayAmount = "10",
)

private fun durationModeOf(value: String): String = when (value) {
    "message", "none" -> value
    else -> {
        val seconds = value.toLongOrNull()
        if (seconds != null && seconds >= 60 && seconds % 60 == 0L) "minutes" else "seconds"
    }
}

private fun durationAmountOf(value: String): String {
    val seconds = value.toLongOrNull() ?: return "60"
    return if (durationModeOf(value) == "minutes") (seconds / 60).toString() else seconds.toString()
}

private fun profileWhenLabel(p: CallingProfile): String = when (p.whenMode) {
    "minutes" -> "After ${p.delayAmount} minutes"
    "seconds" -> "After ${p.delayAmount} seconds"
    "tasker" -> "Tasker trigger"
    else -> formatWhen(p.atMillis)
}

private fun profileDurationLabel(value: String): String = when (value) {
    "message" -> "After greeting"
    "none" -> "No limit"
    else -> "$value seconds"
}

private fun busyLabel(value: String): String = when (value) {
    CallingProfile.DISCARD -> "Discard this profile"
    CallingProfile.NOW -> "Call anyway"
    else -> "Call when the line is free"
}

private fun contactDetails(context: Context, uri: Uri): Pair<String, String> = runCatching {
    var name = ""
    var number = ""
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { rows ->
        if (rows.moveToFirst()) name = rows.getString(0) ?: ""
    }
    val id = runCatching { ContentUris.parseId(uri) }.getOrNull()
    if (id != null) {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(id.toString()),
            null,
        )?.use { rows -> if (rows.moveToFirst()) number = rows.getString(0) ?: "" }
    }
    number to name
}.getOrDefault("" to "")

private fun withDay(atMillis: Long, dayMillis: Long): Long {
    val old = Calendar.getInstance().apply { timeInMillis = atMillis }
    val day = Calendar.getInstance().apply { timeInMillis = dayMillis }
    return day.apply {
        set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, old.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
    }.timeInMillis
}

private fun withTime(atMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = atMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }.timeInMillis

private fun formatWhen(atMillis: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(atMillis))
