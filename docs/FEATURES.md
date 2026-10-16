# POL-80 — minimal Android launcher

Ultra-minimal home-screen replacement for Pixel (and other Android) phones.
Kotlin + Jetpack Compose. No ads, no network, no accounts.

Release metadata: **versionCode 38 · **versionName 0.38-portrait-lock** · package/applicationId `com.bare.launcher` (kept unchanged for in-place upgrades).

- **App name:** POL-80  
- **Package:** `com.bare.launcher`  
- **minSdk:** 26 · **targetSdk / compileSdk:** 35  

## Features

- **Multi-page home** — horizontal pager with page-indicator dots; direction-locked swipes so a clear horizontal gesture changes page on first try. Each page has its own pins + widgets. Dock stays fixed across pages. Add/remove pages in edit mode (`+` / `−` by the dots); drag an item past the side edge to move it onto (or create) an adjacent page.
- **Move icons & widgets** — in edit mode, long-press-drag pinned apps and widgets to reorder on the current page (ordered slot index). Cross-page move via side-edge drag. Layout persists via SharedPreferences.
- **Pinned home grid** — home shows only apps you pin (empty until you add some). Header pencil → edit mode → pin picker with **Done**. Pins persist per page. Outside edit mode, long-press enters edit mode (does **not** unpin). Unpin only via the red `−` badge in edit mode.
- **App drawer** — swipe up or tap the Apps control to open the full launchable-app list.
- **CRT tube warp boot** — ~525ms power-on warps the **entire home layer** (wallpaper plate + header + icons + dock): AGSL barrel/pincushion + chromatic via `RuntimeShader`/`RenderEffect` on API 33+ (fish-eye scale + roll/jitter fallback on older); phosphor tint, scanlines, vignette, fringe, flicker; then settles flat. Readable dark plate + neon rim for `POL-80 // ONLINE`. Cold start + throttled Home (~3.5s). Header stamp: `POL-80 0.38`.
- **System Home reset** — swipe-up Home / MAIN+HOME always restores clean home: closes search, apps drawer and pickers, status drawer, edit mode, and header menus (pins/wallpaper untouched).
- **Fuzzy search** — swipe down on home (or long-press Apps) for a search overlay; mixed recent (~8) apps + people (Phone/WhatsApp) under the bar (tap re-runs action, CLEAR clears both); two columns (LEFT apps / RIGHT people) filtered by the same query (no `@` prefix); contact rows with Phone (`tel:`) + WhatsApp; requests `READ_CONTACTS` gracefully; dismiss via Android system Home (swipe-up-from-bottom) / Back / close — no overlay drag that steals list scroll; 300ms reopen cooldown.
- **Flashlight on dock** — always-present dock control (next to Apps) toggles CameraManager torch; Apps remains reachable.
- **Night phosphor** — after sunset (Open-Meteo sunrise/sunset from weather fetch; else ~19:00–07:00 Europe/Lisbon), grid + dock icons remap to mono green/cyan phosphor and labels go cyan. ⋮ menu cycles Auto (default) / On / Off. Edit-mode badges/drag unchanged.
- **Status digests** — long-press WA / Slack / SMS / CALL / PR chips → sheet with last ~3 notification titles from NotificationListener; tap opens the app (PR → Proton Mail); empty state if none.
- **Shake flashlight** — optional (⋮ → Shake flashlight: Off/On), **off by default**. Shake toggles the same CameraManager torch as the dock flashlight icon (shared controller; no fighting).
- **Lock layout** — optional PIN (4–8 digits, EncryptedSharedPreferences) and/or biometric before entering edit mode (pencil / long-press). Unlock once per edit session; re-locks when edit ends or Home resets.
- **Portrait lock** — default ON (portrait only via `Activity.setRequestedOrientation`). ⋮ → Portrait lock: On/Off unlocks landscape; preference persists.
- **Weather HI/LO** — header shows today's max/min °C via Open-Meteo (device location or Lisbon 38.72,-9.14 default); ~30 min cache.
- **Bottom dock** — up to 5 dock slots (separate from home pins), with placeholders for empty slots. Long-press a slot to assign/clear. Persisted separately; shared across pages.
- **Wallpaper** — transparent home so the system wallpaper shows through; light scrims on header/dock only.
- **Now playing** — when a MediaSession is active (playing/paused), a strip under the status metrics shows title (+ artist), prev/play-pause/next via MediaController; tap title opens the media app. Uses MediaSessionManager with the existing NotificationListener ComponentName (same access as WA/PR).
- **Style D Miami dusk chrome** — palm/dusk bitmap behind header + dock, light readable scrim (~20–40%), cyan–magenta neon edges, dusk coral underline, cyan page-dot bloom; icon labels bright `#F5F0FF` all-caps. Wallpaper unchanged.
- **Tech header** — compact monospace `SYS` time + date + version stamp; icon metrics (phone / SMS / WhatsApp / mail / Slack) with counts; tap to open each app; amber (≥1) / red (≥5) value colors; tap header to expand drawer (battery, network, next calendar event).
- **OTA updates** — header ⋮ → **Check for updates**; auto-check ~every 15 minutes while the launcher UI is running. Fetches `POL-80-latest.json` over HTTPS; if remote `versionCode` > local, shows **Update** / **Later**. Update downloads APK to cache, verifies SHA-256, launches the package installer (`REQUEST_INSTALL_PACKAGES`). Later snoozes until a newer versionCode or 24h.
- **Widgets** — custom preview picker (grouped by app, `loadPreviewImage`) places on the current page; system picker fallback; remove in edit mode.

