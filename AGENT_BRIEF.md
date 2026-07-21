# Roon Shield — Screen Builder Brief (shared contract)

You are building ONE screen for an Android TV (Nvidia Shield) **Roon remote** in Kotlin + Jetpack
Compose, styled to mimic the **native Roon app**. Repo: `C:\Users\higgi\roon-shield`. A foundation
already exists; build ON it.

## HARD RULES
- CREATE ONLY your assigned file(s) at the given path(s). Do **NOT** edit any other file. Never touch
  shared files: `ui/RoonApp.kt`, `ui/RoonViewModel.kt`, `roon/RoonRepository.kt`,
  `roon/services/RoonBrowse.kt`, `roon/BrowseSession.kt`, `ui/theme/*`, `ui/components/*`, `build.gradle.kts`.
- Do **NOT** run gradle/builds/tests. Do **NOT** add dependencies.
- Keep every helper composable/function **private** in your own file (avoid clashes with other agents).
- Use ONLY the APIs below. Don't invent ViewModel/repo methods. If you truly need a shared change,
  build without it if at all possible; otherwise list it under `INTEGRATION REQUESTS`.
- You MAY Read these to confirm signatures: `ui/RoonViewModel.kt`, `roon/BrowseSession.kt`,
  `roon/model/RoonModels.kt`, `ui/components/Cards.kt`, `ui/components/Widgets.kt`,
  `ui/components/AlbumArt.kt`, `ui/theme/Theme.kt`, `ui/components/BrowsePane.kt` (for the pattern).

## APIs
**RoonViewModel** (`com.roonshield.ui.RoonViewModel`)
- StateFlows: `zones: StateFlow<List<Zone>>`, `activeZone: StateFlow<Zone?>`, `activeZoneId: StateFlow<String?>`, `queue: StateFlow<List<QueueItem>>`
- `fun browseSession(key: String, hierarchy: String = "browse"): BrowseSession`
- `suspend fun loadArt(imageKey: String, width: Int, height: Int): ByteArray?`  ← pass `viewModel::loadArt` as `fetchArt`
- Transport: `playPause()`, `next()`, `previous()`, `stop()`, `seekBy(Int)`, `seekTo(Int)`, `toggleShuffle()`, `toggleRadio()`, `cycleLoop()`, `playFromQueue(Long)`
- Zones/volume: `setZone(String)`, `nudgeVolume(Int)`, `setVolume(Int)`, `toggleMute()`

**BrowseSession** (`com.roonshield.roon.BrowseSession`)
- `suspend home(): BrowsePage`, `suspend open(itemKey: String): BrowsePage`, `suspend input(text: String): BrowsePage`,
  `suspend back(levels: Int = 1): BrowsePage`, `suspend invoke(itemKey: String): String?`, `suspend page(offset: Int, count: Int = 100): List<BrowseItem>`

**BrowsePage** (`com.roonshield.roon.BrowsePage`): `list: BrowseList?`, `items: List<BrowseItem>`, `message: String?`, `level: Int`, `title: String`, `totalCount: Int`

**Models** (`com.roonshield.roon.model`)
- `BrowseItem(title: String?, subtitle: String?, imageKey: String?, itemKey: String?, hint: String?, inputPrompt: InputPrompt?)`
- `BrowseList(title, count, level, subtitle, imageKey, hint, displayOffset)`
- `InputPrompt(prompt, action, value, isPassword)`
- `Zone(zoneId, displayName, state, outputs, nowPlaying, isNextAllowed, isPreviousAllowed, isPauseAllowed, isPlayAllowed, isSeekAllowed, queueItemsRemaining, settings)`; `Zone.isPlaying: Boolean`; `Zone.primaryOutputId: String?`
- `NowPlaying(seekPosition: Int?, length: Int?, imageKey: String?, oneLine, twoLine, threeLine: Lines?)`
- `Lines(line1, line2, line3: String?)`
- `QueueItem(queueItemId: Long?, length: Int?, imageKey: String?, oneLine, twoLine, threeLine: Lines?)`
- `Output(outputId, displayName, volume)`; `Volume(value: Double?, min, max, isMuted: Boolean?)`
- `ZoneSettings(loop: String?, shuffle: Boolean?, autoRadio: Boolean?)`

