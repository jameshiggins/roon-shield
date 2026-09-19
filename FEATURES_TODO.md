# Roon Shield — feature backlog / status

Autonomous build toward native-Roon parity + production quality. We implement equivalent
functionality from Roon's public Extension API in our own Roon-styled UI (no copyrighted assets).

## Done
- **Play Next / Add to Queue / Play Now / Start Radio / Shuffle** — via the native-style **action
  sheet** (`LibraryBrowser` + `ActionSheet`). Renders whatever actions Roon returns for the item.
- **Listen Later** — app-local, persisted (`RoonPrefs` DataStore); "Add to Listen Later" on every
  action sheet; a Listen Later screen + nav + Home tile; entries re-resolve via Search.
- **Unified LibraryBrowser** — one browse surface for all hierarchies (grid/circle/tile/list + detail),
  per-level first-item auto-focus, focus-grouped nav rail.
- **Stable connection** — generation-guarded reconnect (fixed the reconnect loop).
- **Singleton connection + sticky UI** — `RoonRepository` is process-wide (an Activity relaunch
  reuses the live socket instead of spawning a duplicate that fights the first over the extension
  id — the old "searching for core forever" loop). Transient drops keep the main UI mounted with a
  "Reconnecting" overlay; browse levels reload on the pairing epoch so stale item keys are flushed.
- **Today's Albums (Home)** — the random album sample is seeded by the calendar day (same picks all
  day, fresh ones each morning) and limited to the **local library**: a browse-tree probe collects
  the streaming services' library albums (e.g. Qobuz → My Qobuz → Favorite Albums) and excludes
  them. Genres removed from the nav rail and Home tiles (unused).
- **Now Playing info panel** — D-pad ▶ past the last control opens the album's Wikipedia entry;
  ▶ again switches to the artist. (Roon's public API exposes none of the native TiVo review/bio
  text, so Wikipedia is the source; lookups sanitize "(2009 Remaster)"-style noise and reject
  same-name non-music articles.)
- **Playing-safe actions** — while music plays, action sheets and detail chips offer only
  non-interrupting actions (Add Next / Queue); the full set returns when stopped/paused.
- **Home v2** — quick tiles removed; "Today's Albums" is a fixed 4-per-row grid of 48 daily picks.
- **Listen Later = native Roon tag** — the tab drills to Library → Tags → "Listen Later" with full
  play/queue actions. (Roon 2.5's built-in Listen Later list is NOT exposed by the Extension API —
  verified live: root/Library/Tags/Playlists contain no such node — so a same-named tag is the
  closest reachable equivalent. App-local bookmarks and the "Add to Listen Later" sheet action were
  removed; tagging happens in the Roon app.)
- Discovery/pairing, Now Playing, Queue, Search, Zones/volume footer, silent reconnect, keep-screen-on.

## Won't do (hard Roon public-API limitations, documented in README)
- **Stream path / format / quality readout.** Confirmed absent from the Extension API's Transport
  `now_playing` (no codec / sample-rate / bit-depth / signal-path fields in `node-roon-api-transport`).
  Cannot be reproduced without RAAT/partner access. Not faked.
- **Play-to-this-device** (Shield as a Roon endpoint) — needs RAAT / Roon Ready (partner-only).

## Remaining polish (next passes)
- **Context menu via MENU / gamepad-X key** on a focused item (currently the action sheet opens on
  click/select — a dedicated MENU key would match the native long-press affordance).
- **Focus trapping at edges** — `focusGroup` reduces D-pad escaping to the launcher; consider
  `FocusProperties { up/left = Cancel }` on rail/content edges to fully trap it.
- **Load-more / paging** for very large lists (currently loads up to ~200 items per level).
- **Now Playing**: lyrics if/when exposed; richer metadata line from `now_playing` three-line.
- **Listen Later**: smarter re-resolve (store artist separately for better search matching).
- **Release build**: signing config + R8/proguard pass + faster cold start (debug first-frame is slow
  on the Shield).
- **Instrumented UI tests** for the browse/action-sheet flows (unit tests cover the pure logic today).

## Verification (live, on the Shield)
- Home renders; drilling a genre shows detail (Play chip + sub-rows); the action sheet opens with
  Shuffle / Start Radio / Add to Listen Later; the Listen Later entry persists to the datastore. ✓
