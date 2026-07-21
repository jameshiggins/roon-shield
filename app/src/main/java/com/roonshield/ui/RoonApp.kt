package com.roonshield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.roonshield.roon.ConnectionState
import com.roonshield.ui.browse.BrowseScreen
import com.roonshield.ui.browse.SearchScreen
import com.roonshield.ui.components.NavRailItem
import com.roonshield.ui.connect.ConnectScreen
import com.roonshield.ui.nowplaying.NowPlayingScreen
import com.roonshield.ui.queue.QueueScreen
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonBackground
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextSecondary
import com.roonshield.ui.zones.ZonePickerScreen

@Composable
fun RoonApp(viewModel: RoonViewModel) {
    val connection by viewModel.connectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(RoonBackground)) {
        if (connection is ConnectionState.Connected) {
            MainScaffold(viewModel)
        } else {
            ConnectScreen(viewModel)
        }
    }
}

private data class NavEntry(val destination: Destination, val icon: ImageVector)

@Composable
private fun MainScaffold(viewModel: RoonViewModel) {
    var selected by remember { mutableStateOf(Destination.NowPlaying) }
    val activeZone by viewModel.activeZone.collectAsState()

    val entries = listOf(
        NavEntry(Destination.NowPlaying, Icons.Filled.PlayCircle),
        NavEntry(Destination.Browse, Icons.Filled.LibraryMusic),
        NavEntry(Destination.Search, Icons.Filled.Search),
        NavEntry(Destination.Queue, Icons.AutoMirrored.Filled.QueueMusic),
        NavEntry(Destination.Zones, Icons.Filled.Speaker),
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation rail
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(RoonSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Roon Shield", color = RoonAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            entries.forEach { entry ->
                NavRailItem(
                    label = entry.destination.label,
                    icon = entry.icon,
                    selected = selected == entry.destination,
                    onClick = { selected = entry.destination },
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = activeZone?.displayName ?: "No zone",
                color = RoonTextSecondary,
                fontSize = 12.sp,
            )
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selected) {
                Destination.NowPlaying -> NowPlayingScreen(viewModel)
                Destination.Browse -> BrowseScreen(viewModel)
                Destination.Search -> SearchScreen(viewModel)
                Destination.Queue -> QueueScreen(viewModel)
                Destination.Zones -> ZonePickerScreen(viewModel)
            }
        }
    }
}
