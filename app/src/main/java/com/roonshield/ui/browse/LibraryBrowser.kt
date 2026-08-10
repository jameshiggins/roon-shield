package com.roonshield.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowseHeuristics
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.BrowseSession
import com.roonshield.roon.albumKey
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.ActionSheet
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.PosterCard
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.SheetAction
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class LibraryStyle { Grid, Circle, Tile, List }

/**
 * The one production browse surface for every Roon hierarchy. Owns an isolated browse session and:
 * a level stack (grid/list/detail with Back to go up), plus a native-style **action sheet** (Play
 * Now / Play Next / Add to Queue / Start Radio + "Add to Listen Later") shown when an item resolves
 * to an action list. Cursor bookkeeping keeps the visible level correct as the sheet opens/closes.
 */
@Composable
fun LibraryBrowser(
    viewModel: RoonViewModel,
    sessionKey: String,
    rootHierarchy: String,
    rootTitle: String,
    modifier: Modifier = Modifier,
    style: LibraryStyle = LibraryStyle.Grid,
    header: (@Composable () -> Unit)? = null,
    randomSample: Boolean = false,
    /** How many albums the random sample shows. */
    sampleSize: Int = 30,
    /** Fixed column count for the root grid (null = adaptive sizing). */
    rootColumns: Int? = null,
    /** Show a Local Library / Full Library toggle at the root (Albums tab). Local hides albums
     *  that came from a streaming service's library (Qobuz/TIDAL favorites). */
    libraryFilterChips: Boolean = false,
    /** Titles to auto-drill through from the hierarchy root before showing the first level —
     *  e.g. ["Library", "Tags", "Listen Later"] lands on the native Roon "Listen Later" tag. */
    rootPath: List<String> = emptyList(),
    /** Sort applied to the root level when the screen opens (deeper levels stay in Roon order). */
    defaultSort: SortMode = SortMode.Default,
) {
    val scope = rememberCoroutineScope()
    // While music plays, only non-interrupting actions (Add Next / Queue) are offered.
    val activeZone by viewModel.activeZone.collectAsState()
    val playing = activeZone?.isPlaying == true
    // Re-pairing invalidates every server-side item key; keying the root load on the pairing epoch
    // reloads this level after a reconnect while the screen itself stays mounted.
    val pairingEpoch by viewModel.pairingEpoch.collectAsState()
    val session = remember(sessionKey) { viewModel.browseSession(sessionKey, rootHierarchy) }
    val stack = remember(sessionKey) { mutableStateListOf<BrowsePage>() }
    val sheetStack = remember(sessionKey) { mutableStateListOf<BrowsePage>() }
    var sheetAnchor by remember(sessionKey) { mutableStateOf<BrowseItem?>(null) }
    var loading by remember(sessionKey) { mutableStateOf(true) }
    var toast by remember(sessionKey) { mutableStateOf<String?>(null) }
    val firstFocus = remember(sessionKey) { FocusRequester() }

    // Auto-focus the first item of each level so the D-pad always has an anchor (unless a sheet is up).
    LaunchedEffect(stack.size, sheetStack.size, loading) {
        if (sheetStack.isEmpty() && !loading && stack.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(sessionKey, pairingEpoch) {
        loading = true
        stack.clear(); sheetStack.clear()
        var home = session.home()
        for (title in rootPath) {
            val match = home.items.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: home.items.firstOrNull { it.title?.contains(title, ignoreCase = true) == true }
            val key = match?.itemKey
            if (key == null) {
                home = BrowsePage(message = "\"$title\" not found in Roon. Create a \"$title\" tag in the Roon app (tag albums or artists), and it appears here.")
                break
            }
            home = session.open(key)
        }
        stack.add(if (randomSample) buildRandomSample(session, home, viewModel.streamingAlbumKeys(), sampleSize) else home)
        loading = false
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    fun closeSheet() {
        scope.launch { repeat(sheetStack.size) { session.back() }; sheetStack.clear(); sheetAnchor = null }
    }
    fun open(item: BrowseItem) {
        val k = item.itemKey ?: return
        scope.launch {
            loading = true
            val page = session.open(k)
            if (BrowseHeuristics.isActionList(page.items)) { sheetAnchor = item; sheetStack.add(page) } else { stack.add(page) }
            loading = false
        }
    }
    fun pickAction(action: BrowseItem) {
        scope.launch {
            loading = true
            val res = session.open(action.itemKey ?: "")
            if (BrowseHeuristics.isActionList(res.items)) sheetStack.add(res) else { toast = action.title ?: "Done"; repeat(sheetStack.size) { session.back() }; sheetStack.clear(); sheetAnchor = null }
            loading = false
        }
    }
    fun back() {
        if (sheetStack.isNotEmpty()) {
            if (sheetStack.size > 1) { scope.launch { session.back() }; sheetStack.removeAt(sheetStack.lastIndex) } else closeSheet()
            return
        }
        if (stack.size > 1) scope.launch {
            val p = session.back()
            if (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
                // Preserve a random-sampled root (don't overwrite it with the ordered parent).
                if (stack.size > 1 || !randomSample) stack[stack.lastIndex] = p
            }
        }
    }

    BackHandler(enabled = sheetStack.isNotEmpty() || stack.size > 1) { back() }

    val page = stack.lastOrNull()
    val atRoot = stack.size <= 1

    // Load-more paging: Roon lists can be thousands of items; we load a window and append on demand.
    val extra = remember(sessionKey) { mutableStateListOf<BrowseItem>() }
    var loadingMore by remember(sessionKey) { mutableStateOf(false) }
    var reachedEnd by remember(sessionKey) { mutableStateOf(false) }
    var sortMode by remember(sessionKey) { mutableStateOf(defaultSort) }
    var shuffleSeed by remember(sessionKey) { mutableStateOf(1) }
    var showSort by remember(sessionKey) { mutableStateOf(false) }
    LaunchedEffect(stack.size, sheetStack.size) {
        extra.clear(); loadingMore = false; reachedEnd = false; showSort = false
        sortMode = if (stack.size <= 1) defaultSort else SortMode.Default
    }
    val baseItems = page?.items ?: emptyList()
    val items = if (extra.isEmpty()) baseItems else baseItems + extra
    val canLoadMore = page != null && !reachedEnd && items.size < page.totalCount
    fun loadMore() {
        val p = page ?: return
        if (loadingMore || reachedEnd || items.size >= p.totalCount) return
        loadingMore = true
        scope.launch {
            val more = runCatching { session.page(items.size, 100) }.getOrNull().orEmpty()
            // Drop any items we already have (guards against offset overlap -> duplicate keys / loops).
            val seen = HashSet<String>().apply { items.forEach { it.itemKey?.let(::add) } }
            val fresh = more.filter { it.itemKey == null || seen.add(it.itemKey) }
            if (fresh.isEmpty()) reachedEnd = true else extra.addAll(fresh)
            loadingMore = false
        }
    }
    var localOnly by remember(sessionKey) { mutableStateOf(true) }
    val excluded by produceState(initialValue = emptySet<String>(), libraryFilterChips, localOnly, pairingEpoch) {
        value = if (libraryFilterChips && localOnly) viewModel.streamingAlbumKeys() else emptySet()
    }
    val playCounts by viewModel.playCounts.collectAsState()
    // Most-played with no history yet behaves as Default (no reorder, no eager full pagination).
    val effectiveSort = if (sortMode == SortMode.MostPlayed && playCounts.isEmpty()) SortMode.Default else sortMode
    // A sort must cover the whole level, so eagerly page through everything while a sort is active.
    LaunchedEffect(effectiveSort, canLoadMore, loadingMore) {
        if (effectiveSort != SortMode.Default && canLoadMore && !loadingMore) loadMore()
    }
    val sorted = applySort(items, effectiveSort, shuffleSeed, playCounts)
    val displayItems =
        if (atRoot && excluded.isNotEmpty()) sorted.filter { albumKey(it) !in excluded } else sorted
    val sorting = effectiveSort != SortMode.Default && canLoadMore // still loading the full set
    val showSentinel = canLoadMore && effectiveSort == SortMode.Default

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (atRoot) rootTitle else (page?.title?.ifEmpty { rootTitle } ?: rootTitle),
                    color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!atRoot) { Spacer(Modifier.width(12.dp)); Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp) }
                if (loading || sorting) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.weight(1f))
                if (libraryFilterChips && atRoot) {
                    ToggleChip("Local Library", selected = localOnly) { localOnly = true }
                    Spacer(Modifier.width(8.dp))
                    ToggleChip("Full Library", selected = !localOnly) { localOnly = false }
                    Spacer(Modifier.width(12.dp))
                }
                if (items.isNotEmpty()) SortChip(sortMode) { showSort = true }
            }
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty() && !loading) {
                Text(page?.message ?: "Nothing here.", color = RoonTextSecondary, fontSize = 14.sp)
            } else if (!atRoot && !BrowseHeuristics.looksLikeGrid(items)) {
                DetailView(page, displayItems, viewModel, firstFocus, showSentinel, playing, ::loadMore, ::open)
            } else {
                GridView(displayItems, if (atRoot) style else LibraryStyle.Grid, viewModel, if (atRoot) header else null, firstFocus, showSentinel, if (atRoot) rootColumns else null, ::loadMore, ::open)
            }
        }

        if (sheetStack.isNotEmpty()) {
            // While playing, offer only Add Next / Queue so a stray click can't cut the music.
            // (If Roon offers no queue-safe action here — e.g. a radio stream — show the full set.)
            val rawActions = sheetStack.last().items
            val sheetActions = if (playing) rawActions.filter(::isQueueSafe).ifEmpty { rawActions } else rawActions
            ActionSheet(
                heading = sheetAnchor?.title,
                subheading = sheetAnchor?.subtitle,
                imageKey = sheetAnchor?.imageKey,
                fetchArt = viewModel::loadArt,
                actions = sheetActions,
                extraActions = emptyList(),
                onPick = ::pickAction,
                onExtra = {},
                onDismiss = ::closeSheet,
            )
        }
        if (showSort) {
            ActionSheet(
                heading = "Sort",
                subheading = null,
                imageKey = null,
                fetchArt = viewModel::loadArt,
                actions = emptyList(),
                extraActions = SortMode.entries.map { m ->
                    SheetAction(m.name, m.label, if (m == sortMode) Icons.Filled.Check else Icons.AutoMirrored.Filled.Sort)
                },
                onPick = {},
                onExtra = { sa ->
                    val m = runCatching { SortMode.valueOf(sa.id) }.getOrNull() ?: SortMode.Default
                    if (m == SortMode.Shuffle) shuffleSeed++
                    sortMode = m
                    showSort = false
                },
                onDismiss = { showSort = false },
            )
        }
        if (toast != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    toast!!,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(RoonAccent).padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Build the "today's albums" page: windows pulled from random offsets across the level, deduped
 *  and shuffled, excluding streaming-service library albums so only the local library shows.
 *  Seeded by the calendar day, so the selection is the same all day (and across reconnects) and
 *  rolls over to a fresh one each morning. */
private suspend fun buildRandomSample(session: BrowseSession, home: BrowsePage, excluded: Set<String>, sampleSize: Int): BrowsePage {
    fun keep(item: BrowseItem) = excluded.isEmpty() || albumKey(item) !in excluded
    val total = home.list?.count ?: home.items.size
    if (total <= sampleSize) {
        val items = home.items.filter(::keep)
        return BrowsePage(list = home.list?.copy(count = items.size), items = items, level = 0)
    }
    val rnd = kotlin.random.Random(LocalDate.now().toEpochDay())
    val windowSize = 6
    val collected = LinkedHashMap<String, BrowseItem>()
    var tries = 0
    while (collected.size < sampleSize && tries < 20) {
        tries++
        val off = rnd.nextInt(0, (total - windowSize).coerceAtLeast(1))
        val window = runCatching { session.page(off, windowSize) }.getOrNull().orEmpty()
        window.forEach { item ->
            if (item.itemKey != null && keep(item)) collected[albumKey(item)] = item
        }
    }
    if (collected.isEmpty()) return home
    val items = collected.values.toList().shuffled(rnd).take(sampleSize)
    return BrowsePage(list = home.list?.copy(count = items.size), items = items, level = 0)
}

/** While music is playing, the only allowed actions are the non-interrupting ones. */
private fun isQueueSafe(item: BrowseItem): Boolean {
    val t = item.title?.lowercase() ?: return false
    return "next" in t || "queue" in t
}

@Composable
private fun GridView(
    items: List<BrowseItem>,
    style: LibraryStyle,
    viewModel: RoonViewModel,
    header: (@Composable () -> Unit)?,
    firstFocus: FocusRequester,
    canLoadMore: Boolean,
    columns: Int?,
    onLoadMore: () -> Unit,
    onOpen: (BrowseItem) -> Unit,
) {
    if (style == LibraryStyle.List) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            header?.let { item { it(); Spacer(Modifier.height(12.dp)) } }
            listItemsIndexed(items, key = { index, it -> it.itemKey ?: "row-$index-${it.title.orEmpty()}" }) { index, item ->
                RoonListItem(
                    item.title ?: "—", item.subtitle, imageKey = item.imageKey, fetchArt = viewModel::loadArt,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onOpen(item) },
                )
            }
            if (canLoadMore) item { LoadMoreSentinel(onLoadMore) }
        }
        return
    }
    val minSize = if (style == LibraryStyle.Circle) 150.dp else if (style == LibraryStyle.Tile) 200.dp else 172.dp
    LazyVerticalGrid(
        columns = if (columns != null) GridCells.Fixed(columns) else GridCells.Adaptive(minSize),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header?.let { item(span = { GridItemSpan(maxLineSpan) }) { it() } }
        gridItemsIndexed(items, key = { index, it -> it.itemKey ?: "cell-$index-${it.title.orEmpty()}" }) { index, item ->
            val focusMod = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
            if (style == LibraryStyle.Tile && item.imageKey.isNullOrEmpty()) {
                ColorTile(item.title ?: "—", modifier = focusMod) { onOpen(item) }
            } else {
                PosterCard(item.title, item.subtitle, item.imageKey, viewModel::loadArt, modifier = focusMod, circular = style == LibraryStyle.Circle, onClick = { onOpen(item) })
            }
        }
        if (canLoadMore) item(span = { GridItemSpan(maxLineSpan) }) { LoadMoreSentinel(onLoadMore) }
    }
}

