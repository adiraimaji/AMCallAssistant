package com.adiraimaji.amcallassistant.ui

import android.app.Application
import com.adiraimaji.amcallassistant.adb.AdbTransport
import com.adiraimaji.amcallassistant.calls.AfterCall
import com.adiraimaji.amcallassistant.calls.CallHistory
import com.adiraimaji.amcallassistant.calls.CallLogLookup
import com.adiraimaji.amcallassistant.calls.CallRules
import com.adiraimaji.amcallassistant.calls.CallTracking
import com.adiraimaji.amcallassistant.calls.VoipApps
import com.adiraimaji.amcallassistant.calls.VoipCallNotes
import com.adiraimaji.amcallassistant.capture.AudioAccess
import com.adiraimaji.amcallassistant.capture.CaptureDaemon
import com.adiraimaji.amcallassistant.capture.DebugNotificationCleaner
import com.adiraimaji.amcallassistant.capture.RecordingMode
import com.adiraimaji.amcallassistant.playback.BothEarsSetting
import com.adiraimaji.amcallassistant.playback.PlayThroughSetting
import com.adiraimaji.amcallassistant.recorder.DebugSwitchesOff
import com.adiraimaji.amcallassistant.recorder.KeepPaired
import com.adiraimaji.amcallassistant.recorder.NetworkRevive
import com.adiraimaji.amcallassistant.recorder.RecorderHealth
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import com.adiraimaji.amcallassistant.recorder.StartWithoutWifi
import com.adiraimaji.amcallassistant.recordings.FileNames
import com.adiraimaji.amcallassistant.recordings.QualitySetting
import com.adiraimaji.amcallassistant.recordings.Recording
import com.adiraimaji.amcallassistant.recordings.RecordingLibrary
import com.adiraimaji.amcallassistant.recordings.RecordingStore
import com.adiraimaji.amcallassistant.recordings.Retention
import com.adiraimaji.amcallassistant.setup.PairingNotification
import com.adiraimaji.amcallassistant.setup.Setup
import com.adiraimaji.amcallassistant.system.ShizukuShell
import com.adiraimaji.amcallassistant.system.ShellGrant
import com.adiraimaji.amcallassistant.ui.theme.Look
import com.adiraimaji.amcallassistant.ui.theme.Looks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A setting the screen shows: read off the main thread, then put into the
 *  state as it is when the reading arrives. */
internal typealias Read = suspend (Application) -> (UiState) -> UiState

/** [get]'s value, put by [put]. One that throws leaves the state's own
 *  value as it was, and the reading lands with the rest: thrown on, it kept
 *  the window unread - home at Checking, every other screen blank - until
 *  the next return to the app (review, 2026-11-18). */
internal fun <T> read(get: suspend (Application) -> T, put: UiState.(T) -> UiState): Read = { app ->
    val read: Result<T> = try {
        Result.success(get(app))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("AMCallAssistant", "reading: one read failed, its value kept", e)
        Result.failure(e)
    }
    read.fold(onSuccess = { value -> { state -> state.put(value) } }, onFailure = { { state -> state } })
}

/**
 * The settings saved while a reading may be under way, read as it lands,
 * not with the rest: one saved between the reading's read and its landing
 * was shown and then put back by the reading until the next.
 *
 * - The ones the screen watches as they are saved (RecorderSwitch's,
 *   DebugSwitchesOff's, VoipCallNotes' and DebugNotificationCleaner's
 *   watch): the tile, a notification-access grant that outlived its screen.
 * - Everything the screen saves and shows at once, without a reading of its
 *   own: every choice Settings saves from its rows, the tile's answer, the
 *   selection tip seen, the call-log offer dismissed, the two permissions'
 *   answers. A reading waits seconds on the recorder; one made then -
 *   "Play recordings", file names, the rules, the tile - came back as it was
 *   before (the 1.1 review, 2026-11-15).
 *
 * Preferences, read from memory, and permission checks, which Android
 * answers from its own cache: quick enough for the main thread, where the
 * landing is. Nothing here may read a file or query a provider.
 */
