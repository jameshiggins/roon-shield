package com.roonshield.ui.albums

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("albums", "albums") }
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { stack.add(session.home()); loading = false }
    BackHandler(enabled = stack.size > 1) {
        scope.launch {
            val p = session.back()
            if (stack.size > 1) { stack.removeAt(stack.lastIndex); stack[stack.lastIndex] = p }
        }
    }
    fun open(item: BrowseItem) {
        val k = item.itemKey ?: return
        scope.launch { loading = true; val next = session.open(k); if (item.hint != "action") stack.add(next); loading = false }
    }

    val page = stack.lastOrNull()
    val atRoot = stack.size <= 1

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (atRoot) "Albums" else (page?.title?.ifEmpty { "Album" } ?: "Album"), color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!atRoot) { Spacer(Modifier.width(12.dp)); Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp) }
            if (loading) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(12.dp))

        val items = page?.items ?: emptyList()
        if (atRoot) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(178.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                    PosterCard(item.title, item.subtitle, item.imageKey, viewModel::loadArt, onClick = { open(item) })
                }
            }
        } else {
            AlbumDetail(page, items, viewModel, ::open)
        }
    }
}

@Composable
private fun AlbumDetail(page: BrowsePage?, items: List<BrowseItem>, viewModel: RoonViewModel, open: (BrowseItem) -> Unit) {
    val actions = items.filter { it.hint == "action" || it.hint == "action_list" }
    val tracks = items.filter { it.hint != "action" && it.hint != "action_list" }
    val headerImage = page?.list?.imageKey ?: items.firstOrNull { !it.imageKey.isNullOrEmpty() }?.imageKey

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Box(modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))) {
                    AlbumArt(headerImage, sizePx = 480, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(180.dp)) {
                    Text(page?.list?.title ?: page?.title ?: "Album", color = RoonTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    page?.list?.subtitle?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(6.dp)); Text(it, color = RoonTextSecondary, fontSize = 16.sp)
                    }
                    if (actions.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            actions.take(4).forEach { a ->
                                LabeledAction(a.title ?: "Play") { open(a) }
                            }
                        }
                    }
                }
            }
        }
        items(tracks, key = { it.itemKey ?: it.title.orEmpty() }) { track ->
            RoonListItem(
                title = track.title ?: "—",
                subtitle = track.subtitle,
                onClick = { open(track) },
            )
        }
    }
}

@Composable
private fun LabeledAction(label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TransportButton(Icons.Filled.PlayArrow, label, size = 44, filled = true, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, color = RoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