**Shared components** (`com.roonshield.ui.components`) — REUSE for consistency:
- `AlbumArt(imageKey: String?, sizePx: Int, modifier = Modifier, showPlaceholder: Boolean = true, fetch)`
- `PosterCard(title: String?, subtitle: String?, imageKey: String?, fetchArt, modifier, width: Dp = 168.dp, circular: Boolean = false, onClick)`
- `RoonListItem(title: String, subtitle: String?, modifier, imageKey: String? = null, fetchArt: (suspend (String,Int,Int)->ByteArray?)? = null, trailing: @Composable (()->Unit)? = null, onClick)`
- `TransportButton(icon: ImageVector, contentDescription: String, modifier, size: Int = 56, active: Boolean = false, filled: Boolean = false, onClick)`
- `SectionHeader(title: String, modifier, action: String? = null)`
- `CardShelf(title: String, modifier, action: String? = null, content: LazyListScope.()->Unit)`
- `Modifier.onFocusState(onChange: (Boolean)->Unit)`

**Theme** (`com.roonshield.ui.theme`): `RoonBackground`, `RoonSurface`, `RoonSurfaceVariant`, `RoonAccent` (0xFF3E7BFA), `RoonTextPrimary`, `RoonTextSecondary`

## Browse pattern (copy this)
```kotlin
val scope = rememberCoroutineScope()
val session = remember { viewModel.browseSession("UNIQUE_KEY", "HIERARCHY") }
val stack = remember { mutableStateListOf<BrowsePage>() }
var loading by remember { mutableStateOf(true) }
LaunchedEffect(Unit) { stack.add(session.home()); loading = false }
BackHandler(enabled = stack.size > 1) {
    scope.launch { val p = session.back(); if (stack.size > 1) { stack.removeAt(stack.lastIndex); stack[stack.lastIndex] = p } }
}
fun open(item: BrowseItem) {
    val k = item.itemKey ?: return
    scope.launch {
        loading = true
        val next = session.open(k)              // invokes the item
        if (item.hint != "action") stack.add(next)   // action items just play; don't push
        loading = false
    }
}
val page = stack.lastOrNull()
```
Use a UNIQUE session key (your screen name). Put a `FocusRequester` on the first item and
`requestFocus()` in `LaunchedEffect(page)` so the D-pad has an anchor (never leave a dead screen).

## Detail / album levels
When a level is an album/playlist (items are tracks — `item.subtitle` is often a duration; `page.list`
usually has `imageKey`/`title`/`subtitle`): render a header (`AlbumArt` from `page.list.imageKey`,
sizePx ~600, big title + subtitle) + a prominent **Play** row (items whose `hint == "action"` or an
`"action_list"` item such as "Play Album"/"Play Now" → expose as `TransportButton`/labeled buttons that
call `open(thatItem)`) + the track list via `RoonListItem`. Track rows that are actions should play on click.

## Look
Native Roon: dark, elegant, large art, generous spacing, clear focus highlight, controller-first, D-pad
navigable (Compose `clickable` + `focusable` already handle center/enter).

## Deliverable signature
`@Composable fun NAME(viewModel: com.roonshield.ui.RoonViewModel, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier)`
(Home also takes `onNavigate: (String) -> Unit`.)

## Your final message MUST end with exactly:
```
FILES CREATED: <paths>
WIRE-IN: <the Content() branch, e.g. "albums" -> AlbumsScreen(viewModel)>
INTEGRATION REQUESTS: <shared-file changes needed, or "none">
```