internal fun asSaved(app: Application, state: UiState): UiState = state.copy(
    enabled = RecorderSwitch.isOn(app),
    setupMethod = Setup.method(app),
    debuggingOff = DebugSwitchesOff.recorderGone(app),
    voipNames = VoipCallNotes.enabled(app),
    debugNotificationHidden = DebugNotificationCleaner.hiding(app),
    keepPairedWaiting = KeepPaired.waiting(app),
    keepPairedRefused = KeepPaired.refused(app),
    automatic = RecordingMode.of(app) == RecordingMode.AUTOMATIC,
    afterCallNotice = AfterCall.enabled(app),
    playThrough = PlayThroughSetting.of(app),
    bothEars = BothEarsSetting.of(app),
    offMode = DebugSwitchesOff.mode(app),
    startWithoutWifi = StartWithoutWifi.enabled(app),
    autoDeleteAfterDays = Retention.maxAgeDays(app),
    autoDeleteShorterThan = Retention.minSeconds(app),
    ruleContacts = CallRules.contacts(app),
    ruleOtherNumbers = CallRules.otherNumbers(app),
    callTracking = CallTracking.enabled(app),
    voipEnabled = VoipApps.enabled(app),
    fileNames = FileNames.format(app),
    quality = QualitySetting.of(app),
    tileAdded = RecorderTile.isAdded(app),
    selectHintSeen = SelectHint.seen(app),
    canReadCallLog = CallLogLookup.granted(app),
    canReadAudio = AudioAccess.granted(app),
    canReadContacts = CallLogLookup.photosGranted(app),
    // What is left to offer is read with the rest; a dismissal, as it lands.
    offerCallLog = state.offerCallLog && !CallLogLookup.bannerDismissed(app),
    // What the notice says, as it lands: the mode switched, or the notice
    // put away, while a reading was out came back as before - the header
    // Ready over Ask first's question off (review, 2026-11-18). From what
    // the reading found Android saying, and preferences.
    notificationsOff = com.adiraimaji.amcallassistant.calls.NotificationHealth.notice(app, state.notificationSwitches),
    batteryNotice = com.adiraimaji.amcallassistant.system.BatteryHealth.notice(app, state.batteryRestricted),
)

/**
 * Everything a reading takes in besides the lists and the settings read as
 * it lands ([asSaved]), in the order it is read: each once, its read beside
 * the field it fills. A new one is one line here - or in [asSaved], for a
 * setting saved from the screen.
 */
