# Notices and attribution

## Upstream: JemRec

AM Call Assistant is a fork of **JemRec**, created by Yevhen Yemchynskyi
(<https://github.com/jemcik/JemRec>), licensed under the Apache License,
Version 2.0. The original copyright notice is retained in [LICENSE](LICENSE) as
Apache 2.0 section 4 requires. Most of the code, design and documentation here
comes from that project; the changes made for this fork are described in
[README.md](README.md) and itemised in [CHANGES.md](CHANGES.md).

  Copyright 2026 Yevhen Yemchynskyi - JemRec
  Copyright 2026 Adiraimaji - AM Call Assistant fork

AM Call Assistant itself is licensed under the Apache License, Version 2.0.

## Changes made by this fork

This is a modified version of JemRec. Apache 2.0 section 4(b) requires modified
files to carry notices stating that they were changed; the Markdown files
changed here say so at the top and point to the complete, grouped record in
[CHANGES.md](CHANGES.md). That record covers the changes made when the fork was
created and the later additions: answering-machine profiles, the Calling
Machine, three Tasker actions with saved-profile dropdowns, audio-file
greetings, searchable voice lists and the millisecond greeting delay.

## Logo

`icon/icon.svg` and the launcher/tile artwork derived from it are the fork's
logo, contributed by the maintainer and released under Apache 2.0 with this
project. The handset shape is the standard Material "call" icon (Apache 2.0,
see "Material icons" below).

## Third-party notices

The notices below are retained from the upstream project and still apply.

## libadb-android

Copyright 2021 Muntashir Al-Islam.
Dual licensed GPL-3.0-or-later **OR** Apache-2.0. AM Call Assistant takes it under
**Apache-2.0**, which the author explicitly offers ("Use whatever license you
need for your project").

<https://github.com/MuntashirAkon/libadb-android>

Note the library's own caveat, repeated here rather than buried: it has never
had a security audit.

Pulled in transitively: `spake2-android` (BoringSSL's SPAKE2, Apache-2.0 /
OpenSSL licence) for the pairing exchange.

## Bouncy Castle

Copyright 2000-2025 The Legion of the Bouncy Castle Inc. MIT-style licence.
Used only to assemble the ASN.1 of the self-signed X.509 certificate that
carries this app's ADB public key. <https://www.bouncycastle.org/licence.html>

## Conscrypt

Copyright The Android Open Source Project, Apache-2.0. Bundled because SPAKE2
binds the pairing exchange to the TLS channel with RFC 5705 exported keying
material, and the platform's own Conscrypt is behind the hidden-API blocklist -
so without this dependency `exportKeyingMaterial` simply does not exist and
pairing cannot complete. Bundling it is what lets AM Call Assistant avoid hidden-API
exemptions altogether. <https://github.com/google/conscrypt>

## scrcpy

Copyright 2018 Genymobile, Apache-2.0. Vendored, as source, in `shellserver/`:
an audio-only fork of `scrcpy-server` at tag **v4.1**, 23 of its 90 server
files, built from source by `shellserver/build.sh`. Package names are left as
`com.genymobile.scrcpy` so that a diff against upstream is a diff.

Every modification is listed in [shellserver/PATCHES.md](shellserver/PATCHES.md)
and marked `PATCHED:` at the site. The full licence text is in
`shellserver/LICENSE-scrcpy`.

<https://github.com/Genymobile/scrcpy>

## Fonts - the Deck and Multitrack themes

Four typefaces, bundled unmodified in `app/src/main/res/font/` from
<https://github.com/google/fonts> (downloaded 2026-10-23), each under the SIL
Open Font License 1.1. The licence text of each travels in the app, in
`app/src/main/assets/licenses/`. The website wears the same looks and serves
the same files, unmodified, from `docs/fonts/` with each licence text beside
its font (`tools/build_site.py` copies both).

- **Doto** (`doto.ttf`, the LCD): Copyright 2024 The Doto Project Authors.
  <https://github.com/oliverlalan/Doto>
- **Share Tech Mono** (`share_tech_mono.ttf`, Deck's engraved labels):
  Copyright (c) 2012 Carrois Type Design, Ralph du Carrois, with Reserved
  Font Name "Share" - which is why it is used as it came, never cut down.
- **JetBrains Mono** (`jetbrains_mono.ttf`, Multitrack): Copyright 2020 The
  JetBrains Mono Project Authors. <https://github.com/JetBrains/JetBrainsMono>
- **Space Grotesk** (`space_grotesk.ttf`, Multitrack): Copyright 2020 The
  Space Grotesk Project Authors. <https://github.com/floriankarsten/space-grotesk>

## F-Droid logo - the website only

`docs/fdroid-logo.svg`, on the website's "Get it on F-Droid" button, is
F-Droid's own logo, unmodified. Copyright 2012 William Theaker, 2013 Robert
Martinez, 2015 Andrew Nayenko. Dual licensed CC-BY-SA 3.0 Unported **OR**
GPL-3.0-or-later; used here under **CC-BY-SA 3.0**. It is not part of the app.
<https://gitlab.com/fdroid/artwork>

## Material icons - the website only

The icons beside the website's reasons and privacy points are Google's Material
Icons, filled - the set the app's own icons come from - their shapes copied from
`src/<group>/<name>/materialicons/24px.svg` in
<https://github.com/google/material-design-icons> (read 2026-10-27), under the
Apache License 2.0.

## GitHub mark - the website only

The GitHub mark on the website's APK button is GitHub's, as drawn in Primer
Octicons (`mark-github`, MIT, Copyright GitHub, Inc.), used to link to GitHub
as GitHub's logo guidelines allow. <https://github.com/primer/octicons>

## ShizuCallRecorder - reference only, no code taken

`kitsumed/ShizuCallRecorder` is GPL-3.0. It is a **reference for the approach
only**; no code from it is copied into AM Call Assistant, which is why AM Call Assistant can be
Apache-2.0. <https://github.com/kitsumed/ShizuCallRecorder>
