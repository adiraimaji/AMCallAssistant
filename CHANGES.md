# Changes made to the original project

AM Call Assistant is a derivative work of **JemRec**, created by Yevhen
Yemchynskyi (<https://github.com/jemcik/JemRec>) and licensed under the Apache
License, Version 2.0.

This file lists the changes made in this fork. Apache 2.0 section 4(b) requires
that modified files carry prominent notices stating that they were changed:
each Markdown file changed by this fork says so at the top and points here. The
upstream copyright and licence text are retained in [LICENSE](LICENSE), and the
upstream notices are retained in [NOTICE.md](NOTICE.md). No part of the upstream
licence has been removed.

## 1.0 — changes made when the fork was created

- **Identity and packaging.** The app is named *AM Call Assistant*, its package
  is `com.adiraimaji.amcallassistant`, and a new logo (`icon/icon.svg`) is used
  for the launcher icon, themed icon and Quick Settings tile. The release APK is
  `AM-Call-Assistant.apk` and the debug APK is `AM-Call-Assistant-Debug.apk`; the
  release asset uploaded to GitHub uses the fixed release name so the in-app
  updater can download it.
- **Versioning.** This fork starts at 1.0; JemRec's earlier versions and
  changelogs are not part of its release history.
- **Tasker / Locale actions.** *Toggle AM Call Assistant* replaces *Main service
  on/off* and *AM Answering Machine* replaces *Answering machine controls*.
  Tasker variables written in a greeting (`%BATT`, `%LOC`, `%name`) are replaced
  with their values before the greeting is spoken, using Tasker's documented
  `VARIABLE_REPLACE_KEYS` protocol; variable names are never read out literally.
- **Duration field.** The answering action uses a free-text number of seconds
  instead of a fixed list: empty follows *App's Setting*, `0` means no limit,
  and a variable such as `%LONGEST` is resolved when the action fires.
- **One-shot answering action.** Firing *AM Answering Machine* answers the call
  ringing at that moment, once, with the action's own settings, then restores
  the app's answering settings. A run with no call leaves them untouched.
- **Greeting editor.** Typed greetings gained a *Play* preview.
- **In-app updates.** Opening the app checks GitHub for a newer release and
  shows its release notes with *Install* / *Cancel*; installing downloads the
  release APK and hands it to Android's installer. JemRec's setting only opened
  the releases page in a browser. A manual *Check for Update* row was added to
  Settings.
- **Builds.** Gradle runs `shellserver/build.ps1` on Windows and
  `shellserver/build.sh` on Unix so Android Studio builds work on both without
  a bash toolchain. JemRec's own third-party notices (libadb-android, Bouncy
  Castle, Conscrypt, scrcpy, fonts, artwork) are retained in `NOTICE.md`, and
  the scrcpy modifications are itemised in `shellserver/PATCHES.md`.

## 1.1 — changes since the first release

### Answering Machine profiles

- The answering machine became a list of profiles. A radio button selects the
  default one, whose greeting, ring delay, Do Not Disturb choice and call
  duration are used; each profile can be edited or deleted, and **New profile**
  opens an empty one. The master switch, test call, carrier check and SIM choice
  remain app-wide.
- Each profile has its own recorded, typed, audio-file or inherited greeting;
  recorded takes are stored per profile (`AnswerProfiles`, `AnswerGreetings`).
  Changing the default syncs the new greeting and settings to the recorder.
- One-time migration: an existing install with non-default answering settings or
  a greeting gets a single profile named *My greeting*; a fresh install starts
  empty.
- The answering screen moved delay, DND, duration and greeting controls out of
  dialogs into inline fields, capsules and radio rows, with a take timer.

### Calling Machine

- A new settings screen with profiles that place outgoing calls, play that
  profile's greeting and stay in the call for a chosen duration. Profiles hold
  number/contact, schedule, duration, busy behaviour and greeting; the editor
  lives in a second pane with Save/Back in the app bar.
- Schedule choices: minutes or seconds from now, a picked date and time, or
  **Tasker trigger** — a profile the app never starts itself; only the plugin
  action places that call.
- Exact alarms (`SCHEDULE_EXACT_ALARM`) are requested and the shell daemon dials
  (the app has no `CALL_PHONE`); numbers are validated, emergency numbers
  refused, and a fired profile is switched off until saved again.
