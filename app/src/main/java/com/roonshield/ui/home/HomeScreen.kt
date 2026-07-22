package com.roonshield.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.browse.LibraryBrowser
import com.roonshield.ui.browse.LibraryStyle
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** Home = quick-access tiles + the Roon browse root, on the unified [LibraryBrowser]. */
@Composable
fun HomeScreen(
    viewModel: RoonViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryBrowser(
        viewModel = viewModel,
        sessionKey = "home",
        rootHierarchy = "albums",
        rootTitle = "Home",
        style = LibraryStyle.Grid,
        modifier = modifier,
        header = { QuickTiles(onNavigate) },
        randomSample = true,
    )
}

private data class Quick(val id: String, val label: String, val icon: ImageVector)

private val QUICK = listOf(
    Quick("albums", "Albums", Icons.Filled.Album),
    Quick("artists", "Artists", Icons.Filled.Groups),
    Quick("genres", "Genres", Icons.Filled.Category),
    Quick("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    Quick("radio", "Live Radio", Icons.Filled.Radio),
    Quick("listenlater", "Listen Later", Icons.Filled.WatchLater),
    Quick("search", "Search", Icons.Filled.Search),
)

@Composable
private fun QuickTiles(onNavigate: (String) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QUICK.forEach { q -> QuickTile(q, modifier = Modifier.weight(1f)) { onNavigate(q.id) } }
        }
        Spacer(Modifier.height(18.dp))
        Text("EXPLORE", color = RoonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
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
            .padding(vertical = 16.dp),
    ) {
        Icon(q.icon, contentDescription = q.label, tint = if (focused) Color.White else RoonTextPrimary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(q.label, color = if (focused) Color.White else RoonTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
