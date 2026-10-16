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
⋮ → Check updates (and a periodic background check) reads every mirror and takes the **highest versionCode**:
- GitHub: `https://api.github.com/repos/noozo/pol-80/releases/latest` (`latest.json` asset, else the `vNN`/`v0.NN` tag + APK asset), from 0.50
- Google Doc export + Drive `latest.json` (legacy mirrors), plus old paste.rs fallbacks

The APK is downloaded (redirects followed), checked against the `sha256` in latest.json, then handed to the system installer.

`latest.json` format:
```json
{"versionCode": 50, "versionName": "0.50-github-ota", "apkUrl": "https://…/POL-80-0.50.apk", "sha256": "…", "notes": "…"}
```

## Build
```
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug
```
