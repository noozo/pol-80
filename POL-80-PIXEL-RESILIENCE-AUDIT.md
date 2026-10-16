# POL-80 Pixel resilience audit (ANY Pixel, stock OS, no Pedro apps)

**Scope:** `/workspace/minimal-launcher` source (built ship = **0.38**; gradle prepared **versionCode 39 / 0.39-pixel-resilience**).  
**Date:** 2026-10-01 PT  
**Goal:** Crash / hard-fail risks when WA / Slack / Proton / custom dock apps are absent; permissions denied; NLS off.

## Verdict

**MainActivity does not crash solely because Pedro apps are missing.** Status chips, dock, and pins already degrade in most paths. Remaining risks were mostly **unchecked system-service / PackageManager / widget / torch / installer** edges — several quick wins applied in source as **0.39-pixel-resilience** (APK not built in this pass).

---

## Ranked crash / hard-fail risks

### P0 — can take down home (or torch path) on stock Pixel

| # | Risk | Where | Fresh-Pixel trigger | Status |
|---|------|-------|---------------------|--------|
| 1 | `CameraManager.cameraIdList` / characteristics throw outside try in torch resolve | `FlashlightController` | Tap dock torch (or shake after grant) while camera busy / access denied mid-call | **Hardened** (nullable CM, try around id list + chars + toggle) |
| 2 | `host.createView` / RemoteViews inflate throws | `WidgetHostHelper.createView` → `WidgetSlot` `AndroidView` | Stale widget id after restore / provider missing | **Hardened** (try → null; empty frame) |
| 3 | `queryIntentActivities` / `loadIcon` throws on one bad ResolveInfo | `MainActivity.queryLaunchableApps` | Rare OEM / corrupt package in launcher query | **Hardened** (outer + per-item try; default icon) |
| 4 | `getLaunchIntentForPackage` null / unresolved → `startActivity` | `MainActivity.launchApp` | Uninstalled pin/dock mid-session | **Hardened** (try + resolve guard + NEW_TASK) |

### P1 — user-initiated crash / ANR-adjacent

| # | Risk | Where | Trigger | Status |
|---|------|-------|---------|--------|
| 5 | `UpdateChecker.launchInstaller` no try | OTA Update flow | No package installer / ActivityNotFound | **Hardened** (returns Boolean; toast) |
| 6 | Unsafe `as CameraManager` / `SensorManager` / `MediaSessionManager` | Flashlight / Shake / NowPlaying ctors | Theoretical null service | **Hardened** (`as?` + null guards) |
| 7 | `RuntimeShader(AGSL)` compile throw | `CrtBootEffect.crtHomeWarp` | Odd GPU / API 33+ shader fail | **Hardened** (try → null → fish-eye fallback) |
| 8 | `setRequestedOrientation` throw | `applyPortraitLock` | Multi-window / tablet edge | **Hardened** (try/catch) |

### P2 — no crash, but broken / confusing on stock Pixel (no Pedro apps)

| # | Issue | Behavior today | Recommended |
|---|-------|----------------|-------------|
| 9 | Status chips assume WA / Proton / Slack | Counts `—` or `0` without NLS/apps; **tap → Play Store** via `StatusApps` | OK degrade; optional: hide chips or show “Install” only when package missing |
| 10 | NLS off by default | WA/PR/SL null → `—`; `NLS?` hint | Keep; don’t auto-open settings |
| 11 | Auto-request `READ_CALL_LOG` + `READ_SMS` on first `statusTick` | Deny → `—` (StatusMonitor safe) | Keep; consider defer until user taps CALL/SMS |
| 12 | Empty dock / empty home | Defaults blank (no hardcoded dock packages) | **Good for any Pixel** |
| 13 | Ghost pins/dock keys for uninstalled apps | Empty cell (null `appByKey`) — no crash | Soft-prune missing keys on app reload (optional) |
| 14 | Wallpaper plate | `SecurityException` → black plate | Already OK |
| 15 | Calendar / location / contacts | Request on expand/search; deny → `—` / empty | Already OK |
| 16 | Portrait lock default ON | Fine on phone Pixel; landscape via ⋮ | Already OK |
| 17 | OTA auto-check | Network/JSON fail silent on auto | Already OK; paste.rs primary in 0.39 gradle |

### Already resilient (no change needed for crash)

- **`StatusApps.safeStart`**: `resolveActivity` + try/catch; market/https fallbacks for WA/Proton/Slack/dialer/SMS.  
- **`StatusMonitor`**: permission gates + `SecurityException` → null.  
- **`BareNotificationListener`**: missing packages → count 0; `activeNotifications` try.  
- **`ContactsRepo` / dial / WA intents**: try/catch + toast.  
- **`NowPlayingMonitor`**: SecurityException on sessions; openMediaApp runCatching.  
- **`LayoutLockStore`**: EncryptedSharedPreferences → plaintext fallback.  
- **`WeatherRepo`**: no location → Lisbon; fetch errors → cache/null.  
- **Manifest `<queries>`**: MAIN/LAUNCHER + dial/sms/tel/market + WA/Slack/Proton/dialer/Messages packages.

---

## Hardening applied in source (v0.39-pixel-resilience)

- `FlashlightController.kt` — nullable CameraManager; safe cameraIdList / characteristics  
- `WidgetHostHelper.createView` — try/catch → null  
- `UpdateChecker.launchInstaller` — try/catch → Boolean  
- `ShakeDetector` / `NowPlayingMonitor` — safe service casts  
- `MainActivity` — safe `queryLaunchableApps`, `launchApp`, `applyPortraitLock`; installer failure toast  
- `CrtBootEffect` — RuntimeShader create in try  
- `app/build.gradle.kts` — `versionName = "0.39-pixel-resilience"` (versionCode **39** already)

**Not done:** APK assemble / Drive / paste.rs publish; ghost-pin prune; status-chip UX hide-when-missing.

---

## Suggested follow-ups (non-crash)

1. Soft-prune home pins + dock slots whose package is gone after `loadApps()`.  
2. Defer CALL/SMS permission until first chip tap (less scary first-run on family Pixel).  
3. Treat WA/PR/SL as optional modules in UI when `!StatusApps.isInstalled`.  
4. Build + ship 0.39 APK; refresh paste.rs `latest.json`.
