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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.roonshield.roon.ConnectionState
import com.roonshield.ui.browse.BrowseScreen
import com.roonshield.ui.browse.SearchScreen
import com.roonshield.ui.components.NavRailItem
import com.roonshield.ui.components.RoonLogo
import com.roonshield.ui.connect.ConnectScreen
import com.roonshield.ui.nowplaying.NowPlayingScreen
import com.roonshield.ui.queue.QueueScreen
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonBackground
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun RoonApp(viewModel: RoonViewModel) {
    val connection by viewModel.connectionState.collectAsState()
    val booting by viewModel.booting.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(RoonBackground)) {
        when {
            connection is ConnectionState.Connected -> MainScaffold(viewModel)
            // Show a splash (not the discovery screen) while the silent reconnect runs.
            booting && connection is ConnectionState.Idle -> BootSplash()
            else -> ConnectScreen(viewModel)
        }
    }
}

@Composable
private fun BootSplash() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoonLogo(height = 40.dp, tint = RoonTextPrimary)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = RoonAccent, modifier = Modifier.height(24.dp).width(24.dp))
        }
    }
}

private data class NavEntry(val destination: Destination, val icon: ImageVector)

@Composable
private fun MainScaffold(viewModel: RoonViewModel) {
    var selected by remember { mutableStateOf(Destination.NowPlaying) }
    val activeZone by viewModel.activeZone.collectAsState()
    val firstNavFocus = remember { FocusRequester() }

    // Give the controller a focus anchor on the nav rail when the app opens, so the first
    // D-pad press does something instead of being swallowed by an unfocused tree.
    LaunchedEffect(Unit) { runCatching { firstNavFocus.requestFocus() } }

    val entries = listOf(
        NavEntry(Destination.NowPlaying, Icons.Filled.PlayCircle),
        NavEntry(Destination.Browse, Icons.Filled.LibraryMusic),
        NavEntry(Destination.Search, Icons.Filled.Search),
        NavEntry(Destination.Queue, Icons.AutoMirrored.Filled.QueueMusic),
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
            RoonLogo(height = 22.dp, tint = RoonTextPrimary)
            Spacer(Modifier.height(16.dp))
            entries.forEachIndexed { index, entry ->
                NavRailItem(
                    label = entry.destination.label,
                    icon = entry.icon,
                    selected = selected == entry.destination,
                    modifier = if (index == 0) Modifier.focusRequester(firstNavFocus) else Modifier,
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
            }
        }
    }
}
