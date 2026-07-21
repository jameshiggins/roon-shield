package com.roonshield.ui.artists

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import com.roonshield.ui.theme.RoonBackground
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

/** Roon hints that identify an item as a fire-and-forget / chooser action rather than a navigable list. */
private fun BrowseItem.isAction(): Boolean = hint == "action" || hint == "action_list"

/**
 * Artists screen: a grid of circular artist cards at the root; opening an artist pushes the rich
 * Roon artist page (header + Play/Radio actions + Top Tracks / Albums / Appears On sub-lists), with
 * Back walking up the stack. Owns its own isolated browse session so it never collides with others.
 */
@Composable
fun ArtistsScreen(
    viewModel: RoonViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("artists", "artists") }
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val p = session.home()
        stack.clear()
        stack.add(p)
        loading = false
    }

    val page = stack.lastOrNull()
    val depth = stack.size

    BackHandler(enabled = depth > 1) {
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
            // Terminal actions (Play Now, Start Radio) produce no new list — don't push.
            // Everything else (artists, albums, tracks, "action_list" choosers) drills in.
            if (item.hint != "action" && (next.items.isNotEmpty() || next.message == null)) {
                stack.add(next)
            }
            loading = false
        }
    }

    // Focus anchor: index 0 is always composed, so it's a safe requestFocus target.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(depth, loading) {
        if (!loading) runCatching { focusRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize().background(RoonBackground)) {
        when {
            page == null && loading -> LoadingIndicator()
            page == null -> EmptyMessage("Nothing here.")
            depth == 1 -> ArtistGrid(page, viewModel, focusRequester, ::open)
            else -> ArtistDetail(page, depth, viewModel, focusRequester, ::open)
        }

        if (loading && page != null) {
            CircularProgressIndicator(
                color = RoonAccent,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(22.dp),
            )
        }
    }
}

/** Root level: a grid of circular artist cards; first card takes initial focus. */
@Composable
private fun ArtistGrid(
    page: BrowsePage,
    viewModel: RoonViewModel,
    focusRequester: FocusRequester,
    open: (BrowseItem) -> Unit,
) {
    val items = page.items
    if (items.isEmpty()) {
        EmptyMessage(page.message ?: "No artists in your library.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Text(
            text = page.title.ifEmpty { "Artists" },
            color = RoonTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                val isFirst = item === items.first()
                PosterCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    imageKey = item.imageKey,
                    fetchArt = viewModel::loadArt,
                    circular = true,
                    modifier = if (isFirst) Modifier.focusRequester(focusRequester) else Modifier,
                    onClick = { open(item) },
                )
            }
        }
    }
}

/**
 * Artist page (and deeper sub-lists). Header from `page.list` (large circular art at the artist level),
 * then prominent action buttons (Play / Start Radio), then the remaining list/section items as rows
 * that drill deeper on click.
 */
@Composable
private fun ArtistDetail(
    page: BrowsePage,
    depth: Int,
    viewModel: RoonViewModel,
    focusRequester: FocusRequester,
    open: (BrowseItem) -> Unit,
) {
    val actions = page.items.filter { it.isAction() }
    val rows = page.items.filter { !it.isAction() }
    val isArtistLevel = depth == 2

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        item(key = "__header__") {
            DetailHeader(
                imageKey = page.list?.imageKey,
                title = page.list?.title ?: page.title.ifEmpty { "Artist" },
                subtitle = page.list?.subtitle,
                circular = isArtistLevel,
                fetchArt = viewModel::loadArt,
            )
        }

        if (actions.isNotEmpty()) {
            item(key = "__actions__") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    actions.forEachIndexed { index, item ->
                        ActionButton(
                            label = item.title ?: "Play",
                            modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                            onClick = { open(item) },
                        )
                    }
                }
            }
        }

        page.message?.let { msg ->
            if (rows.isEmpty()) item(key = "__message__") {
                Text(msg, color = RoonTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        items(rows, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
            val isFirstFocus = actions.isEmpty() && item === rows.firstOrNull()
            RoonListItem(
                title = item.title ?: "—",
                subtitle = item.subtitle,
                imageKey = item.imageKey,
                fetchArt = viewModel::loadArt,
                modifier = if (isFirstFocus) Modifier.focusRequester(focusRequester) else Modifier,
                onClick = { open(item) },
            )
        }
    }
}

/** Large artist/album header: art on the left, name + subtitle on the right. */
@Composable
private fun DetailHeader(
    imageKey: String?,
    title: String,
    subtitle: String?,
    circular: Boolean,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(shape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape),
        ) {
            AlbumArt(imageKey = imageKey, sizePx = 600, fetch = fetchArt, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.padding(start = 24.dp).weight(1f)) {
            Text(
                text = title,
                color = RoonTextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = RoonTextSecondary,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A prominent, focusable text button for artist actions (Play / Start Radio / Add to Queue …). */
@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) RoonAccent else RoonTextPrimary.copy(alpha = 0.12f)
    val fg = if (focused) Color.White else RoonTextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = RoonTextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
