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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roonshield.roon.ConnectionState
import com.roonshield.ui.browse.SearchScreen
import com.roonshield.ui.components.BrowsePane
import com.roonshield.ui.components.BrowseStyle
import com.roonshield.ui.components.NavRailItem
import com.roonshield.ui.components.NowPlayingBar
import com.roonshield.ui.components.RoonLogo
import com.roonshield.ui.connect.ConnectScreen
import com.roonshield.ui.home.HomeScreen
import com.roonshield.ui.nowplaying.NowPlayingScreen
import com.roonshield.ui.queue.QueueScreen
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonBackground
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.zones.ZoneOverlay

private data class NavItem(val id: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home", "Home", Icons.Filled.Home),
    NavItem("albums", "Albums", Icons.Filled.Album),
    NavItem("artists", "Artists", Icons.Filled.Groups),
    NavItem("genres", "Genres", Icons.Filled.Category),
    NavItem("composers", "Composers", Icons.Filled.MusicNote),
    NavItem("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    NavItem("radio", "Live Radio", Icons.Filled.Radio),
    NavItem("browse", "Browse", Icons.Filled.LibraryMusic),
    NavItem("search", "Search", Icons.Filled.Search),
    NavItem("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    NavItem("settings", "Settings", Icons.Filled.Settings),
    NavItem("nowplaying", "Now Playing", Icons.Filled.PlayCircle),
)

@Composable
fun RoonApp(viewModel: RoonViewModel) {
    val connection by viewModel.connectionState.collectAsState()
    val booting by viewModel.booting.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(RoonBackground)) {
        when {
            connection is ConnectionState.Connected -> MainScaffold(viewModel)
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

@Composable
private fun MainScaffold(viewModel: RoonViewModel) {
    var selected by remember { mutableStateOf("home") }
    var showZones by remember { mutableStateOf(false) }
    val activeZone by viewModel.activeZone.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val activeZoneId by viewModel.activeZoneId.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Scrollable native-style nav rail
            Column(
                modifier = Modifier
                    .width(228.dp)
                    .fillMaxHeight()
                    .background(RoonSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoonLogo(height = 22.dp, tint = RoonTextPrimary)
                Spacer(Modifier.height(14.dp))
                NAV_ITEMS.forEach { item ->
                    NavRailItem(
                        label = item.label,
                        icon = item.icon,
                        selected = selected == item.id,
                        onClick = { selected = item.id },
                    )
                }
            }

            // Content + footer
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Content(selected, viewModel, onSelect = { selected = it })
                }
                if (selected != "nowplaying") {
                    NowPlayingBar(
                        zone = activeZone,
                        fetchArt = viewModel::loadArt,
                        onOpenNowPlaying = { selected = "nowplaying" },
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onOpenZones = { showZones = true },
                    )
                }
            }
        }

        if (showZones) {
            ZoneOverlay(
                zones = zones,
                activeZoneId = activeZoneId,
                onSelect = { viewModel.setZone(it); showZones = false },
                onNudgeVolume = viewModel::nudgeVolume,
                onToggleMute = viewModel::toggleMute,
                onClose = { showZones = false },
            )
        }
    }
}

@Composable
private fun Content(selected: String, viewModel: RoonViewModel, onSelect: (String) -> Unit) {
    when (selected) {
        "home" -> HomeScreen(viewModel, onNavigate = onSelect)
        "albums" -> BrowsePane(viewModel, sessionKey = "albums", rootHierarchy = "albums", style = BrowseStyle.Grid, rootTitle = "Albums")
        "artists" -> BrowsePane(viewModel, sessionKey = "artists", rootHierarchy = "artists", style = BrowseStyle.Circle, rootTitle = "Artists")
        "genres" -> BrowsePane(viewModel, sessionKey = "genres", rootHierarchy = "genres", style = BrowseStyle.Grid, rootTitle = "Genres")
        "composers" -> BrowsePane(viewModel, sessionKey = "composers", rootHierarchy = "composers", style = BrowseStyle.Circle, rootTitle = "Composers")
        "playlists" -> BrowsePane(viewModel, sessionKey = "playlists", rootHierarchy = "playlists", style = BrowseStyle.List, rootTitle = "Playlists")
        "radio" -> BrowsePane(viewModel, sessionKey = "radio", rootHierarchy = "internet_radio", style = BrowseStyle.List, rootTitle = "Live Radio")
        "browse" -> BrowsePane(viewModel, sessionKey = "browse", rootHierarchy = "browse", style = BrowseStyle.Grid, rootTitle = "Browse")
        "search" -> SearchScreen(viewModel)
        "queue" -> QueueScreen(viewModel)
        "settings" -> BrowsePane(viewModel, sessionKey = "settings", rootHierarchy = "settings", style = BrowseStyle.List, rootTitle = "Settings")
        "nowplaying" -> NowPlayingScreen(viewModel)
    }
}