private val SETTINGS: List<Read> = listOf(
    // CONFIRMED, because this reading decides whether to revive.
    //
    // A single ping is a health check; here it was a death certificate. One
    // missed reply - and on battery, with the CPU throttled and the app just
    // resumed, they are missed - meant "the recorder is down", which fired a
    // revive, which re-armed Wireless debugging, which made Android post its
    // notification. Every trip to the home screen and back. Plugged into USB
    // the phone stays awake, the ping never misses, and none of it happens;
    // that is what made it look like a mystery rather than a timeout.
    read(CaptureDaemon::isRunningConfirmed) { copy(daemonRunning = it) },
    // For Settings' "Mode of operation" row: the shell route is in use only
    // while Shizuku's binder is there and permission was granted. On IO, not
    // in asSaved: checkSelfPermission is a call out to Shizuku's service.
    read({ ShizukuShell.binderAvailable() && ShizukuShell.granted() }) { copy(shizukuConnected = it) },
    // Read alongside the daemon check: if the recorder is down, the header
    // needs to know whether the ADB channel is even open before it tells the
    // user to wait for Wi-Fi.
    read(Setup::wirelessDebuggingOn) { copy(wirelessDebuggingOn = it) },
    read(Setup::usbDebugging) { copy(usbDebugging = it) },
    read(Setup::developerOptionsHidden) { copy(developerOptionsHidden = it) },
    // Why the recorder is down, once for both of the header's readings of it.
    read(RecorderHealth::cause) {
        copy(
            networkNotAllowed = it == RecorderHealth.Problem.NETWORK_NOT_ALLOWED,
            pairingRefused = it == RecorderHealth.Problem.CANNOT_CONNECT,
        )
    },
    read(NetworkRevive::onWifi) { copy(onWifi = it) },
    read(PairingNotification::blocked) { copy(pairingNotificationsBlocked = it) },
    // What Android says is off of AM Call Assistant's notifications: questions to its
    // notification service, so here, off the main thread; what the notice
    // says of it is decided as the reading lands (asSaved).
    read(com.adiraimaji.amcallassistant.calls.NotificationHealth::switches) { copy(notificationSwitches = it) },
    // The Battery setting Restricted: a question to Android's activity
    // manager, so here; the notice is decided as the reading lands.
    read(com.adiraimaji.amcallassistant.system.BatteryHealth::read) { copy(batteryRestricted = it) },
    // Only until setup is done: a getprop, on the phones that may have the
    // switch.
    read({ !Setup.isComplete(it) && ShellGrant.switchMayBeOff() }) { copy(systemOptimizationSwitch = it) },
    read(RecordingStore::describe) { copy(folder = it) },
    read({ if (com.adiraimaji.amcallassistant.system.AutoStart.available(it)) com.adiraimaji.amcallassistant.system.AutoStart.label(it) else null }) { copy(autoStartLabel = it) },
    read(com.adiraimaji.amcallassistant.recordings.FolderAccess::lost) { copy(folderLost = it) },
    read({ RecordingStore.treeUri(it) == null }) { copy(usingDefaultFolder = it) },
    // An earpiece, and a proximity sensor to know when it is at the ear:
    // what "Play recordings" offers.
    read({ EarWatch.hasEarpiece(it) to EarWatch.canDarkenScreen(it) }) { (earpiece, darkens) ->
        copy(earpieceAvailable = earpiece, raiseAvailable = earpiece && darkens)
    },
    // Android's pairing timeout, for Keep AM Call Assistant paired: a settings query.
    read(KeepPaired::timeoutMs) { copy(pairingKept = it == 0L, pairingDays = KeepPaired.days(it)) },
    read(StartWithoutWifi::readyForUser) { copy(portSet = it) },
    read(StartWithoutWifi::openedThisBoot) { copy(portOurs = it) },
    read(StartWithoutWifi::failedThisBoot) { copy(portFailed = it) },
    read(StartWithoutWifi::closeFailedThisBoot) { copy(portCloseFailed = it) },
    read({ CallLogLookup.anythingLeftToOffer(it) && !CallLogLookup.bannerDismissed(it) }) { copy(offerCallLog = it) },
    // Whose calls: the choice, the contacts chosen - a contacts query or two
    // each, so only for the choices that list them - and whether contacts
    // may be read at all, which telling who is who needs. Each read once.
    read({ app ->
        val contacts = CallRules.contacts(app)
        val canReadContacts = CallLogLookup.photosGranted(app)
        val listed = canReadContacts && (contacts == CallRules.Contacts.ALL_BUT || contacts == CallRules.Contacts.ONLY)
        Triple(contacts, canReadContacts, if (listed) CallRules.chosen(app) else emptyList())
    }) { (_, _, chosen) ->
        // The choice and the permission are read as the reading lands
        // (asSaved); the contacts chosen, a query, here.
        copy(ruleChosen = chosen)
    },
    read(VoipApps::installed) { copy(voipInstalled = it) },
    read(VoipApps::supported) { copy(voipSupported = it) },
    read(FileNames::honorDialer) { copy(honorDialer = it) },
    read(FileNames::inHonorFolder) { copy(inHonorFolder = it) },
    read(::appVersion) { copy(appVersion = it) },
)

/**
 * The screen's reading of everything it shows from outside itself: the
 * recordings and the trash, the call log while its tab is up, Multitrack's
 * caller colours, the recorder's state and every setting (SETTINGS) - read
 * together, off the main thread, and put into the state at once, with the
 * switches the screen watches as they are saved then ([asSaved]).
 *
 * After each reading is put in, in this order: the selection keeps only the
 * rows still shown, the answering machine reads its own settings again,
 * playback hears of the list (Playback.listRead), and the waveform fill-in
 * goes over it.
 */