- Each profile has its own greeting, uploaded only for its call; the answering
  greeting is restored afterwards. Calls are marked in recordings via the
  `JEMREC_CALLING` file tag.

### Tasker plugin

- A third action, **AM Calling Machine**, was added; it places a call with the
  number, duration, busy behaviour, greeting and greeting delay given in the
  action. The answering action answers the ringing call once with its own
  settings and restores the app's afterwards.
- Both actions gained a **profile dropdown** (`ProfileDropdown`): **Custom**
  uses the fields typed in the action; a saved profile's current values are read
  when the action fires, and changing a field returns the dropdown to Custom.
  A profile's relative minutes/seconds start when the action runs, a Tasker
  trigger calls at once, and a picked date/time profile is rejected by the
  action.
- A **Record** greeting capsule can use a profile's existing recorded greeting;
  no new recording can be made in a Tasker editor. One **Play greeting** button
  plays the selected Text/File/Record source.
- The config format went from version 2 (1.0) to version 8: v3 audio path, v4
  calling fields, v5 millisecond greeting delay, v6 greeting type, v7 the two
  profile ids and v8 the id of a greeting recorded while Custom is selected.
  All older versions still parse with their old behaviour.

### Greetings and voices

- Greetings can be audio files from storage (MP3, M4A/AAC, WAV, Ogg, FLAC and
  other phone-supported formats), picked with the system file picker or given
  by path/URI; Android's decoder converts them to 16-bit 48 kHz mono PCM, capped
  at 30 seconds (`AudioGreeting`, `FilePath`).
- Voice pickers are searchable and a tap selects a voice and closes the picker.
- The greeting delay is settable in milliseconds in profiles and the Tasker
  calling action (`0` = immediately, default `1000` ms).
- **Answer after** accepts any positive number of seconds; **Longest message**
  became **Call duration**, accepting any number of seconds (`0` = no limit).
- Editor activities use `adjustResize` and greeting fields are brought into
  view when the keyboard opens (`GreetingFieldFocusTest`).

### Shell daemon, docs and tests

- The shell recorder gained an `armCall` path and a `U` command to arm the
  recorder, dial, wait the configured delay, play the greeting with the
  microphone muted, and respect the call duration; emergency numbers are
  rejected before dialling.
- Screenshots, `docs/ARCHITECTURE.md`, `docs/SETUP.md`, `docs/setup.html`, the
  website generator, the F-Droid changelog (`fastlane/.../2.txt`) and the
  1.1 release notes were updated. New tests cover audio greetings, file paths,
  the voice picker, greeting-field focus and the expanded plugin protocol.
- Version bump: 1.0 (version code 1) → 1.1 (version code 2).

## Files changed by this fork

Grouped summary of the files touched since upstream (new files included):

- **Docs and metadata:** `README.md`, `NOTICE.md`, `CHANGES.md`,
  `docs/ARCHITECTURE.md`, `docs/SETUP.md`, `docs/setup.html`,
  `fastlane/metadata/android/en-US/changelogs/`.
- **App (Kotlin and resources):** `app/src/main/AndroidManifest.xml`,
  `answering/` (including the new `AnswerProfiles.kt`, `AnswerGreetings.kt`,
  `AudioGreeting.kt`), `audio/FilePath.kt`, `calling/`, `capture/`, `recorder/`,
  `recordings/`, `tasker/` (including the new `CallingEditActivity.kt`,
  `ProfileDropdown.kt`, `TaskerGreetingEditor.kt`), `ui/` (including the new
  `CallingMachineScreen.kt`, `InlineEditor.kt`, `VoicePickerDialog.kt`) under
  `app/src/main/java/com/adiraimaji/amcallassistant/`, plus their tests
  (including `GreetingFieldFocusTest.kt`) and UI screenshots.
- **Shell daemon (Java):** `shellserver/src/com/adiraimaji/amcallassistant/shell/`
  (`AnswerMachine.java`, `AnswerSettings.java`, `Commands.java`, `Wire.java`).
- **Build and site tools:** `gradle.properties`, `tools/build_site.py`.

## Release notes

- Fork-wide overview: [README.md — Differences from JemRec](README.md#differences-from-jemrec)
