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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextSecondary
import com.roonshield.wiki.WikiEntry
import com.roonshield.wiki.WikipediaService
import kotlinx.coroutines.launch

/** One page of the info viewer: the album, or one of the performing artists. */
sealed interface InfoSubject {
    val label: String

    data class Album(val album: String, val artist: String?) : InfoSubject {
        override val label: String get() = "ALBUM"
    }

    data class Artist(val name: String) : InfoSubject {
        override val label: String get() = name.uppercase()
    }
}

/** Multi-artist lines come as "A / B" or "A feat. B" — split them into individual artists. */
internal fun splitArtists(line: String?): List<String> =
    line?.split(" / ", " feat. ", "; ")?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct()
        ?: emptyList()

/** Album first, then each performing artist (capped so the tab row stays readable). */
internal fun buildInfoSubjects(album: String?, artistLine: String?): List<InfoSubject> {
    val artists = splitArtists(artistLine)
    val subjects = mutableListOf<InfoSubject>()
    album?.takeIf { it.isNotBlank() }?.let { subjects += InfoSubject.Album(it, artists.firstOrNull()) }
    artists.take(4).forEach { subjects += InfoSubject.Artist(it) }
    return subjects
}

/**
 * Right-side overlay on Now Playing with the Wikipedia entry for the current album or artist(s)
 * (Roon's public API exposes none of the native app's TiVo review/bio text). ◀/▶ moves between
 * the album and each artist, ▲/▼ scrolls the text, Back closes.
 */
@Composable
internal fun NowPlayingInfoPanel(
    subjects: List<InfoSubject>,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = subjects.getOrNull(index) ?: return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    var loading by remember(subject) { mutableStateOf(true) }
    var entry by remember(subject) { mutableStateOf<WikiEntry?>(null) }
    LaunchedEffect(subject) {
        loading = true
        entry = when (subject) {
            is InfoSubject.Album -> WikipediaService.albumInfo(subject.album, subject.artist)
            is InfoSubject.Artist -> WikipediaService.artistInfo(subject.name)
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
                    Key.DirectionRight -> { onSelect((index + 1) % subjects.size); true }
                    Key.DirectionLeft -> { onSelect((index - 1 + subjects.size) % subjects.size); true }
                    Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(260f) }; true }
                    Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-260f) }; true }
                    else -> false
                }
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row {
            subjects.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.width(16.dp))
                Text(
                    s.label,
                    color = if (i == index) RoonAccent else RoonTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (i == index) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val name = when (subject) {
            is InfoSubject.Album -> subject.album
            is InfoSubject.Artist -> subject.name
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = RoonAccent, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Looking up $name…", color = RoonTextSecondary, fontSize = 14.sp)
                }
                entry == null -> Text(
                    "No Wikipedia entry found for \"$name\".",
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
            "From Wikipedia   ·   ◀ ▶ album / artists   ·   ▲ ▼ scroll   ·   Back closes",
            color = RoonTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
