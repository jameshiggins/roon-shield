package com.roonshield.ui.genres

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

@Composable
fun GenresScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("genres", "genres") }
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
        scope.launch {
            loading = true
            val next = session.open(k)
            if (item.hint != "action") stack.add(next)
            loading = false
        }
    }

    val page = stack.lastOrNull()
    val items = page?.items ?: emptyList()
    // Root genres have no art -> color tiles; deeper levels with art/tracks -> list.
    val useTiles = stack.size <= 1 && items.all { it.imageKey.isNullOrEmpty() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(page?.title?.ifEmpty { "Genres" } ?: "Genres", color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stack.size > 1) { Spacer(Modifier.size(12.dp)); Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp) }
            if (loading) { Spacer(Modifier.size(12.dp)); CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty() && !loading) page?.message?.let { Text(it, color = RoonTextSecondary, fontSize = 14.sp) }

        if (useTiles) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(190.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item -> GenreTile(item.title ?: "—") { open(item) } }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                listItems(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                    RoonListItem(
                        title = item.title ?: "—",
                        subtitle = item.subtitle,
                        imageKey = item.imageKey,
                        fetchArt = viewModel::loadArt,
                        onClick = { open(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreTile(name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val base = genreColor(name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .graphicsLayer { scaleX = if (focused) 1.04f else 1f; scaleY = if (focused) 1.04f else 1f }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f))))
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun genreColor(name: String): Color {
    val palette = listOf(
        Color(0xFF3E7BFA), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF97316),
        Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFFEAB308),
    )
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}
