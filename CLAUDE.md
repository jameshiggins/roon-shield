# Roon Shield

Native Android TV app for the Nvidia Shield: a controller-first **Roon remote** (`com.roonshield`),
Kotlin + Jetpack Compose, talking to the Roon Core via the official Extension API (WebSocket + MOO).
Control-only — it drives existing Roon zones; it does not play audio itself.

Not to be confused with **RoonViz** (`com.jameshiggins.roonviz`, repo `android-roon-visualizer`),
the separate visualizer app that also lives on the Shield.

## Environment

- Shield ("VideoShield"): `192.168.50.195:5555` over wireless adb, wired eth0.
- Roon Core: ROCK NUC at `192.168.50.118`.
- adb: `C:\Android\platform-tools\adb.exe`. Android SDK at `C:\Android`.
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` (set `JAVA_HOME` before gradle).
- `local.properties` already points `sdk.dir` at `C:\Android` (gitignored, machine-specific).

## Build / deploy / test

```powershell
# Build only
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat --no-daemon assembleDebug        # → app\build\outputs\apk\debug\app-debug.apk

# Build + install + launch in one step
.\scripts\deploy.ps1 -ShieldIp 192.168.50.195   # add -SkipBuild to just reinstall

# Unit tests
.\gradlew.bat --no-daemon testDebugUnitTest
```

First install only: enable the extension in a Roon remote → Settings → Extensions → "Roon Shield".

## adb gotchas

- A stale offline `emulator-5562` is often attached; bare `adb shell` targets it and fails
  "device offline". Always `adb connect 192.168.50.195:5555` first and use
  `-s 192.168.50.195:5555` on every command.
- If an install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch between a
  CI-built and locally-built APK), `adb uninstall <pkg>` first — and note uninstalling drops the
  stored Roon pairing token, so the extension must be re-enabled in Roon afterwards.
- If the Shield is asleep (display off → deep Doze), Android blocks *app* network while shell
  `ping`/`nc` still work — the app's WebSocket connects will time out and it looks like a Core
  outage. `adb shell input keyevent KEYCODE_WAKEUP` first (may switch the TV input via CEC).
- `am start -n com.roonshield/.MainActivity --es start_tab nowplaying` deep-links a tab (great for
  driving UI tests over adb).

## Launch behaviour

- Every time the app comes to the foreground (`onStart`) it lands on Now Playing if the active zone
  is playing, otherwise Home (`MainScaffold` in `ui/RoonApp.kt`). A `start_tab` deep link wins for
  that one open, cold (`onCreate`) or warm (`onNewIntent` → `viewModel.requestTab`).
- The app is opened automatically when music starts in the living room — not by this app (a
  sleeping Shield has no app network) but by the Nanoleaf Roon extension service on the PC, over
  adb: see `shield` in `..\nanoleaf-roon-extension\` (`src/shield/launcher.js`).

## Code conventions

- `AGENT_BRIEF.md` is the contract used when parallel agents each build one screen: create only your
  assigned file, keep helpers private, reuse `ui/components/*` and `ui/theme/*`, don't invent
  ViewModel/repo methods, don't add dependencies. Follow it for any new screen work.
- Shared/core files (`ui/RoonApp.kt`, `ui/RoonViewModel.kt`, `roon/RoonRepository.kt`,
  `roon/services/RoonBrowse.kt`, `roon/BrowseSession.kt`) change rarely and deliberately.
- Every screen must be D-pad navigable: give the first item a `FocusRequester` and request focus on
  page load — never leave a screen with no focus anchor.
- Roon's public API has hard limits (no library edits, no PCM access, etc.) — see the
  "API limitations" section of README.md before promising a feature.