## Tech status counters

Header shows icon + count for each channel (bright icons on palms chrome), e.g. phone `2` · SMS `1` · WA `5` · mail `3` · Slack `4`:

| Icon | Source | Permission / access |
|------|--------|---------------------|
| **Phone** | Missed calls (`CallLog`, `NEW=1`) | `READ_CALL_LOG` (runtime) |
| **SMS** | Unread non-archived conversations (`Telephony.Threads`, `READ=0`, `ARCHIVED=0`; SMS+MMS fallback) | `READ_SMS` (runtime) |
| **WhatsApp** | Active notifications from `com.whatsapp` / `com.whatsapp.w4b` | Notification access |
| **Mail (Proton)** | Active notifications from Proton Mail (`ch.protonmail.android`, …) | Notification access |
| **Slack** | Active notifications from `com.Slack` / `com.tinyspeck.slackandroid` | Notification access |

- Denied / unavailable → that metric shows `—` (muted).
- Tap phone → dialer / call log; SMS → Messages; WhatsApp → WhatsApp; mail → Proton Mail; Slack → Slack (Play Store fallback if missing).
- Value colors: muted at 0/—; **amber** at ≥1 (`STATUS_AMBER_AT`); **red** at ≥5 (`STATUS_RED_AT`) — constants in `StatusMonitor`.
- Expandable drawer (tap header / ▼): battery % + charging, network (Wi‑Fi SSID or Mobile), next Calendar event (requests `READ_CALENDAR` on expand). Collapse via second tap, outside tap, or Back.
- WhatsApp, Proton Mail, and Slack are **notification-based only** (NotificationListenerService). POL-80 does **not** scrape those apps’ storage.
- If notification access is off, a tiny `NLS?` hint appears under the date; tap it to open **Notification access** settings.

### Grant Notification access

1. Install / open POL-80 (or set it as Home).
2. Tap **`NLS?`** in the header, **or** go to  
   **Settings → Apps → Special app access → Notification access → POL-80 → Allow**.
3. Return to POL-80; WhatsApp / Proton / Slack should populate from active notifications.

### Grant Call Log / SMS

On first launch POL-80 requests `READ_CALL_LOG` and `READ_SMS`. You can also tap the status line later to re-request missing runtime permissions, or enable them under **Settings → Apps → POL-80 → Permissions**.


## OTA updates (Drive + HTTPS)

