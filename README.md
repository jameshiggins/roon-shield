# roon-shield

A native **Android TV** app for the **Nvidia Shield** that acts as a **Roon remote**, redesigned for
the Shield game controller / D-pad. It controls existing Roon output zones via Roon's official
**Extension API** (WebSocket + MOO protocol) — browse, search, now-playing, transport, and queue.

> **Scope:** control-only remote for *existing* Roon endpoints. It cannot make the Shield itself a
> Roon playback zone (that needs RAAT / "Roon Ready," which is partner-only and not in the public API).

See [`PLAN.md`](./PLAN.md) for the design.

## Features (v1)

- **Discovery & pairing** — finds Roon Cores on the LAN via SOOD; one-time "Enable" in Roon Settings.
  Manual host:port entry as a fallback. Token is persisted for silent reconnect.
- **Now Playing** — album art, track/artist/album, seek bar, play/pause, prev/next, ±10s seek,
  volume up/down, mute.
- **Browse & Search** — the full Roon library hierarchy (artists/albums/genres/playlists…) and search;
  select items to drill in and play (Play Now / Queue etc. via Roon's action lists).
- **Queue** — view upcoming tracks and play from any point.
- **Zones** — pick the active output zone.
- **Controller mapping** — D-pad focus navigation; media keys + gamepad Y (play/pause), L1/R1
  (prev/next), FF/RW keys all handled globally.

## Architecture

- `app/src/main/java/com/roonshield/roon/` — pure-Kotlin Roon client (JVM-unit-testable):
  `moo/` MOO wire codec + dispatcher, `SoodDiscovery`, `RoonCore` (connect/register/token),
  `services/` Transport/Browse/Image, `RoonRepository` (StateFlows).
- `app/src/main/java/com/roonshield/ui/` — Jetpack **Compose for TV** UI + `RoonViewModel`.
- `app/src/test/` — MOO codec + SOOD parsing unit tests.

## Prerequisites

- JDK 17, Android SDK (platform 34, build-tools 34.0.0). This machine: SDK at `C:\Android`
  (see `local.properties`).
- A Roon Core running on the same network.

## Build & test

```powershell
./gradlew.bat assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat testDebugUnitTest    # MOO + SOOD unit tests
```

## Sideload to the Shield (wireless ADB)

1. On the Shield: Settings → Device Preferences → About → tap **Build** 7× (unlocks Developer
   options), then enable **Network debugging**. Note the Shield's IP.
2. From this repo:

   ```powershell
   ./scripts/deploy.ps1 -ShieldIp <shield-ip>
   ```

   This runs `adb connect`, builds, installs, and launches the app.
3. **First run only:** in any Roon remote, open **Settings → Extensions** and click **Enable** next to
   **Roon Shield**. The app then connects and lists your zones.

## Status

v1 implemented and building; APK produced; unit tests green. End-to-end verification against a live
Roon Core (pair → browse → play → transport/queue) is the remaining manual step — see
[`PLAN.md`](./PLAN.md) "Verification".
