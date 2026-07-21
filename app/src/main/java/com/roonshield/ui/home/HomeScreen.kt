package com.roonshield.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
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
import com.roonshield.ui.components.PosterCard
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

private data class Quick(val id: String, val label: String, val icon: ImageVector)

private val QUICK = listOf(
    Quick("albums", "Albums", Icons.Filled.Album),
    Quick("artists", "Artists", Icons.Filled.Groups),
    Quick("genres", "Genres", Icons.Filled.Category),
    Quick("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    Quick("radio", "Live Radio", Icons.Filled.Radio),
    Quick("search", "Search", Icons.Filled.Search),
)

@Composable
fun HomeScreen(
    viewModel: RoonViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("home", "browse") }
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
    val items = page?.items ?: emptyList()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (atRoot) "Home" else (page?.title ?: "Home"), color = RoonTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!atRoot) { Spacer(Modifier.width(12.dp)); Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp) }
                if (loading) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp)) }
            }
        }

        if (atRoot) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QUICK.forEach { q -> QuickTile(q, modifier = Modifier.weight(1f)) { onNavigate(q.id) } }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("EXPLORE", color = RoonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
            PosterCard(
                title = item.title,
                subtitle = item.subtitle,
                imageKey = item.imageKey,
                fetchArt = viewModel::loadArt,
                onClick = { open(item) },
            )
        }

        if (items.isEmpty() && !loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(page?.message ?: "Nothing here.", color = RoonTextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun QuickTile(q: Quick, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = if (focused) 1.05f else 1f; scaleY = if (focused) 1.05f else 1f }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) RoonAccent else RoonSurfaceVariant)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
    ) {
        Icon(q.icon, contentDescription = q.label, tint = if (focused) Color.White else RoonTextPrimary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(q.label, color = if (focused) Color.White else RoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
