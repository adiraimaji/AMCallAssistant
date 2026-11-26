# AM Call Assistant

> **Modified from JemRec.** This project is an Apache-2.0 licensed fork of
> JemRec; the changes made to the original project are listed in
> [CHANGES.md](CHANGES.md).

**Version 1.2 — current release**

AM Call Assistant records phone calls and messenger calls on an unrooted Android
phone, and can answer the call ringing right now with a spoken greeting. Its
native setup pairs the phone with its own Wireless debugging once and needs no
root, no companion app and no computer; fresh installs can instead choose the
optional Shizuku route if they already have Shizuku installed. Settings shows
which mode is in use and can switch between them.

This is an open-source fork of [JemRec](https://github.com/jemcik/JemRec) by
Yevhen Yemchynskyi. See [Differences from JemRec](#differences-from-jemrec) and
[Attribution](#attribution-and-license).

## What it does

- Records both sides of a phone call as Ogg Opus, about 12 MB an hour, in
  `Recordings/AM Call Assistant`. Automatic mode records every call; *Ask first*
  waits for you to tap Record. Messenger calls (WhatsApp, Telegram, Signal,
  Viber) and Google Meet can be recorded too on Android 14+.
- Warns when a recording is silent, cut short, or when something is in the way
  (recorder down, battery setting, folder or notification access).
- Plays calls as a waveform, in both ears, through the earpiece, with the screen
  off. A Calls tab says what was recorded and, when it was not, why.
- Offers three looks — Memo, Deck and Multitrack — each in light, dark or the
  phone's theme.
- Has an answering machine (off until enabled) that plays a greeting you record,
  type, or pick from an audio file in storage, with the microphone muted, and
  keeps the caller's message. It opens on its profiles: a radio button picks
  the default one, whose greeting, ring delay, Do Not Disturb choice and call
  duration are used. Each profile can be edited or deleted; the master switch,
  test call and carrier check stay on the list.
- Has a **Calling Machine**: profiles that place calls at a chosen date and
  time, play that profile's greeting when the other end answers, and stay in
  the call for its duration. A call can be set for a number of minutes or
  seconds from now, or for a specific date and time. The screen opens on the
  saved profiles: tap one to edit it, or use its switch to turn it on or off;
  **New profile** opens an empty one. **Tasker trigger** is a profile the app
  never starts on its own: only the plugin action places that call. Calls it
  places are tagged in the
  recordings list. Android's exact-alarm allowance is requested when a profile
  is saved.
- Has three **Tasker / Locale** actions under **Plugin → AM Call Assistant**.
- Number fields in the Calling Machine editor and the **AM Calling Machine**
  action open a dropdown of your contacts: tapping the field shows every phone
  number, typing in that same field filters the list, and picking a row fills
  the number. One row is shown per number.
- **Updates happen in the app.** Every opening checks GitHub for a newer release.
  When there is one, its release notes are shown with **Install** and **Cancel**;
  Install downloads `AM-Call-Assistant.apk` and passes it to Android's installer.
  The **Check for Update** row in Settings runs the same check and confirms with a
  toast when this is already the newest — the check on opening never does.
- Recordings and pairing stay on the phone. The only outgoing request is the
  update check to GitHub; it carries no recordings, contacts or identifiers.

Requires Android 12 or later and a phone whose audio hardware exposes the
voice-call source. A self-test under Settings → Diagnostics tells you if yours
works.

## Differences from JemRec

AM Call Assistant keeps JemRec's recording engine, shell daemon, setup flow and
design. Where it differs from the upstream project:

- **Identity.** The app is *AM Call Assistant*, its package is
  `com.adiraimaji.amcallassistant`, and its logo is `icon/icon.svg`, used for the
  launcher icon, themed icon and Quick Settings tile. It is a separate app from
  JemRec, with its own settings and recordings.
- **Versioning.** This fork begins at 1.0; JemRec's earlier versions and
  changelogs are not part of its release history.
- **Tasker action names.** *Toggle AM Call Assistant* replaces *Main service
  on/off*; *AM Answering Machine* replaces *Answering machine controls*.
- **Tasker variables.** `%BATT`, `%LOC` or any Tasker variable written in a
  greeting is replaced with its value before it is spoken, using Tasker's
  documented `VARIABLE_REPLACE_KEYS` protocol. Variable names are never spoken
  literally.
- **Call duration.** A free-text field in seconds: empty follows *App's
  Setting*, `0` means no limit, `300` means five minutes. A variable such as
  `%LONGEST` works there; an unresolved one means no limit.
- **Answering action.** Firing *AM Answering Machine* answers the call ringing
  at that moment, once, with the action's own settings, then puts the app's
  answering settings back. A run with no call leaves them untouched.
- **Greeting editor.** Typed greetings have a **Play** preview; voice lists in
  the app and the Tasker action are searchable and a tap selects a voice. A
  greeting can also be an audio file from storage (MP3, M4A, WAV and other
  formats Android reads), chosen in the app or by path in the Tasker action.
- **Calling Machine.** Scheduled outgoing calls with profiles, a Tasker action,
  and a recording tag; JemRec has no outgoing-call feature.
- **Setup modes.** Native self-pairing is joined by an optional Shizuku route;
  Settings shows the mode in use and can switch it. JemRec has only its ADB
  setup.
- **Contact picker.** Phone numbers are read inside the app and offered in a
  dropdown attached to the number field; typing in the field filters the list.
  JemRec has no in-app contact picker.
- **Updates.** A built-in updater installs the newest release from GitHub;
  JemRec's setting opened the releases page in a browser.
- **Release files.** The release APK is `AM-Call-Assistant.apk` and the debug
  APK is `AM-Call-Assistant-Debug.apk`. The release asset uploaded to GitHub has
  the same fixed name, which is what the updater downloads.
- **Windows builds.** Gradle runs `shellserver/build.ps1` on Windows and
  `shellserver/build.sh` on Unix, so Android Studio's Build and Run work on
  both without a bash toolchain.

## Install

Download `AM-Call-Assistant.apk` from the
[Releases page](https://github.com/adiraimaji/AMCallAssistant/releases) and
install it. Android asks you to allow installing from an unknown source once.
If a newer release is out, the app offers it when you open it. JemRec can stay
installed alongside this app; they use different packages, settings and folders.

## Setup

1. Turn on **Developer options**, then **USB debugging** and **Wireless
   debugging** (some skins add one more switch).
2. Open AM Call Assistant and follow setup. It pairs over Wireless debugging;
   type the pairing code into the notification when asked.
3. Run the self-test in Settings → Diagnostics to confirm both sides of a call
   are captured.

A fresh install first asks which mode to use. **Native** follows the steps
above. **Shizuku** needs Shizuku (or a compatible fork such as Shizuku-Next)
installed, running and allowing AM Call Assistant, but no pairing code and no
Wireless debugging. **Settings → Setup → Mode of operation** shows which one is
in use and switches between them; switching runs the new mode's setup steps
again and keeps your recordings, permissions and pairing.

Pairing survives reboots, and Android's seven-day pairing limit if you let it.
The recorder restarts after a reboot when the phone lets the app run on its own.

## Tasker plugin

Under **Plugin → AM Call Assistant**:

- **Toggle AM Call Assistant** turns recording on or off when the action runs.
  Its *Revert* option puts back the setting that was there before the first
  plugin action.
- **AM Calling Machine** places a call with the number, duration and greeting
  given in the action, or uses a saved profile chosen in the dropdown. A
  profile's minutes or seconds start when the action runs; **Tasker trigger**
  calls at once. A profile with a picked date and time cannot be saved here —
  choose **Custom** instead. Changing any field switches the dropdown back to
  Custom, leaving the saved profile alone.
- **AM Answering Machine** answers the ringing call immediately, just once.
  Choose the delay under Do Not Disturb, the call duration, and a greeting:
  written text with a voice, or a path to an audio file (Tasker variables work
  in the path). Both may be filled; the **Text** or **File** capsule you select
  says which one this action uses. A **Record** capsule is shown for a selected
  profile that already has a recorded greeting. The single **Play greeting**
  button at the top right of the Greeting section plays whatever type is
  selected; no recording can be made here. Fields left at **App's Setting** follow whatever you chose
  in the app at the time the action fires.
  It can also use a saved answering profile from the dropdown; **Custom** keeps
  the fields typed here, and changing any field switches back to Custom. This
  action does not change the app's own answering settings.

In the answering and calling actions, Tasker variables are written the way
Tasker writes them — `%BATT`, `%LOC`, `%name` — and are substituted before the
action runs. An unresolvable call-duration variable means no limit.

## Build from source

You need JDK 21 (the shell daemon's bytes depend on it), the Android SDK with
platforms 36 and 37, and build-tools 36.0.0.

```sh
./gradlew assembleRelease     # AM-Call-Assistant.apk
./gradlew assembleDebug       # AM-Call-Assistant-Debug.apk
```

The daemon jar under `shellserver/` is built by the `buildShellServer` Gradle
task, which runs `shellserver/build.sh` on Unix and `shellserver/build.ps1` on
Windows. Both use the same pinned javac 21 and build-tools d8. Android Studio's
Build and Run work without extra setup:

```bat
gradlew.bat assembleDebug
```

Release signing is read from the environment (`AMCA_KEYSTORE`,
`AMCA_KEYSTORE_PASSWORD`, `AMCA_KEY_ALIAS`, `AMCA_KEY_PASSWORD`). With no
keystore the release APK is left unsigned rather than signed with the debug key.

Run `./gradlew test` for the unit and contract tests. More detail is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and
[docs/SETUP.md](docs/SETUP.md).

## Repository layout

- `app/` — Android application (Kotlin, Compose) and its tests.
- `shellserver/` — the shell-side recorder daemon, vendored scrcpy sources,
  protocol fixtures and tests.
- `icon/` — `icon.svg`, the source artwork for the app's icons.
- `docs/` — manual, architecture and development notes, plus the website.
- `fastlane/` — F-Droid/GitHub listing metadata.
- `tools/` — device checks, screen recording and site tools.

## Privacy and security

AM Call Assistant asks for no microphone permission and reads no phone state in
the app process; the shell-side daemon does the capture. `INTERNET` serves the
loopback socket to that daemon and the in-app update check. No analytics, no
account, no upload of calls or contacts. `REQUEST_INSTALL_PACKAGES` lets the
updater hand a downloaded release to Android's installer.

See [SECURITY.md](SECURITY.md) to report a problem privately, and the
[privacy policy](docs/privacy-policy.html).

## Attribution and license

- **JemRec** — Copyright 2026 Yevhen Yemchynskyi,
  <https://github.com/jemcik/JemRec>. This project is a derivative of it and
  keeps its Apache 2.0 notices.
- **AM Call Assistant** — Copyright 2026 Adiraimaji.
- Third-party components (libadb-android, Bouncy Castle, Conscrypt, scrcpy,
  fonts and artwork) are listed in [NOTICE.md](NOTICE.md).

Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE).
