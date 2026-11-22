# Contributing

Thanks for helping improve AM Call Assistant.

This is an open-source fork of [JemRec](https://github.com/jemcik/JemRec).
Please keep the attribution in [NOTICE.md](NOTICE.md) intact.

## Before you start

- Use JDK 21. The shell daemon's compiled bytes depend on it.
- Install Android SDK platforms 36 and 37 and build-tools 36.0.0.
- Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the build and test layout.

## Changes

- Open an issue first for anything large, so the approach can be agreed.
- Keep the existing style: small functions, a comment explaining *why* a
  non-obvious choice was made, and a test for behaviour that matters.
- Run `./gradlew test` and `./gradlew lintDebug` before opening a pull request.
  Lint warnings are errors in this project.
- If you change a screen, re-record its screenshots with
  `./gradlew testDebugUnitTest -PrecordScreens=true` and include the new PNGs.
- If you change the daemon protocol, update both `shellserver/` and the app,
  plus the fixtures under `shellserver/tests/fixtures/`.
- Do not commit signing keys, pairing tokens, recordings or build output.

## Reporting bugs

Use the issue templates. Include the phone, Android version and app version;
the Diagnostics → Copy a report output is ideal. Never attach real recordings.
Security problems go to [SECURITY.md](SECURITY.md), not a public issue.

## License

By contributing you agree that your work is licensed under Apache 2.0, the
license of this project and its upstream.
