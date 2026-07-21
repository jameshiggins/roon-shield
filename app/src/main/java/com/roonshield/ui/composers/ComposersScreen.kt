package com.roonshield.ui.composers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.PosterCard
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

/**
 * Composers browser. Root is a grid of circular composer cards; drilling into a composer pushes
 * their works/albums (a grid of cards). When a level is an album/playlist (art + a Play action and
 * mostly art-less track rows) it renders a native-style detail: big header art, a prominent Play
 * row, and the track list. Every click is routed through [open] so any visual misclassification is
 * cosmetic only — navigation and playback never break.
 */
@Composable
fun ComposersScreen(
    viewModel: RoonViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("composers", "composers") }
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var loading by remember { mutableStateOf(true) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        loading = true
        stack.add(session.home())
        loading = false
    }

    BackHandler(enabled = stack.size > 1) {
        scope.launch {
            loading = true
            val parent = session.back()
            if (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
                stack[stack.lastIndex] = parent
            }
            loading = false
        }
    }

    fun open(item: BrowseItem) {
        val key = item.itemKey ?: return
        scope.launch {
            loading = true
            val next = session.open(key)
            // Actions (Play …) never push; only real browsable levels do.
            if (item.hint != "action" && (next.items.isNotEmpty() || next.message == null)) {
                stack.add(next)
            }
            loading = false
        }
    }

    val page = stack.lastOrNull()
    val isRoot = stack.size <= 1

    // Focus the first item once a real (non-empty, loaded) level is on screen, on every drill-in.
    LaunchedEffect(page, loading) {
        if (!loading && page != null && page.items.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = page?.title?.ifEmpty { "Composers" } ?: "Composers",
                color = RoonTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isRoot) {
                Spacer(Modifier.width(12.dp))
                Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp)
            }
            if (loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        val items = page?.items ?: emptyList()
        if (items.isEmpty()) {
            page?.message?.let { Text(it, color = RoonTextSecondary, fontSize = 14.sp) }
        } else if (page != null && isAlbumLevel(page)) {
            AlbumDetail(
                page = page,
                fetchArt = viewModel::loadArt,
                firstFocus = firstFocus,
                onOpen = { open(it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            CardGrid(
                items = items,
                fetchArt = viewModel::loadArt,
                circular = isRoot,
                firstFocus = firstFocus,
                onOpen = { open(it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * A level looks like an album/playlist when the list itself carries cover art, it offers at least
 * one action (Play …), and most of its items are art-less (tracks don't carry their own art, but
 * album/drill-in targets do). This keeps a "works" grid that happens to include a stray "Play All"
 * from being mistaken for a track list.
 */
private fun isAlbumLevel(page: BrowsePage): Boolean {
    val items = page.items
    if (page.list?.imageKey.isNullOrEmpty()) return false
    if (items.none { it.hint == "action" }) return false
    val withArt = items.count { !it.imageKey.isNullOrEmpty() }
    return withArt * 2 <= items.size
}

@Composable
private fun CardGrid(
    items: List<BrowseItem>,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    circular: Boolean,
    firstFocus: FocusRequester,
    onOpen: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (circular) 150.dp else 170.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        gridItemsIndexed(items, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
            PosterCard(
                title = item.title,
                subtitle = item.subtitle,
                imageKey = item.imageKey,
                fetchArt = fetchArt,
                circular = circular,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun AlbumDetail(
    page: BrowsePage,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    firstFocus: FocusRequester,
    onOpen: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = page.items.filter { it.hint == "action" }
    val tracks = page.items.filter { it.hint != "action" }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                AlbumArt(imageKey = page.list?.imageKey, sizePx = 600, fetch = fetchArt)
            }
            Column(modifier = Modifier.padding(start = 20.dp).weight(1f)) {
                Text(
                    text = page.title.ifEmpty { "Album" },
                    color = RoonTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!page.list?.subtitle.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = page.list?.subtitle.orEmpty(),
                        color = RoonTextSecondary,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.forEachIndexed { index, action ->
                            ActionButton(
                                label = action.title ?: "Play",
                                primary = index == 0,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = { onOpen(action) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(tracks, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
                RoonListItem(
                    title = item.title ?: "—",
                    subtitle = item.subtitle,
                    imageKey = item.imageKey,
                    fetchArt = fetchArt,
                    // If there were no action buttons, the first track becomes the focus anchor.
                    modifier = if (index == 0 && actions.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

/** A focusable, labeled Play-style button for album/playlist actions. */
@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> RoonAccent
        primary -> RoonAccent.copy(alpha = 0.9f)
        else -> RoonSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                if (focused) BorderStroke(2.dp, RoonTextPrimary) else BorderStroke(1.dp, Color.Transparent),
                RoundedCornerShape(999.dp),
            )
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
