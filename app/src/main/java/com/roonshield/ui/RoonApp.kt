package com.roonshield.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roonshield.roon.ConnectionState
import com.roonshield.ui.browse.LibraryBrowser
import com.roonshield.ui.browse.LibraryStyle
import com.roonshield.ui.components.NavRailItem
import com.roonshield.ui.components.NowPlayingBar
import com.roonshield.ui.components.RoonLogo
import com.roonshield.ui.connect.ConnectScreen
import com.roonshield.ui.home.HomeScreen
import com.roonshield.ui.listenlater.ListenLaterScreen
import com.roonshield.ui.nowplaying.NowPlayingScreenV2
import com.roonshield.ui.queue.QueueScreenV2
import com.roonshield.ui.search.SearchScreenV2
import com.roonshield.ui.settings.SettingsScreen
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
    NavItem("listenlater", "Listen Later", Icons.Filled.WatchLater),
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
    var selected by remember { mutableStateOf(viewModel.initialTab ?: "home") }
    var lastTab by remember { mutableStateOf("home") }
    var showZones by remember { mutableStateOf(false) }
    val activeZone by viewModel.activeZone.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val activeZoneId by viewModel.activeZoneId.collectAsState()
    val firstNavFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { firstNavFocus.requestFocus() } }

    // Idle screensaver: after 15s of no input the app drops into full-screen Now Playing; any key wakes
    // it (handled in the Activity) and returns to where you were.
    val screensaver by viewModel.screensaver.collectAsState()
    var enteredViaScreensaver by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(screensaver) {
        if (screensaver) {
            if (selected != "nowplaying") { lastTab = selected; selected = "nowplaying" }
            enteredViaScreensaver = true
        } else if (enteredViaScreensaver) {
            if (selected == "nowplaying") selected = lastTab
            enteredViaScreensaver = false
        }
    }

    fun goNowPlaying() {
        if (selected != "nowplaying") lastTab = selected
        selected = "nowplaying"
    }

    // Now Playing is a full-screen display: no nav rail, no footer. Back returns to the last tab.
    if (selected == "nowplaying") {
        BackHandler { selected = lastTab }
        NowPlayingScreenV2(viewModel, modifier = Modifier.fillMaxSize())
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Scrollable native-style nav rail
            Column(
                modifier = Modifier
                    .width(228.dp)
                    .fillMaxHeight()
                    .background(RoonSurface)
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoonLogo(height = 22.dp, tint = RoonTextPrimary)
                Spacer(Modifier.height(14.dp))
                NAV_ITEMS.forEachIndexed { index, item ->
                    NavRailItem(
                        label = item.label,
                        icon = item.icon,
                        selected = selected == item.id,
                        modifier = if (index == 0) Modifier.focusRequester(firstNavFocus) else Modifier,
                        onClick = { if (item.id == "nowplaying") goNowPlaying() else selected = item.id },
                    )
                }
            }

            // Content + footer
            Column(modifier = Modifier.fillMaxSize().focusGroup()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Content(selected, viewModel, onSelect = { selected = it })
                }
                NowPlayingBar(
                    zone = activeZone,
                    fetchArt = viewModel::loadArt,
                    onOpenNowPlaying = { goNowPlaying() },
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onOpenZones = { showZones = true },
                )
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
        "albums" -> LibraryBrowser(viewModel, "albums", "albums", "Albums", style = LibraryStyle.Grid)
        "artists" -> LibraryBrowser(viewModel, "artists", "artists", "Artists", style = LibraryStyle.Circle)
        "genres" -> LibraryBrowser(viewModel, "genres", "genres", "Genres", style = LibraryStyle.Tile)
        "composers" -> LibraryBrowser(viewModel, "composers", "composers", "Composers", style = LibraryStyle.Circle)
        "playlists" -> LibraryBrowser(viewModel, "playlists", "playlists", "Playlists", style = LibraryStyle.Grid)
        "radio" -> LibraryBrowser(viewModel, "radio", "internet_radio", "Live Radio", style = LibraryStyle.Grid)
        "listenlater" -> ListenLaterScreen(viewModel, onNavigate = onSelect)
        "browse" -> LibraryBrowser(viewModel, "browse", "browse", "Browse", style = LibraryStyle.Grid)
        "search" -> SearchScreenV2(viewModel)
        "queue" -> QueueScreenV2(viewModel)
        "settings" -> SettingsScreen(viewModel)
        "nowplaying" -> NowPlayingScreenV2(viewModel)
    }
}
