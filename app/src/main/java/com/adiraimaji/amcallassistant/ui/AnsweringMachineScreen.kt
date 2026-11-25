package com.adiraimaji.amcallassistant.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adiraimaji.amcallassistant.answering.AnswerDiagnostics
import com.adiraimaji.amcallassistant.answering.AnswerGreetings
import com.adiraimaji.amcallassistant.answering.AnswerProfile
import com.adiraimaji.amcallassistant.answering.AnswerProfiles
import com.adiraimaji.amcallassistant.answering.AnsweringMachine
import com.adiraimaji.amcallassistant.answering.EmergencyPause
import com.adiraimaji.amcallassistant.answering.Greeting
import com.adiraimaji.amcallassistant.audio.FilePath
import com.adiraimaji.amcallassistant.capture.AudioAccess
import com.adiraimaji.amcallassistant.tasker.GreetingMaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The choosing that still needs a window of its own: a profile's "no limit"
 *  warning, the SIM choice, the two test reports, the emergency-pause warning
 *  and the voice list. Everything else is edited on the screen. */
private enum class AnswerDialog {
    NO_LIMIT, SIM, TEST, CARRIER, RESUME, VOICE,
}

/**
 * THE ANSWERING MACHINE, as two panes. The screen opens on its profiles: one
 * of them is the default, chosen with a radio button, and its settings and
 * greeting are the ones used when a call is answered. The master switch turns
 * the whole machine on or off. Tapping a card opens that profile's editor;
 * Back and Save live in the bar, as in the Calling Machine.
 *
 * The tests, the carrier report and the explanation of how answering works
 * stay on the list: they describe this phone, not one profile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnsweringMachineScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val a by vm.answer.state.collectAsState()
    val ag by AnswerGreetings.state.collectAsState()
    val holder by Greeting.holder.collectAsState()
    val listening = a.takeListening || holder != null
    val making by Greeting.making.collectAsState()
    val processing = a.processingTake || making != null
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(making) {
        if (making == null) {
            vm.answer.reload()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.answer.readTakeEnding()
        }
    }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val app = context.applicationContext
    var dialog by rememberSaveable { mutableStateOf<AnswerDialog?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { AnswerProfiles.all(app) }
    val defaultId = AnswerProfiles.default(app)?.id

    var draft by rememberSaveable(stateSaver = AnswerProfileSaver) { mutableStateOf(newAnswerDraft()) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var delayInput by rememberSaveable { mutableStateOf(draft.delay.toString()) }
    var longestInput by rememberSaveable { mutableStateOf(draft.longest.toString()) }
    var longestMode by rememberSaveable { mutableStateOf("preset") }
    var greetingText by rememberSaveable { mutableStateOf("") }
    var ttsVoiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var audioPath by rememberSaveable { mutableStateOf("") }
    var showWrite by rememberSaveable { mutableStateOf(false) }
    var showFile by rememberSaveable { mutableStateOf(false) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var greetingKind by rememberSaveable { mutableStateOf("") }
    // Save stays gray until the selected greeting has actually been made.
    // The accepted values are kept, not a flag written on every keystroke.
    var validatedText by rememberSaveable { mutableStateOf<String?>(null) }
    var validatedAudio by rememberSaveable { mutableStateOf<String?>(null) }
    var showVoice by rememberSaveable { mutableStateOf(false) }
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

    val editorOpen by vm.answerEditorOpen.collectAsState()
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            audioPath = FilePath.fromUri(context, uri) ?: uri.toString()
            draft = draft.copy(audioPath = audioPath)
        }
    }
    val audioPermission = rememberPermissionRequest { _ -> audioPicker.launch("audio/*") }
    LaunchedEffect(showWrite) {
        if (showWrite) vm.answer.loadTtsVoices() else vm.answer.closeTts()
    }
    LaunchedEffect(draft.id) { AnswerGreetings.load(context, draft.id) }
    LaunchedEffect(draft.id, greetingText.trim(), ttsVoiceId, greetingKind) {
        val text = greetingText.trim()
        if (greetingKind == "text" && text.isNotBlank()) {
            delay(700)
            AnswerGreetings.useText(
                context, draft.id, text,
                ttsVoiceId ?: a.ttsVoices.firstOrNull()?.id,
            ) { made -> if (made) validatedText = text }
        }
    }
    LaunchedEffect(draft.id, audioPath, greetingKind) {
        val path = audioPath
        if (greetingKind == "file" && path.isNotBlank()) {
            delay(700)
            AnswerGreetings.useAudio(context, draft.id, path) { made ->
                if (made) validatedAudio = path
            }
        }
    }
    val answerSaveBlocked = editorOpen && (ag.busy || when (greetingKind) {
        "text" -> greetingText.isBlank() || validatedText != greetingText.trim()
        "file" -> audioPath.isBlank() || validatedAudio != audioPath
        "record" -> !AnswerGreetings.present(app, draft.id)
        else -> false
    })
    LaunchedEffect(answerSaveBlocked) {
        // A pause in typing first: changing the bar on each key recomposed the
        // whole scaffold and dropped the text field's focus.
        if (answerSaveBlocked) delay(700)
        vm.setAnswerSaveEnabled(!answerSaveBlocked)
    }
    LaunchedEffect(Unit) {
        vm.answerEditRequests.collect { request ->
            if (request.id == null) {
                draft = newAnswerDraft()
                editing = null
            } else {
                AnswerProfiles.get(app, request.id)?.let { profile ->
                    draft = profile
                    editing = profile.id
                }
            }
            delayInput = draft.delay.toString()
            longestInput = draft.longest.toString()
            longestMode = "preset"
            error = null
            greetingNotice = null
            showWrite = false
            showFile = false
            showRecord = false
            greetingKind = answerKindOf(draft, app)
            showWrite = greetingKind == "text"
            showFile = greetingKind == "file"
            showRecord = greetingKind == "record"
            showVoice = false
            greetingText = ""
            audioPath = ""
        }
    }
    LaunchedEffect(Unit) {
        vm.answerSaveRequests.collect { saveAnswer(
            context, vm, draft, editing, delayInput, longestInput, longestMode,
            greetingKind, greetingText, ttsVoiceId ?: a.ttsVoices.firstOrNull()?.id ?: "default", audioPath,
            { error = it }, { revision++ },
        ) { showAnswerProfiles(draft, editing, vm) } }
    }
    LaunchedEffect(Unit) { vm.answeringMachineOpened() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.answer.readPendingTest()
        vm.answer.readTakeEnding()
    }
    val now = LocalNow.current

    when (dialog) {
        AnswerDialog.NO_LIMIT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("No limit?") },
            text = {
                Text(
                    "The message runs until the caller hangs up, or until they have been " +
                        "silent for ${AnsweringMachine.SILENCE_HANGUP_SECONDS} seconds. Until then " +
                        "your phone is in that call: you cannot make a call without ending it, " +
                        "and other calls wait. A caller somewhere noisy may never count as " +
                        "silent, and then it lasts until they hang up.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    draft = draft.copy(longest = AnsweringMachine.NO_LIMIT)
                    longestInput = "0"
                    longestMode = "preset"
                    dialog = null
                }) { Text("No limit") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        AnswerDialog.SIM -> ChoiceDialog(
            title = "Answer on",
            choices = a.sims.map { it.first } + AnsweringMachine.SIM_BOTH,
            selected = when (a.sim) {
                AnsweringMachine.SIM_DEFAULT -> a.defaultSim ?: AnsweringMachine.SIM_BOTH
                else -> a.sim
            },
            label = { id -> if (id == AnsweringMachine.SIM_BOTH) "Both SIMs" else simLabel(a, id) },
            onChoose = vm.answer::setSim,
            onDismiss = { dialog = null },
        )
        AnswerDialog.TEST -> TestDialog(
            pausedUntil = a.pausedUntil,
            start = { incoming, number -> dialog = null; vm.answer.startTest(incoming, number) },
            dismiss = { dialog = null },
        )
        AnswerDialog.CARRIER -> CarrierDialog(a, vm, dismiss = { dialog = null })
        AnswerDialog.RESUME -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Answer calls again?") },
            text = {
                Text(
                    "You called an emergency number. If they call you back while you cannot " +
                        "pick up, the answering machine would answer them with your greeting, " +
                        "instead of letting your phone ring until you can." +
                        (a.pausedUntil?.let { " The pause ends by itself on ${pauseEnd(it)}." } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    vm.answer.resumeAfterEmergency()
                    dialog = null
                }) { Text("Answer again") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Keep paused") } },
        )
        AnswerDialog.VOICE -> VoicePickerDialog(
            voices = a.ttsVoices,
            selectedId = ttsVoiceId ?: a.ttsVoices.firstOrNull()?.id ?: "",
            onChoose = {
                ttsVoiceId = it
                draft = draft.copy(voiceId = it)
            },
            onDismiss = { dialog = null },
            intro = "Only voices already on the phone are offered. Nothing is sent anywhere to be spoken.",
        )
        null -> Unit
    }

    a.askHeard?.let { type ->
        AlertDialog(
            onDismissRequest = vm.answer::dismissHeard,
            title = { Text("Did the caller hear your greeting?") },
            text = { Text("All of it, from the start, and no sound from around this phone.") },
            confirmButton = { TextButton(onClick = { haptics.confirm(); vm.answer.heard(type, true) }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.answer.heard(type, false) }) { Text("No") } },
        )
    }

    Column(modifier) {
        if (!editorOpen) {
            SettingsGroup {
                row {
                    val blocked = a.blocked
                    val paused = a.paused
                    SwitchRow(
                        title = "Answering machine",
                        summary = when {
                            blocked != null && !a.enabled -> blocked
                            paused != null -> "Paused after an emergency call, until ${pauseEnd(paused)}"
                            a.enabled -> "Answers the phone calls you don't take, after ${a.delay} seconds"
                            else -> "Off"
                        },
                        summaryColor = if (blocked != null && a.stageOne?.possible == false || paused != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        mark = headerMark(Marked.ANSWERING, marks(state, a.enabled), recorderStatus(state)),
                        checked = a.enabled,
                        enabled = a.enabled || blocked == null,
                        onCheckedChange = vm.answer::setEnabled,
                    )
                }
                if (a.paused != null) {
                    row {
                        SettingsRow(
                            title = "Answer again now",
                            summary = "It stands down for a day after you call an emergency number",
                            icon = Icons.Filled.Warning,
                            onClick = { dialog = AnswerDialog.RESUME },
                        )
                    }
                }
            }

            SettingsGroup(
                "Profiles",
                note = "The selected profile is the one the answering machine uses. Its greeting, " +
                    "delay, Do Not Disturb choice and call duration are used for every call it answers.",
            ) {
                for (profile in profiles) {
                    row(key = profile.id) {
                        Card(
                            onClick = {
                                draft = profile
                                editing = profile.id
                                vm.editAnswerProfile(profile.id)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        profile.name.ifBlank { "Answering profile" },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text("Answers after ${profile.delay} seconds", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Do Not Disturb: " + if (profile.dndShort) "After 2 rings" else "The same delay",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "Duration: " + if (profile.longest == AnsweringMachine.NO_LIMIT) {
                                            "No limit"
                                        } else {
                                            seconds(profile.longest)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val greetingType = when {
                                        AnswerGreetings.present(app, profile.id) -> "Record"
                                        profile.text.isNotBlank() -> "Text"
                                        profile.audioPath.isNotBlank() -> "File"
                                        else -> "None"
                                    }
                                    val greetingLength = AnswerGreetings.seconds(app, profile.id)
                                        ?.let { " · ${it.toInt()} s" } ?: ""
                                    Text(
                                        "Greeting: $greetingType$greetingLength",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                RadioButton(
                                    selected = profile.id == defaultId,
                                    onClick = {
                                        haptics.tap()
                                        AnswerProfiles.select(app, profile.id)
                                        vm.answer.profileChanged()
                                        revision++
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Clear of the floating New profile button over the last card.
            Spacer(Modifier.height(72.dp))

            SettingsGroup("Test on this phone") {
                row {
                    val stage = a.stageOne
                    SettingsRow(
                        title = "Checks",
                        summary = when {
                            a.checking -> "Checking..."
                            stage == null -> "Not run yet - tap to run"
                            stage.possible -> "Passed: this phone can play a greeting into a call. A test call tells for sure."
                            else -> stage.problem
                        },
                        summaryColor = if (stage?.possible == false) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        icon = Icons.Filled.Build,
                        onClick = vm.answer::runChecks,
                        enabled = !a.checking && state.daemonRunning,
                    )
                }
                row {
                    SettingsRow(
                        title = "Test call",
                        summary = when (a.test) {
                            "arming" -> if (a.testIncoming) "Getting the recorder ready..." else "Calling..."
                            "armed" -> when {
                                a.testUntil < now() -> "Asking the recorder how it went..."
                                a.testIncoming -> "Waiting for someone to call, until ${timeOfDay(a.testUntil)}"
                                else -> "Calling..."
                            }
                            "running" -> "Testing - your greeting is playing to the call"
                            else -> testedSummary(a)
                        },
                        icon = Icons.Filled.Call,
                        onClick = if (a.test == null) ({ dialog = AnswerDialog.TEST }) else null,
                        enabled = a.stageOne?.possible == true && a.greetingSeconds != null && state.daemonRunning,
                    )
                }
                row {
                    SettingsRow(
                        title = "Carrier voicemail",
                        summary = when {
                            a.checkingCarrier -> "Asking your carrier... this takes a few seconds"
                            a.carrier == null -> "Tap to ask your carrier when its voicemail answers"
                            a.carrier?.startsWith("off") == true -> "Off: nothing answers before AM Call Assistant"
                            a.carrierSeconds != null -> "Answers unanswered calls after ${a.carrierSeconds} seconds"
                            else -> "Your carrier did not say"
                        },
                        icon = SettingsIcons.Voicemail,
                        onClick = { dialog = AnswerDialog.CARRIER },
                        enabled = state.daemonRunning,
                    )
                }
                row {
                    SettingsRow(
                        title = "Copy a report",
                        summary = "This phone and what the tests found, to paste into a report on GitHub",
                        icon = Icons.Filled.Share,
                        onClick = { haptics.tap(); vm.answer.copyReport() },
                    )
                }
            }

            SettingsFooter(
                "When it answers, your microphone is muted and your greeting plays to the caller, " +
                    "then a beep. Their message is saved like any recording, marked as a message, " +
                    "and a notification tells you. While they speak you can still take the call: " +
                    "Talk to them in the notification, or unmute on the call screen.",
                "It answers phone calls only - not WhatsApp, Telegram, Viber or Google Meet " +
                    "calls - and never while you are on another call, a messenger call or in a " +
                    "meeting, or roaming, or " +
                    "for a day after you call an emergency number. If AM Call Assistant cannot tell who is " +
                    "calling, it lets the phone ring.",
                "AM Call Assistant records your greeting the way it records calls, through its recorder, so it " +
                    "never needs permission to use the microphone. A greeting you type is spoken on " +
                    "the phone by Android's text-to-speech. Nothing is sent anywhere. " +
                    "Experimental: tested on two phones, an Honor " +
                    "on 2G calls and a Galaxy S23 over Wi-Fi Calling. " +
                    "The test call here is how to know it works on yours.",
            )
        } else {
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
            }

            SettingsGroup("When it answers") {
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Answer after", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "How long it rings before AM Call Assistant answers. Your carrier's own voicemail, " +
                                "if you have it, must wait longer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        CompactField(
                            value = delayInput,
                            onValueChange = { typed ->
                                val digits = typed.filter(Char::isDigit).take(3)
                                delayInput = digits
                            },
                            label = "Seconds",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("With Do Not Disturb on", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "For callers Do Not Disturb would silence. Callers it lets through ring as usual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                row {
                    RadioRow(
                        title = "After 2 rings (about ${AnsweringMachine.DND_DELAY} seconds)",
                        selected = draft.dndShort,
                    ) { haptics.tap(); draft = draft.copy(dndShort = true) }
                }
                row {
                    RadioRow(
                        title = "The same delay",
                        selected = !draft.dndShort,
                    ) { haptics.tap(); draft = draft.copy(dndShort = false) }
                }
                row {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Call duration", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "How long a caller can speak after the beep before AM Call Assistant hangs up " +
                                "and saves the message. No limit waits for the caller to hang up, or to fall silent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val preset = when (draft.longest) {
                            30 -> "30 seconds"
                            60 -> "1 minute"
                            120 -> "2 minutes"
                            AnsweringMachine.NO_LIMIT -> "No limit"
                            else -> ""
                        }
                        Capsules(
                            labels = listOf("No limit", "30 seconds", "1 minute", "2 minutes", "Seconds"),
                            selected = if (longestMode == "seconds") "Seconds" else preset,
                        ) { label ->
                            when (label) {
                                "No limit" -> dialog = AnswerDialog.NO_LIMIT
                                "Seconds" -> longestMode = "seconds"
                                else -> {
                                    val value = when (label) {
                                        "30 seconds" -> 30
                                        "1 minute" -> 60
                                        else -> 120
                                    }
                                    longestMode = "preset"
                                    longestInput = value.toString()
                                    draft = draft.copy(longest = value)
                                }
                            }
                        }
                        if (longestMode == "seconds") {
                            Spacer(Modifier.height(4.dp))
                            CompactField(
                                value = longestInput,
                                onValueChange = { typed -> longestInput = typed.filter(Char::isDigit).take(5) },
                                label = "Seconds",
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                }
                row { InlineError(error) }
            }
            SettingsGroup("Greeting") {
                row {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val canPlay = !ag.busy && when (greetingKind) {
                            "record" -> AnswerGreetings.present(app, draft.id)
                            "file" -> audioPath.isNotBlank()
                            "text" -> greetingText.isNotBlank()
                            else -> false
                        }
                        TextButton(onClick = {
                            when (greetingKind) {
                                "record" -> AnswerGreetings.togglePreview(context, draft.id)
                                "file" -> scope.launch {
                                    GreetingMaker.fromAudio(context, audioPath)?.let(Greeting::preview)
                                }
                                "text" -> scope.launch {
                                    GreetingMaker.make(
                                        context, greetingText.trim(),
                                        ttsVoiceId ?: a.ttsVoices.firstOrNull()?.id,
                                    )?.let(Greeting::preview)
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
                        val greetingSeconds = ag.seconds?.toInt()?.coerceAtLeast(1)
                            ?: if (defaultId == null || draft.id == defaultId) {
                                a.greetingSeconds?.toInt()?.coerceAtLeast(1)
                            } else {
                                null
                            }
                        val hasSound = AnswerGreetings.present(app, draft.id) || greetingSeconds != null
                        Text(
                            when {
                                ag.recording -> "Recording your greeting"
                                ag.busy -> "Preparing your greeting"
                                greetingSeconds != null -> "Your greeting"
                                else -> "No greeting yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val detail = when {
                            ag.recording ->
                                "Speak now. Tap Stop when you are done (${Greeting.MAX_SECONDS} seconds at most)."
                            listening -> "Waiting for the recorder..."
                            a.greetingProblem != null -> a.greetingProblem.orEmpty()
                            greetingSeconds != null ->
                                "${seconds(greetingSeconds)}. " +
                                    if (a.playing) "Playing - tap to stop" else "Tap to play it"
                            else -> "Record what callers hear before the beep"
                        }
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (a.greetingProblem != null && !ag.recording && !listening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        ag.error?.takeIf { !ag.recording }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Capsules(
                            labels = listOf("Text", "File", "Record", "Delete"),
                            selected = when (greetingKind) {
                                "text" -> "Text"
                                "file" -> "File"
                                "record" -> "Record"
                                else -> ""
                            },
                            enabled = !ag.busy || ag.recording,
                            disabled = buildSet {
                                if (processing || listening) { add("Text"); add("File") }
                                // Not while a test call is armed or under way:
                                // it plays this greeting.
                                if (a.test != null || processing || listening) add("Delete")
                                if (!state.daemonRunning || processing || listening) add("Record")
                            },
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
                                "Delete" -> {
                                    AnswerGreetings.stopPreview()
                                    AnswerGreetings.delete(context, draft.id)
                                    draft = draft.copy(text = "", audioPath = "", voiceId = "default")
                                    greetingText = ""
                                    ttsVoiceId = null
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
                                    ag.recording -> "Recording your greeting"
                                    ag.seconds != null || greetingSeconds != null ->
                                        "Recorded greeting · ${(ag.seconds ?: 0.0).toInt()} s"
                                    else -> "No greeting recorded yet"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (ag.recording) {
                                TakeTimer(ag.tookAt)
                                Text(
                                    "Speak now. Tap Stop when you are done (${Greeting.MAX_SECONDS} seconds at most).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val recordLabel = if (ag.seconds != null || greetingSeconds != null) "Record again" else "Record"
                            FilledTonalButton(
                                onClick = {
                                    if (ag.recording) {
                                        AnswerGreetings.stopTake(context, draft.id)
                                    } else {
                                        AnswerGreetings.startTake(context, draft.id)
                                    }
                                },
                                enabled = !ag.busy || ag.recording,
                                modifier = Modifier.widthIn(min = 104.dp),
                            ) { Text(if (ag.recording) "Stop" else recordLabel, maxLines = 1) }
                        }
                        if (showWrite) {
                            CompactField(
                                value = greetingText,
                                onValueChange = {
                                    greetingText = it.take(300)
                                    draft = draft.copy(text = greetingText)
                                },
                                modifier = Modifier.bringIntoViewRequester(greetingFieldRequester),
                                onFocusChanged = { greetingFieldFocused = it },
                                label = "What callers hear",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 5,
                                supporting = "${greetingText.length}/300",
                            )
                            SettingsRow(
                                title = "Voice",
                                summary = a.ttsVoices.firstOrNull { it.id == ttsVoiceId }?.label
                                    ?: a.ttsVoices.firstOrNull()?.label
                                    ?: (a.ttsError ?: "Loading voices..."),
                                icon = Icons.Filled.Edit,
                                onClick = { showVoice = true },
                                enabled = a.ttsVoices.isNotEmpty(),
                            )
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
                                enabled = !ag.busy,
                            ) { Text("Choose a file") }
                        }
                        greetingNotice?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }


            FilledTonalButton(
                onClick = {
                    editing?.let { AnswerProfiles.remove(app, it) }
                    vm.answer.profileChanged()
                    revision++
                    showAnswerProfiles(newAnswerDraft(), null, vm)
                },
                enabled = editing != null,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete profile") }
        }
    }

    if (showVoice) {
        VoicePickerDialog(
            voices = a.ttsVoices,
            selectedId = ttsVoiceId ?: a.ttsVoices.firstOrNull()?.id ?: "",
            onChoose = {
                ttsVoiceId = it
                draft = draft.copy(voiceId = it)
            },
            onDismiss = { showVoice = false },
        )
    }
}

/** Save lives in the bar; this is the one copy of the work it does. */
private fun saveAnswer(
    context: android.content.Context,
    vm: MainViewModel,
    draft: AnswerProfile,
    editing: String?,
    delayInput: String,
    longestInput: String,
    longestMode: String,
    greetingKind: String,
    writtenText: String,
    voiceId: String,
    audioPath: String,
    onError: (String) -> Unit,
    onSaved: () -> Unit,
    onDone: () -> Unit,
) {
    val app = context.applicationContext
    val delay = delayInput.trim().toIntOrNull()
    if (delay == null || delay <= 0) {
        onError("Enter a number of seconds greater than zero.")
        return
    }
    var longest = draft.longest
    if (longestMode == "seconds") {
        val typed = longestInput.trim().toIntOrNull()
        if (typed == null) {
            onError("Enter a number of seconds, or 0 for no limit.")
            return
        }
        longest = typed
    }
    val saved = draft.copy(
        delay = delay,
        longest = longest,
        text = if (greetingKind == "text") writtenText.trim() else "",
        audioPath = if (greetingKind == "file") audioPath else "",
        voiceId = voiceId,
    )
    AnswerProfiles.save(app, saved)
    if (AnswerProfiles.default(app)?.id == saved.id || editing == null) {
        vm.answer.profileChanged()
    }
    // Text and file greetings are spoken/decoded into the profile's greeting
    // file at save time, so the answering machine has audio to play.
    when (greetingKind) {
        "text" -> if (saved.text.isNotBlank()) {
            AnswerGreetings.useText(app, saved.id, saved.text, saved.voiceId) { made ->
                if (made) vm.answer.profileChanged()
            }
        }
        "file" -> if (saved.audioPath.isNotBlank()) {
            AnswerGreetings.useAudio(app, saved.id, saved.audioPath) { made ->
                if (made) vm.answer.profileChanged()
            }
        }
    }
    onSaved()
    onDone()
}