Hosted manifest **`POL-80-latest.json`** (also on Google Drive):

| Field | Meaning |
|-------|---------|
| `versionCode` / `versionName` | Remote app version |
| `apkDriveFileId` | Drive file id for the APK |
| `apkUrl` | Direct HTTPS APK URL (preferred for download) |
| `sha256` | Hex digest of the APK |
| `notes` | Dialog blurb |

**OTA Doc (primary latest.json, share Anyone with the link):**  
`https://docs.google.com/document/d/1Qn_N7-ZIB3yZzOSiNKzIU0AT3ZzTJxUb3PN2itGXPzg/export?format=txt`

**Drive JSON file id (this release):** `1PvDBfBdkDj4y_CuMFjt-I7XJwwpPx9_f`  
Drive link: https://drive.google.com/file/d/19QDSpUe1MW-rGPRdpj0JtCL5FeWHc9Oi/view?usp=drivesdk

**How the app fetches JSON (in order) — v0.35+:**
1. `BuildConfig.UPDATE_JSON_URL` — Google Doc export (content-updatable):  
   `https://docs.google.com/document/d/1Qn_N7-ZIB3yZzOSiNKzIU0AT3ZzTJxUb3PN2itGXPzg/export?format=txt`
2. Drive usercontent / uc? for `UPDATE_JSON_DRIVE_FILE_ID` (must be **Anyone with the link**)
3. Gist + GitHub raw fallbacks (note: GitHub Soft404 for anonymous as of 2026-09-30)

APK Drive (this release): https://drive.google.com/file/d/19QDSpUe1MW-rGPRdpj0JtCL5FeWHc9Oi/view?usp=drivesdk  
sha256: `cebfcfc836736f0b55869df9870e2300b883ce67f261c7281797eeea5a94eeb2`

To publish a new build: upload APK + `POL-80-latest.json` to Drive, update the gist/GitHub mirrors with the new `versionCode` / `sha256` / ids, bump `versionCode` in `app/build.gradle.kts`.

## Build

```bash
JAVA_HOME=/workspace/.tools/jdk-17 ./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (release copy: `POL-80-0.38.apk`)

## Install / set as Home

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then: **Settings → Apps → Default apps → Home app → POL-80**

## Project layout

```
minimal-launcher/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/bare/launcher/
│   │   ├── MainActivity.kt              # Compose UI, pager, drag-reorder, sheets
│   │   ├── LauncherPrefs.kt             # multi-page layout / dock persistence
│   │   ├── WidgetHostHelper.kt          # AppWidgetHost pick/bind/configure
│   │   ├── StatusMonitor.kt             # CALL / SMS + battery / net / calendar
│   │   ├── StatusApps.kt                # tap-to-open Phone/SMS/WA/Proton/Slack
│   │   ├── BareNotificationListener.kt  # WA / PR / Slack via NotificationListenerService
│   │   ├── NowPlayingMonitor.kt         # MediaSessionManager now-playing
│   │   ├── WeatherRepo.kt               # Open-Meteo HI/LO + 30min cache
│   │   ├── ContactsRepo.kt              # READ_CONTACTS fuzzy people + Phone/WA intents
│   │   ├── FlashlightController.kt      # CameraManager torch toggle
│   │   ├── UpdateChecker.kt             # Drive/HTTPS OTA latest.json + APK install
│   │   └── Theme.kt                     # Style D Miami dusk color tokens
│   └── res/
└── README.md
```

## Privacy

- Internet for Open-Meteo weather HI/LO and OTA update checks (latest.json + APK download; no accounts / analytics)  
- Camera permission only for dock flashlight torch  
- No accounts / analytics / ads  
- Reads launchable activities + app widgets via PackageManager / AppWidgetManager  
- Optional: Call Log + SMS for header counts; Contacts for search people column; NotificationListener for WhatsApp / Proton Mail / Slack badge counts only (active notifications, no message body scraping / no app storage access)

## License

Do what you want with it — personal / educational use.
