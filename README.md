# AM Call Assistant

**Version 1.0 — initial release**

AM Call Assistant records phone calls and messenger calls on an unrooted Android
phone, and can answer the call ringing right now with a spoken greeting. It needs
no root, no Shizuku, no companion app and no computer: the phone pairs itself
with its own Wireless debugging once, in setup.

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
- Has an answering machine (off until enabled) that plays a recorded or typed
  greeting with the microphone muted, and keeps the caller's message.
- Has two **Tasker / Locale** actions under **Plugin → AM Call Assistant**.
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
- **Longest message.** A free-text field in seconds: empty follows *App's
  Setting*, `0` means no limit, `300` means five minutes. A variable such as
  `%LONGEST` works there; an unresolved one means no limit.
- **Answering action.** Firing *AM Answering Machine* answers the call ringing
  at that moment, once, with the action's own settings, then puts the app's
  answering settings back. A run with no call leaves them untouched.
- **Greeting editor.** Typed greetings have a **Play** preview, and the voice
  list scrolls so every installed voice can be reached.
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

Pairing survives reboots, and Android's seven-day pairing limit if you let it.
The recorder restarts after a reboot when the phone lets the app run on its own.

## Tasker plugin

Under **Plugin → AM Call Assistant**:

- **Toggle AM Call Assistant** turns recording on or off when the action runs.
  Its *Revert* option puts back the setting that was there before the first
  plugin action.
- **AM Answering Machine** answers the ringing call immediately, just once.
  Choose the delay under Do Not Disturb, the longest message, and a greeting
  with a voice; *Play* speaks it before you save. Fields left at **App's
  Setting** follow whatever you chose in the app at the time the action fires.
  This action does not change the app's own answering settings.

In both actions, Tasker variables are written the way Tasker writes them —
`%BATT`, `%LOC`, `%name` — and are substituted before the action runs. An
unresolvable longest-message variable means no limit.

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
