package com.roonshield.ui.playlists

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Playlists screen: a native-style grid of playlist posters at the root, drilling into a rich
 * detail (large art header, prominent Play/Shuffle/Queue action row, then the track list).
 * Owns one isolated browse session on the "playlists" hierarchy.
 */
@Composable
fun PlaylistsScreen(
    viewModel: RoonViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("playlists", "playlists") }
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
            // Action items (Play/Shuffle/Queue) just play — never push a level.
            if (item.hint != "action") stack.add(next)
            loading = false
        }
    }

    // First-item auto-focus anchor: re-requested whenever the level changes.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(page, loading) {
        if (!loading) runCatching { firstFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        val isDetail = depth > 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isDetail) (page?.title?.ifEmpty { "Playlist" } ?: "Playlist") else "Playlists",
                color = RoonTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDetail) {
                Spacer(Modifier.width(12.dp))
                Text("· Back goes up", color = RoonTextSecondary, fontSize = 13.sp)
            }
            if (loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            page == null -> LoadingBody()
            depth > 1 -> PlaylistDetail(
                page = page,
                fetchArt = viewModel::loadArt,
                firstFocus = firstFocus,
                onOpen = ::open,
            )
            else -> PlaylistGrid(
                items = page.items,
                message = page.message,
                fetchArt = viewModel::loadArt,
                firstFocus = firstFocus,
                onOpen = ::open,
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RoonAccent)
    }
}

@Composable
private fun PlaylistGrid(
    items: List<BrowseItem>,
    message: String?,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    firstFocus: FocusRequester,
    onOpen: (BrowseItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = message ?: "No playlists.",
                color = RoonTextSecondary,
                fontSize = 15.sp,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
            PosterCard(
                title = item.title,
                subtitle = item.subtitle,
                imageKey = item.imageKey,
                fetchArt = fetchArt,
                width = 180.dp,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun PlaylistDetail(
    page: BrowsePage,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    firstFocus: FocusRequester,
    onOpen: (BrowseItem) -> Unit,
) {
    val actions = page.items.filter { it.hint == "action" || it.hint == "action_list" }
    val tracks = page.items.filter { it.hint != "action" && it.hint != "action_list" }
    val list = page.list

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header: large art + title + subtitle/count.
        item(key = "__header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AlbumArt(
                    imageKey = list?.imageKey,
                    sizePx = 600,
                    fetch = fetchArt,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = list?.title ?: page.title.ifEmpty { "Playlist" },
                        color = RoonTextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = list?.subtitle?.takeIf { it.isNotBlank() }
                        ?: "${tracks.size.takeIf { it > 0 } ?: page.totalCount} tracks"
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sub,
                        color = RoonTextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Prominent action row (Play / Shuffle / Queue …).
        if (actions.isNotEmpty()) {
            item(key = "__actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        ActionButton(
                            label = action.title ?: "Play",
                            icon = iconFor(action.title),
                            primary = index == 0,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onOpen(action) },
                        )
                    }
                }
            }
        }

        // Message / empty handling for the detail level.
        if (tracks.isEmpty()) {
            item(key = "__empty") {
                Text(
                    text = page.message ?: "This playlist is empty.",
                    color = RoonTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        itemsIndexed(tracks, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, track ->
            val focusFirstTrack = actions.isEmpty() && index == 0
            RoonListItem(
                title = track.title ?: "—",
                subtitle = track.subtitle,
                imageKey = track.imageKey,
                fetchArt = fetchArt,
                modifier = if (focusFirstTrack) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onOpen(track) },
            )
        }
    }
}

private fun iconFor(title: String?): ImageVector {
    val t = title?.lowercase().orEmpty()
    return when {
        t.contains("shuffle") -> Icons.Filled.Shuffle
        t.contains("queue") || t.contains("add") -> Icons.AutoMirrored.Filled.QueueMusic
        else -> Icons.Filled.PlayArrow
    }
}

/** A focusable, labeled pill button for playlist actions (Play / Shuffle / Queue). */
@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> RoonAccent
        primary -> RoonAccent.copy(alpha = 0.16f)
        else -> RoonSurfaceVariant
    }
    val fg = if (focused) Color.White else if (primary) RoonAccent else RoonTextPrimary
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            }
            .clip(shape)
            .background(bg)
            .border(
                if (focused) BorderStroke(2.dp, RoonAccent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape,
            )
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