private fun showAnswerProfiles(draft: AnswerProfile, editing: String?, vm: MainViewModel) {
    vm.showAnswerProfiles()
}

@Composable
internal fun TakeTimer(since: Long) {
    // The app's clock, which the screen tests fix.
    val clock = LocalNow.current
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(since) {
        while (true) {
            now = clock()
            delay(250)
        }
    }
    Text(
        takeSeconds(now - since),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun TestDialog(pausedUntil: Long?, start: (Boolean, String) -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    // Sent as a USSD code, not dialled: no call would come of it, and the
    // next call the user made would be taken as the test.
    val code = number.endsWith("#")
    // Never dialled for a test: the call it made would be an emergency call.
    val emergency = remember(number) { EmergencyPause.isEmergencyNumber(context, number) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Test call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pausedUntil == null) {
                        "Best: someone calls you. The next call in three minutes is answered with a " +
                            "short tone and your greeting, whoever is calling - then you are asked " +
                            "whether they heard it."
                    } else {
                        "Someone calling you is a test again from ${pauseEnd(pausedUntil)}, a day " +
                            "after your emergency call: until then, the caller may be them calling back."
                    },
                )
                Text(
                    "Or AM Call Assistant calls a number that answers by itself, such as your carrier's " +
                        "information line, and measures what it can.",
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter { it.isDigit() || it in "+*#" }.take(32) },
                    label = { Text("Number to call") },
                    singleLine = true,
                    isError = code || emergency,
                    supportingText = when {
                        code -> { { Text("A code ending in # is not a call") } }
                        emergency -> { { Text("Never an emergency number") } }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { start(true, "") }, enabled = pausedUntil == null) { Text("Someone calls me") }
        },
        dismissButton = {
            TextButton(onClick = { start(false, number) }, enabled = number.length >= 3 && !code && !emergency) {
                Text("Call the number")
            }
        },
    )
}

