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
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
) {
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(sessionKey) {
        loading = true
        stack.clear(); sheetStack.clear()
        stack.add(session.home())
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
        if (stack.size > 1) scope.launch { val p = session.back(); if (stack.size > 1) { stack.removeAt(stack.lastIndex); stack[stack.lastIndex] = p } }
    }

    BackHandler(enabled = sheetStack.isNotEmpty() || stack.size > 1) { back() }

    val page = stack.lastOrNull()
    val atRoot = stack.size <= 1

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (atRoot) rootTitle else (page?.title?.ifEmpty { rootTitle } ?: rootTitle),
                    color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!atRoot) { Spacer(Modifier.width(12.dp)); Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp) }
                if (loading) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))

            val items = page?.items ?: emptyList()
            if (items.isEmpty() && !loading) {
                Text(page?.message ?: "Nothing here.", color = RoonTextSecondary, fontSize = 14.sp)
            } else if (!atRoot && !BrowseHeuristics.looksLikeGrid(items)) {
                DetailView(page, items, viewModel, firstFocus, ::open)
            } else {
                GridView(items, if (atRoot) style else LibraryStyle.Grid, viewModel, if (atRoot) header else null, firstFocus, ::open)
            }
        }

        if (sheetStack.isNotEmpty()) {
            ActionSheet(
                heading = sheetAnchor?.title,
                subheading = sheetAnchor?.subtitle,
                imageKey = sheetAnchor?.imageKey,
                fetchArt = viewModel::loadArt,
                actions = sheetStack.last().items,
                extraActions = listOf(SheetAction("listen_later", "Add to Listen Later", Icons.Filled.WatchLater)),
                onPick = ::pickAction,
                onExtra = {
                    val anchor = sheetAnchor
                    if (anchor != null) {
                        // If the sheet was opened from a "Play X" action chip, save the thing itself
                        // (the current level: album/artist/genre) rather than the verb.
                        val levelTitle = stack.lastOrNull()?.let { it.list?.title ?: it.title }
                        val effective = if (BrowseHeuristics.isActionVerb(anchor.title) && !levelTitle.isNullOrBlank()) {
                            anchor.copy(title = levelTitle, subtitle = null, imageKey = anchor.imageKey ?: stack.lastOrNull()?.list?.imageKey)
                        } else {
                            anchor
                        }
                        viewModel.addListenLater(effective)
                    }
                    toast = "Added to Listen Later"
                    closeSheet()
                },
                onDismiss = ::closeSheet,
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

@Composable
private fun GridView(
    items: List<BrowseItem>,
    style: LibraryStyle,
    viewModel: RoonViewModel,
    header: (@Composable () -> Unit)?,
    firstFocus: FocusRequester,
    onOpen: (BrowseItem) -> Unit,
) {
    if (style == LibraryStyle.List) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            header?.let { item { it(); Spacer(Modifier.height(12.dp)) } }
            listItemsIndexed(items, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
                RoonListItem(
                    item.title ?: "—", item.subtitle, imageKey = item.imageKey, fetchArt = viewModel::loadArt,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onOpen(item) },
                )
            }
        }
        return
    }
    val minSize = if (style == LibraryStyle.Circle) 150.dp else if (style == LibraryStyle.Tile) 200.dp else 172.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header?.let { item(span = { GridItemSpan(maxLineSpan) }) { it() } }
        gridItemsIndexed(items, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
            val focusMod = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
            if (style == LibraryStyle.Tile && item.imageKey.isNullOrEmpty()) {
                ColorTile(item.title ?: "—", modifier = focusMod) { onOpen(item) }
            } else {
                PosterCard(item.title, item.subtitle, item.imageKey, viewModel::loadArt, modifier = focusMod, circular = style == LibraryStyle.Circle, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun DetailView(page: BrowsePage?, items: List<BrowseItem>, viewModel: RoonViewModel, firstFocus: FocusRequester, onOpen: (BrowseItem) -> Unit) {
    val actions = items.filter { it.hint == "action" || it.hint == "action_list" }
    val rows = items.filter { it.hint != "action" && it.hint != "action_list" }
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
        listItemsIndexed(rows, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, row ->
            RoonListItem(
                row.title ?: "—", row.subtitle, imageKey = row.imageKey, fetchArt = viewModel::loadArt,
                modifier = if (actions.isEmpty() && index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpen(row) },
            )
        }
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

