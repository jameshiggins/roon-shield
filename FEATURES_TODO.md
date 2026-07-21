# Roon Shield — Next feature backlog (paused, per user)

Dev paused so the Shield is free for Plex. No deploys / no force-stopping Plex until resumed.

> Approach note: we implement **equivalent functionality from Roon's public Extension API** and render
> it in our own Roon-styled UI — we do not copy/reproduce the official app's proprietary screens pixel
> for pixel. Match behavior, not copyrighted assets.

## 1. Stream path & quality (Now Playing)
- Show the current stream's **format / quality** on Now Playing (e.g. codec, sample rate/bit depth,
  lossless / hi-res indicator, and the signal-path quality state if exposed).
- **Doc research needed first:** confirm what the Extension API actually exposes. The Transport
  `subscribe_zones` zone `now_playing` object is the candidate; the full DSP "signal path" chain and
  the colored quality dot may be internal-only. Read node-roon-api transport service + inspect a live
  zone payload (we already log full MOO frames — capture a `subscribe_zones` "Subscribed"/"Changed"
  body and see which format/quality fields are present).
- Implement what's available; if signal path isn't exposed, show format/quality that is and note the gap.

## 2. Play Next / Add to Queue  (largely already available)
- Roon's Browse **action lists** already include actions like Play Now, **Play Next / Add Next**,
  **Add to Queue / Queue**, Start Radio, Add to Library when you open a track/album. Our BrowseSession
  drills into these today.
- Work: surface them as **first-class buttons** and add a **context menu** (controller MENU button, or
  a dedicated "…" affordance) on any focused track/album/artist that opens its action list, so Play
  Next / Add to Queue are one press away instead of buried.
- Wire MENU key (KEYCODE_MENU / gamepad) at the item level to invoke the action list.

## 3. Listen Later
- Roon exposes **Tags / bookmarks**. "Listen Later" = either a dedicated tag or a saved-for-later flow.
- **Doc research needed:** whether tags/bookmarks are creatable/settable via the Extension Browse API
  (action-list items such as "Add to Tag") or only readable. If the action list offers a tag/bookmark
  action, expose a "Listen Later" button that applies it; add a "Listen Later" browse entry (Tags /
  Bookmarks hierarchy) to view them.
- Fallback if API can't set tags: maintain an app-local "Listen Later" list (persist item refs) — but
  note item_keys are session-scoped, so store durable identifiers (title/artist) or re-resolve.

## Docs to read on resume
- node-roon-api Transport service (zone / now_playing fields for format/quality).
- node-roon-api Browse service (action lists: play_next/add_next, queue, tags/bookmarks).
- Capture a live `subscribe_zones` body from our own MOO logs to see real field names.

## Verification (on resume, when Shield is free again)
- Now Playing shows format/quality for a playing track.
- On a focused track: MENU opens actions; Play Next and Add to Queue work (verify queue order changes).
- Listen Later adds an item and it appears in a Listen Later view.