@Composable
private fun CarrierDialog(a: AnswerUi, vm: MainViewModel, dismiss: () -> Unit) {
    val longer = a.carrierSeconds?.let { it < 30 } == true
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Carrier voicemail") },
        text = {
            Text(
                "Your carrier's own voicemail takes calls nobody answers, after its own " +
                    "timer. AM Call Assistant has to answer first. AM Call Assistant can ask your carrier for the " +
                    "timer" + if (longer) {
                        ", and make it wait the longest it allows, 30 seconds - your carrier's " +
                            "voicemail then still takes calls AM Call Assistant does not. It is your carrier's " +
                            "setting, so it stays if AM Call Assistant goes."
                    } else {
                        "."
                    },
            )
        },
        confirmButton = {
            if (longer) {
                TextButton(onClick = { dismiss(); vm.answer.carrierWaitLonger() }) { Text("Wait 30 seconds") }
            } else {
                TextButton(onClick = { dismiss(); vm.answer.checkCarrier() }) { Text("Ask the carrier") }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

private fun simLabel(a: AnswerUi, id: String): String =
    a.sims.firstOrNull { it.first == id }?.second ?: "SIM"

internal fun seconds(s: Int): String = when {
    s < 60 -> "$s seconds"
    s == 60 -> "1 minute"
    s % 60 == 0 -> "${s / 60} minutes"
    else -> "$s seconds"
}

/** How long the take has run, in whole seconds. */
internal fun takeSeconds(elapsedMs: Long): String = "%d s".format(Locale.US, (elapsedMs / 1000).coerceAtLeast(0))

/** A time of day, as the screen writes it: in Latin digits in any language. */
internal fun timeOfDay(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

/** When a pause ends, with its day. */
internal fun pauseEnd(millis: Long): String =
    SimpleDateFormat("d MMM 'at' HH:mm", Locale.US).format(Date(millis))

/** The Test call row with no test under way: how the tests came out, and why
 *  an update cleared any, until a test is filed again. Tested in
 *  AnswerSummaryTest. */
internal fun testedSummary(a: AnswerUi): String {
    val results = AnswerDiagnostics.summary(a.results)
    val why = when (a.cleared) {
        AnswerDiagnostics.Cleared.VOLTE ->
            "Your VoLTE test was cleared: before 0.31 a test over Wi-Fi Calling could be filed as VoLTE. Test again"
        AnswerDiagnostics.Cleared.ROUTES ->
            "Your tests were cleared: since 0.31 the greeting's way to the caller follows the call. Test again"
        null -> null
    }
    return when {
        why == null -> results ?: "Not tested on this phone yet"
        results == null -> why
        else -> "$results. $why"
    }
}

/** The answering machine in a few words, for its row in Settings. */
internal fun answerSummary(a: AnswerUi): String = when {
    a.paused != null -> "Paused after an emergency call"
    a.enabled -> "After ${a.delay} seconds" + when {
        a.cleared != null -> " - test again after the update"
        a.results.isEmpty() -> " - not tested on this phone yet"
        else -> ""
    }
    a.stageOne?.possible == false -> "Not available on this phone"
    !a.callTracking -> "Off - needs exact call tracking"
    else -> "Off"
}

/** The icon the row and the list share. */
internal val messageIcon = SettingsIcons.Voicemail

private val AnswerProfileSaver = mapSaver(
    save = { p: AnswerProfile ->
        mapOf(
            "id" to p.id, "name" to p.name, "delay" to p.delay, "dndShort" to p.dndShort,
            "longest" to p.longest, "text" to p.text, "audioPath" to p.audioPath, "voiceId" to p.voiceId,
        )
    },
    restore = { map ->
        AnswerProfile(
            id = map["id"] as String,
            name = map["name"] as String,
            delay = map["delay"] as Int,
            dndShort = map["dndShort"] as Boolean,
            longest = map["longest"] as Int,
            text = map["text"] as String,
            audioPath = map["audioPath"] as String,
            voiceId = map["voiceId"] as String,
        )
    },
)

private fun answerKindOf(p: AnswerProfile, context: android.content.Context): String = when {
    AnswerGreetings.present(context, p.id) -> "record"
    p.audioPath.isNotBlank() -> "file"
    p.text.isNotBlank() -> "text"
    else -> ""
}

private fun newAnswerDraft() = AnswerProfile()
