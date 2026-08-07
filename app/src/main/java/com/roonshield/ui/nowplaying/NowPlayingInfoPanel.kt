package com.roonshield.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextSecondary
import com.roonshield.wiki.WikiEntry
import com.roonshield.wiki.WikipediaService
import kotlinx.coroutines.launch

enum class InfoTab { Album, Artist }

/** Multi-artist lines come as "A / B" or "A feat. B" — the lookup wants the primary artist. */
internal fun primaryArtist(artist: String?): String? =
    artist?.substringBefore(" / ")?.substringBefore(" feat.")?.trim()?.takeIf { it.isNotBlank() }

/**
 * Right-side overlay on Now Playing with the Wikipedia entry for the current album or artist
 * (Roon's public API exposes none of the native app's TiVo review/bio text). D-pad ◀/▶ switches
 * between the album and artist entries, ▲/▼ scrolls, Back (or ◀ from the album tab) closes.
 */
@Composable
internal fun NowPlayingInfoPanel(
    tab: InfoTab,
    album: String?,
    artist: String?,
    onSwitchTab: (InfoTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    var loading by remember(tab, album, artist) { mutableStateOf(true) }
    var entry by remember(tab, album, artist) { mutableStateOf<WikiEntry?>(null) }
    LaunchedEffect(tab, album, artist) {
        loading = true
        entry = when (tab) {
            InfoTab.Album -> album?.takeIf { it.isNotBlank() }?.let { WikipediaService.albumInfo(it, primaryArtist(artist)) }
            InfoTab.Artist -> primaryArtist(artist)?.let { WikipediaService.artistInfo(it) }
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(480.dp)
            .background(Color(0xF5121214))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionRight -> { onSwitchTab(if (tab == InfoTab.Album) InfoTab.Artist else InfoTab.Album); true }
                    Key.DirectionLeft -> { if (tab == InfoTab.Artist) onSwitchTab(InfoTab.Album) else onClose(); true }
                    Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(260f) }; true }
                    Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-260f) }; true }
                    else -> false
                }
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row {
            TabLabel("ALBUM", active = tab == InfoTab.Album)
            Spacer(Modifier.width(18.dp))
            TabLabel("ARTIST", active = tab == InfoTab.Artist)
        }
        Spacer(Modifier.height(16.dp))

        val subject = if (tab == InfoTab.Album) album else primaryArtist(artist)
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = RoonAccent, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Looking up ${subject ?: "…"}", color = RoonTextSecondary, fontSize = 14.sp)
                }
                entry == null -> Text(
                    if (subject.isNullOrBlank()) "Nothing to look up for this stream."
                    else "No Wikipedia entry found for \"$subject\".",
                    color = RoonTextSecondary, fontSize = 15.sp,
                )
                else -> Column {
                    Text(entry!!.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Column(modifier = Modifier.verticalScroll(scroll)) {
                        Text(
                            entry!!.extract,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "From Wikipedia   ·   ◀ ▶ album / artist   ·   ▲ ▼ scroll   ·   Back closes",
            color = RoonTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TabLabel(label: String, active: Boolean) {
    Text(
        label,
        color = if (active) RoonAccent else RoonTextSecondary,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
    )
}