internal class Reading(
    private val app: Application,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<UiState>,
    private val answer: AnswerController,
    private val playback: Playback,
    private val fill: WaveformFill,
) {
    /** Readings begun, and the latest whose list is on screen. Main thread. */
    private var started = 0L
    private var applied = 0L

    /**
     * Fire and forget, for callers that only want the screen to catch up.
     *
     * Anything that needs the READING before it carries on must await [now]
     * instead. This one returns the instant it is called, which is how a
     * caller could finish, drop its busy flag and let the screen render a
     * verdict from before the work it had just done.
     */
    fun refresh(): Job = scope.launch { now() }

    /** A reading, put into the state before it returns. */
    suspend fun now() {
        // Readings overlap - a resume, a save announced, a keep toggled - and
        // each reads the list, then waits on the recorder for seconds. One
        // started earlier and finishing later must not put back a list from
        // before the other: a recording just saved vanished until the next
        // resume.
        val generation = ++started

        // ALL of this is file I/O and it must not run on the main thread.
        // The screen's scope dispatches on Main, and listing recordings
        // queries MediaStore and opens every file with MediaMetadataRetriever
        // to read its duration. On the UI thread that freezes the app - and
        // because the activity had been brought to the front over the dialer,
        // it froze the dialer's touches with it. An unresponsive phone right
        // after a call is about the worst thing this app could do.
        val reading = withContext(Dispatchers.IO) {
            val (recordings, trashed) = RecordingLibrary.read(app)
            // Only while the tab is up: it is the call log and a contacts
            // lookup per number, and nobody is looking at it otherwise. Null
            // when not read, so this reading leaves alone a list a later one
            // has read already.
            // Whether this is the call log itself: without the permission the
            // list is an empty one, which says nothing of the calls - and a
            // reading that began before a grant and lands after it took the
            // tab out of unread with it (review, 2026-11-18). Asked before the
            // load: a grant between the two is then read as no grant, and the
            // next reading reads the log; the other way round, the empty list
            // passed for it (review, 2026-11-18).
            val withLog = CallLogLookup.granted(app)
            val calls = if (state.value.homeTab == HomeTab.CALLS) CallHistory.load(app, recordings, trashed) else null
            val callsRead = calls != null && withLog
            // Before the lists reach the screen, so no row shows a colour and
            // then changes it; and given only in Multitrack, the one look that
            // has them - a look never chosen keeps nothing. Once given, they
            // stay through a change of look, so a caller's colour is theirs.
            val colours = if (Looks.choice(app).look == Look.MULTITRACK) {
                CallerColours.update(
                    app,
                    recordings.map { it.caller to (it.startedAtMillis ?: 0L) } + (calls ?: state.value.calls).map { it.label to it.at },
                )
            } else {
                emptyMap()
            }
            val settings = SETTINGS.map { it(app) }
            Lists(recordings, trashed, calls, colours, callsRead) to settings
        }
        val (lists, settings) = reading

        if (generation < applied) return
        applied = generation
        state.update { now ->
            asSaved(app, settings.fold(now) { s, put -> put(s) }).copy(
                recordings = lists.recordings,
                trashed = lists.trashed,
                calls = lists.calls ?: now.calls,
                callerColours = lists.colours,
                connected = AdbTransport.lastKnownConnected,
                listUnread = false,
                callsUnread = now.callsUnread && !lists.callsRead,
            ).let { next ->
                // A name that is no longer shown cannot stay selected. It would
                // still be counted by the toolbar - the count the user reads
                // before confirming a deletion - and deleted with the rest.
                next.copy(selected = stillShown(next))
            }
        }
        answer.reload()
        playback.listRead(lists.recordings)
        fill.start()
    }

    /** The lists a reading takes in. */
    private class Lists(
        val recordings: List<Recording>,
        val trashed: List<Recording>,
        val calls: List<CallHistory.Call>?,
        val colours: Map<String, Int>,
        /** [calls] read from the call log, the permission held. */
        val callsRead: Boolean,
    )
}
