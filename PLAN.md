# Roon Remote for Nvidia Shield — Implementation Plan

## Context

Build a native Android TV app, sideloaded onto an Nvidia Shield over wireless ADB, that acts as a
**Roon remote** — feature-parity with Roon's official Remote app (browse, search, now-playing,
transport, queue) but redesigned for the Shield **game controller / D-pad** instead of touch.

The app talks to the Roon Core over Roon's **official Extension API**: a WebSocket + "MOO" RPC
protocol offering the exact services a remote needs — **Transport** (zones, play/pause/next/prev/seek,
volume), **Browse** (the same hierarchical library tree the phone app navigates, plus search),
**Image** (album art), and **Status** (so the app shows up in Roon Settings). There is no official
Kotlin SDK, so the MOO client is reimplemented natively in Kotlin. This path is sanctioned and stable
across Roon updates; the only user-visible cost is a one-time **"Enable"** of the extension in
Roon Settings → Extensions on first connect.

**Expectation set with user:** near-full remote *functionality*, but a Roon-*styled* UI of our own
(not a pixel-identical clone of Roon's proprietary UI). Confirmed scope: Transport + Now Playing,
Library browse + search, Queue management. Zone selection + volume are included as required baseline
(you must pick a zone to control anything); zone *grouping* is deferred.

**Hard limitation (confirmed OK with user):** this is a **control-only remote** for *existing* Roon
output zones. The Extension API **cannot make the Shield itself a Roon playback endpoint** — that
requires RAAT / "Roon Ready," which is partner-only under NDA and not in the public API. So "play to
this device" (the Shield's own HDMI/audio out) is out of scope and infeasible via this path. The user
has other Roon endpoints and wants the Shield purely as a controller for those.

## Tech stack

- **Kotlin**, Android Gradle (Kotlin DSL), JDK 17.
- **Jetpack Compose for TV** — `androidx.tv:tv-material` for TV components, plus standard
  `androidx.compose.foundation` `LazyColumn`/`LazyRow` (the old `androidx.tv:tv-foundation` /
  `TvLazyColumn` were folded back into Compose Foundation — confirm current library state at
  implementation time and don't pin the dead artifact).
- **OkHttp** `WebSocket` for the MOO transport; **kotlinx-coroutines** + `Flow`/`StateFlow` for state.
- **Coil** for image loading (backed by our Roon Image API fetch), **DataStore (Preferences)** for persistence.
- `minSdk 26`, `targetSdk 34` (Shield runs Android 11 = API 30). Single `:app` module to start.

## Architecture

Two layers: a pure-Kotlin **Roon client core** (no Android UI deps, JVM-unit-testable) and a
**Compose-for-TV UI** on top, bridged by a `RoonRepository` exposing `StateFlow`s.

```
app/src/main/java/.../roonshield/
  roon/
    moo/Moo.kt            # MOO wire codec + request/response/subscription dispatch over OkHttp WebSocket
    moo/MooMessage.kt     # parse/encode "MOO/1 <VERB> <name>\n<headers>\n\n<body>" (1 WS frame = 1 msg)
    RoonCore.kt           # connection + registration handshake, token persistence, core_paired/unpaired
    SoodDiscovery.kt      # UDP multicast (port 9003) SOOD query -> discover Core host/port; manual-entry fallback
    services/Transport.kt # subscribe_zones, control(), seek(), change_volume(), mute(), change_now_playing
    services/Browse.kt    # browse()/load() over hierarchy+item_key; action lists; search input
    services/Image.kt     # get_image(image_key, scale/size) -> ByteArray for Coil
    services/Status.kt    # set_status("Connected", is_error=false) so app appears in Roon Settings
    RoonRepository.kt     # single source of truth; StateFlows: connection, zones, activeZone, nowPlaying, browseStack, queue
    model/*.kt            # Zone, NowPlaying, BrowseItem, BrowseList, QueueItem data classes
  ui/
    RoonApp.kt            # top-level nav scaffold (nav rail: Now Playing / Browse / Search / Queue / Zones)
    connect/ConnectScreen.kt      # discovery list + manual IP entry + "waiting for authorization" state w/ instructions
    nowplaying/NowPlayingScreen.kt# big art, title/artist/album, seek bar, transport row, volume, active-zone chip
    browse/BrowseScreen.kt        # TvLazyColumn/grid driven by Browse API; drill-down; action-list dialog
    search/SearchScreen.kt        # on-screen text entry -> Browse search hierarchy
    queue/QueueScreen.kt          # upcoming tracks; play-from-here / remove
    zones/ZonePickerScreen.kt     # choose active output zone
    theme/                        # Roon-like dark theme, focus-scaling for controller
    input/ControllerInput.kt      # centralized key mapping (see below)
  MainActivity.kt         # ComponentActivity hosting RoonApp; wires Repository lifecycle
  AndroidManifest.xml     # LEANBACK_LAUNCHER, uses-feature leanback/touchscreen(false), INTERNET, TV banner
```

### Roon client core — the critical piece

1. **MOO codec** (`moo/MooMessage.kt`, `moo/Moo.kt`): each WebSocket frame is exactly one MOO message.
   Encode/parse `MOO/1 REQUEST <service>/<op>` with headers `Request-Id`, `Content-Type: application/json`,
   `Content-Length`, blank line, then JSON body. Responses arrive as `RESPONSE` / `CONTINUE` / `COMPLETE`
   carrying the same `Request-Id`. Maintain an incrementing request-id counter, a pending-request map, and
   a subscription registry (`subscribe_*` sends a `subscription_key`; the Core streams `CONTINUE` updates
   until `unsubscribe_*`). This is verified against the format returned by `moo.js` (documented in the plan
   research). Round-trip encode/parse gets JVM unit tests.

2. **Discovery** (`SoodDiscovery.kt`): send a SOOD query via UDP multicast on port **9003**, collect Core
   replies (service id, host, port). Always offer **manual host:port entry** as a fallback (Shield may be on
   a segmented network). Persist the chosen Core.

3. **Registration / pairing** (`RoonCore.kt`): after WebSocket connect, call the registry service
   (`com.roonlabs.registry:1` → `info`, then `register`) sending extension metadata
   (`extension_id`, `display_name`, `display_version`, `publisher`, `email`, `website`), the
   `required_services` and `provided_services` (Status, Ping). **Pin the exact service versions** —
   transport is `com.roonlabs.transport:2`; browse/image/status are `:1`; registry is `:1`. A wrong
   version is a silent registration failure. If a saved
   **token** exists, send it to reconnect silently; otherwise the Core returns a token after the user hits
   **Enable** in Roon Settings — persist it via DataStore. Fire `core_paired` (wire up service clients +
   subscribe zones) / `core_unpaired` (teardown). Auto-reconnect with backoff on socket drop.

4. **Service clients**: `Transport` subscribes to zones (drives Now Playing + Queue + active-zone state),
   and issues `control` (play/pause/playpause/next/previous/stop), `seek`, `change_volume`, `mute`.
   `Browse` implements the load/browse loop over `hierarchy` + `item_key`, returning lists of `BrowseItem`s
   and surfacing per-item **action lists** (Play Now, Add Next, Queue, Start Radio). Search is a Browse
   input hierarchy. `Image` fetches album art bytes for Coil. `Status` sets the extension's status line.

### Controller / D-pad optimization (`ui/input/ControllerInput.kt`)

Central key map applied via `onKeyEvent` + Compose focus management:

- **D-pad + focus** for all navigation; **A / DPAD_CENTER** = select/activate; **B / Back** = up one
  browse level (pop `browseStack`) or exit.
- **Y** = global play/pause; **X** = open action list (context menu) on focused item.
- **L1 / R1** (or media prev/next keys) = previous/next track.
- On **Now Playing**: Left/Right = seek ±10s, Up/Down = volume ±; on lists, Up/Down = navigate.
- Also handle Android **media keys** (`KEYCODE_MEDIA_PLAY_PAUSE`, etc.) so Shield/remote media buttons work.
- Large focus-scaled cards, visible focus ring, generous spacing — TV-legible from the couch.

### Persistence (DataStore)

`{ coreHost, corePort, token, pairedCoreId, lastActiveZoneId }` — enables silent reconnect and restoring
the last-controlled zone on launch.

## Build & wireless sideload workflow (Shield)

1. On the Shield: Settings → Device Preferences → About → tap Build 7× (Developer options), enable
   **Network debugging**; note the Shield IP.
2. From the dev machine: `adb connect <shield-ip>:5555` (a `scripts/deploy.ps1` will wrap
   connect + `./gradlew installDebug` + launch).
3. `AndroidManifest.xml`: `MainActivity` intent-filter with `LEANBACK_LAUNCHER`, `uses-feature
   android.software.leanback` and `android.hardware.touchscreen required=false`, `INTERNET` permission,
   and a TV `android:banner`.

## Verification (end-to-end)

- **Unit (JVM, `./gradlew test`):** MOO encode/parse round-trips (REQUEST/RESPONSE/CONTINUE/COMPLETE,
  headers, Content-Length framing), subscription key handling, SOOD reply parsing.
- **Integration against a real Core (requires a running Roon Core on the LAN):**
  1. Install to Shield over wireless ADB; launch from the TV launcher row.
  2. App discovers/connects → shows "waiting for authorization"; open **Roon → Settings → Extensions**,
     confirm the app is listed, click **Enable** → app transitions to connected and lists zones.
  3. With the **controller only**: pick a zone → Browse to an album → open action list → **Play Now** →
     confirm audio plays on that zone and **Now Playing** updates (art, title, progress) via the zone
     subscription.
  4. Test **pause / next / prev / seek / volume** from the transport row and hardware media keys.
  5. Open **Queue** → verify upcoming tracks, play-from-here, remove.
  6. **Search** → type via on-screen entry → play a result.
  7. Kill/relaunch app → confirms **silent reconnect** (token persisted, no re-Enable needed) and last
     zone restored.
- Record a short controller-driven walkthrough (GIF/screen capture) as the acceptance artifact.

## Deferred (post-v1)

Zone grouping/ungrouping, Roon **Settings** service UI, multi-core switching polish, standby/convenience
power, playlist editing beyond queue, and Tidal/Qobuz account linking (browsing already works through the
Browse hierarchy once the Core is logged in).
