# POL-80

A HUD / CRT-phosphor home-screen launcher for Pixel phones (Kotlin + Jetpack Compose).
Package `com.bare.launcher`, minSdk 26, targetSdk 35.

Compact glanceable header (BAT · NET · RF, CAL), signal weather, context chip, missed-call radar,
home stamp, charge ritual, edge clock, rain mode, night phosphor, fuzzy search, multi-page pins and dock.
See [docs/FEATURES.md](docs/FEATURES.md) for the long feature list (partly historical).

## Install (sideload)
1. Download `POL-80-<version>.apk` from [Releases](https://github.com/noozo/pol-80/releases/latest).
2. Open it on the phone and allow "Install unknown apps" for your browser / files app.
3. Press Home and choose POL-80 as the default launcher.

All builds are signed with the same certificate, so new versions install over old ones in place.

## OTA updates
From 0.51, ⋮ → Check updates (and a periodic background check) reads **only** GitHub:
`https://api.github.com/repos/noozo/pol-80/releases/latest` (anonymous). It uses the release's `latest.json`
asset, falling back to the `v0.NN` tag + `sha256: …` in the release notes + the APK asset.
The old Google Doc / Drive / paste.rs mirrors were removed (0.50 and earlier still read them).

The APK is downloaded (redirects followed), checked against the `sha256`, then handed to the system installer.
To ship: create a release `v0.NN` marked latest with `POL-80-0.NN.apk` and `latest.json`.

`latest.json` format:
```json
{"versionCode": 51, "versionName": "0.51-gh-only", "apkUrl": "https://…/POL-80-0.50.apk", "sha256": "…", "notes": "…"}
```

## Build
```
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug
```