/** When this scrolls into view it requests the next page. */
@Composable
private fun LoadMoreSentinel(onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DetailView(page: BrowsePage?, items: List<BrowseItem>, viewModel: RoonViewModel, firstFocus: FocusRequester, canLoadMore: Boolean, playing: Boolean, onLoadMore: () -> Unit, onOpen: (BrowseItem) -> Unit) {
    // Header "chips" are verb actions (Play Album, Shuffle, Start Radio). Everything else — tracks and
    // sub-lists, even though tracks are action_list items — renders as rows. While music plays,
    // direct-action chips that would interrupt are hidden (action_list chips open a filtered sheet).
    val actions = items
        .filter { (it.hint == "action" || it.hint == "action_list") && BrowseHeuristics.isActionVerb(it.title) }
        .filter { !playing || it.hint == "action_list" || isQueueSafe(it) }
    val rows = items.filterNot { (it.hint == "action" || it.hint == "action_list") && BrowseHeuristics.isActionVerb(it.title) }
    val headerImage = page?.list?.imageKey ?: items.firstOrNull { !it.imageKey.isNullOrEmpty() }?.imageKey

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (headerImage != null || !page?.list?.subtitle.isNullOrEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    if (headerImage != null) {
                        Box(modifier = Modifier.size(168.dp).clip(RoundedCornerShape(12.dp))) {
                            AlbumArt(headerImage, sizePx = 460, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(168.dp)) {
                        Text(page?.list?.title ?: page?.title ?: "", color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        page?.list?.subtitle?.takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RoonTextSecondary, fontSize = 15.sp) }
                        if (actions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                actions.take(3).forEachIndexed { i, a ->
                                    ActionChip(a.title ?: "Play", modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) { onOpen(a) }
                                }
                            }
                        }
                    }
                }
            }
        } else if (actions.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    actions.take(4).forEachIndexed { i, a ->
                        ActionChip(a.title ?: "Play", modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) { onOpen(a) }
                    }
                }
            }
        }
        listItemsIndexed(rows, key = { index, it -> it.itemKey ?: "det-$index-${it.title.orEmpty()}" }) { index, row ->
            RoonListItem(
                row.title ?: "—", row.subtitle, imageKey = row.imageKey, fetchArt = viewModel::loadArt,
                modifier = if (actions.isEmpty() && index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpen(row) },
            )
        }
        if (canLoadMore) item { LoadMoreSentinel(onLoadMore) }
    }
}

@Composable
private fun ActionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) RoonAccent else RoonSurface)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.Icon(com.roonshield.ui.components.actionIcon(label), contentDescription = null, tint = if (focused) Color.White else RoonAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (focused) Color.White else RoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) RoonAccent else if (selected) RoonSurface else Color.Transparent)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else if (selected) RoonAccent else RoonTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SortChip(current: SortMode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) RoonAccent else RoonSurface)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        androidx.compose.material3.Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = "Sort",
            tint = if (focused) Color.White else RoonTextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (current == SortMode.Default) "Sort" else current.label,
            color = if (focused) Color.White else RoonTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ColorTile(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val palette = listOf(
        Color(0xFF3E7BFA), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF97316),
        Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFFEAB308),
    )
    val base = palette[((name.hashCode() % palette.size) + palette.size) % palette.size]
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.5f))))
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

