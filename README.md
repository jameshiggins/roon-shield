# roon-shield

A native **Android TV** app for the **Nvidia Shield** that acts as a **Roon remote**, redesigned for
the Shield game controller / D-pad. It controls existing Roon output zones via Roon's official
**Extension API** (WebSocket + MOO protocol) with a Roon-styled, controller-first UI.

> **Scope:** control-only remote for *existing* Roon endpoints. See **API limitations** below.

## Features

- **Discovery & pairing** — finds Roon Cores on the LAN via SOOD; one-time "Enable" in Roon Settings.
  Manual host:port entry fallback. Token persisted for **silent reconnect** on launch.
- **Home** — quick-access tiles + the Roon browse root, all drillable.
- **Browse everything** — Albums, Artists, Genres, Composers, Playlists, Live Radio, plus the generic
  Browse tree — through one unified browser with grid / circular / color-tile / list layouts and
  album/artist/playlist **detail** pages (header art, Play actions, track lists).
- **Action sheet** — selecting a track/album/genre opens a native-style sheet with the actions Roon
  provides: **Play Now / Play Next / Add to Queue / Start Radio / Shuffle** (+ **Add to Listen Later**).
- **Search** — full Roon search with categorized results.
- **Queue** — now-playing pin, stats, play-from-here, transport + shuffle/repeat.
- **Now Playing** — blurred album backdrop, large type, a focusable D-pad scrubber, shuffle / prev /
  play / next / repeat / Roon Radio, and an **Up Next** panel.
- **Listen Later** — app-local bookmarks (persisted); entries re-resolve via Search.
- **Zones & volume** — a footer bar with a zone/volume overlay (switch active zone, adjust volume/mute).
- **Controller mapping** — D-pad focus (per-level auto-focus, focus-grouped rail); global media keys +
  gamepad Y (play/pause), L1/R1 (prev/next), FF/RW; keep-screen-on so the Shield daydream won't steal
  focus while the remote is on screen.

## API limitations (by design of Roon's public API — not app gaps)

- **No "play to this device":** the Shield can't be made a Roon playback endpoint (needs RAAT /
  "Roon Ready", partner-only, not in the public API). The app controls *other* Roon zones.
- **No stream path / format / quality:** the Extension API's Transport `now_playing` object exposes
  **no** codec, sample-rate, bit-depth, or signal-path fields (confirmed in `node-roon-api-transport`).
  So the signal-path / quality readout from the official app can't be reproduced here.

## Architecture

- `app/src/main/java/com/roonshield/roon/` — pure-Kotlin Roon client (JVM-unit-testable): `moo/` MOO
  wire codec + dispatcher, `SoodDiscovery`, `RoonCore` (connect/register/token, generation-guarded
  reconnect), `services/` Transport/Browse/Image, `RoonRepository` (StateFlows), `BrowseSession`
  (isolated per-screen cursors via `multi_session_key`), `BrowseHeuristics`, `RoonPrefs` (DataStore).
- `app/src/main/java/com/roonshield/ui/` — Jetpack **Compose for TV**: `RoonApp` (nav rail + footer +
  zone overlay), `browse/LibraryBrowser` (the one browse surface + action sheet), plus Home / Search /
  Queue / Now Playing / Listen Later / Settings / Connect screens and shared `components/`.
- `app/src/test/` — MOO codec, SOOD parsing, ZoneReducer, model JSON, ReconnectPolicy, MOO dispatch,
  BrowseHeuristics, and ListenLater serialization unit tests.

## Prerequisites

- JDK 17, Android SDK (platform 34, build-tools 34.0.0). This machine: SDK at `C:\Android`.
- A Roon Core on the same network.

## Build, test, deploy

```powershell
./gradlew.bat assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat testDebugUnitTest    # all JVM unit tests
./scripts/deploy.ps1 -ShieldIp <shield-ip>   # adb connect + build + install + launch
```

**First run only:** in any Roon remote open **Settings → Extensions** and click **Enable** next to
**Roon Shield**. The app then connects and lists your zones (subsequent launches reconnect silently).

## Status

Runs on the Nvidia Shield against a live Roon Core: silent reconnect, browse with real library data,
action sheet (Play Next / Add to Queue / Start Radio / Shuffle), Listen Later, now-playing, zones,
persistent footer. A stable single connection (generation-guarded reconnect). See
[`FEATURES_TODO.md`](./FEATURES_TODO.md) for remaining polish.
