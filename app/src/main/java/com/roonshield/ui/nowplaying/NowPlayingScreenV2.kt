package com.roonshield.ui.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.Zone
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.delay

/**
 * Full-screen "display" now playing: the album art fills the full height on the left, and the
 * track/artist/album details sit small in the lower-right, clear of the art. No nav rail or footer
 * (the host hides them). Hardware media keys + the on-screen controls drive playback; Back exits.
 */
@Composable
fun NowPlayingScreenV2(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val zone by viewModel.activeZone.collectAsState()
    if (zone == null) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No zone selected.", color = RoonTextSecondary, fontSize = 16.sp)
        }
        return
    }
    val z = zone!!
    val np = z.nowPlaying
    val lines = np?.threeLine ?: np?.twoLine ?: np?.oneLine
    val settings = z.settings
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(z.zoneId) { runCatching { playFocus.requestFocus() } }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Blurred full-bleed backdrop derived from the art, darkened.
        AlbumArt(np?.imageKey, sizePx = 80, showPlaceholder = false, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0x4D000000), Color(0xF0000000))),
            ),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Full-height album art (square) on the left.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
            ) {
                AlbumArt(np?.imageKey, sizePx = 1080, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
            }

            // Remaining space: artist photos top-right, details bottom-right.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val artistKeys = np?.artistImageKeys ?: emptyList()
                if (artistKeys.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 40.dp, end = 44.dp)
                            .fillMaxHeight(0.52f)
                            .aspectRatio(1.3f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
                    ) {
                        ArtistPhotos(artistKeys, viewModel::loadArt)
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 44.dp, bottom = 40.dp)
                        .widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = (z.displayName ?: "").uppercase(),
                        color = RoonAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = lines?.line1 ?: "Nothing playing",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, lineHeight = 28.sp,
                    )
                    lines?.line2?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    }
                    lines?.line3?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, color = RoonTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    }

                    Spacer(Modifier.height(16.dp))
                    Scrubber(np?.seekPosition ?: 0, np?.length ?: 0, viewModel::seekBy, viewModel::playPause)

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TransportButton(Icons.Filled.Shuffle, "Shuffle", size = 38, active = settings?.shuffle == true, onClick = viewModel::toggleShuffle)
                        TransportButton(Icons.Filled.SkipPrevious, "Previous", size = 42, onClick = viewModel::previous)
                        TransportButton(
                            icon = if (z.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            size = 54, filled = true,
                            modifier = Modifier.focusRequester(playFocus),
                            onClick = viewModel::playPause,
                        )
                        TransportButton(Icons.Filled.SkipNext, "Next", size = 42, onClick = viewModel::next)
                        val loop = settings?.loop
                        TransportButton(
                            icon = if (loop == "loop_one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat", size = 38,
                            active = loop == "loop" || loop == "loop_one", onClick = viewModel::cycleLoop,
                        )
                        TransportButton(Icons.Filled.Radio, "Roon Radio", size = 38, active = settings?.autoRadio == true, onClick = viewModel::toggleRadio)
                    }
                }
            }
        }
    }
}

/** Cuts between the artist's photos in the top-right, cross-fading every few seconds. */
@Composable
private fun ArtistPhotos(imageKeys: List<String>, fetch: suspend (String, Int, Int) -> ByteArray?) {
    var index by remember(imageKeys) { mutableStateOf(0) }
    LaunchedEffect(imageKeys) {
        if (imageKeys.size > 1) {
            while (true) {
                delay(9000)
                index = (index + 1) % imageKeys.size
            }
        }
    }
    val key = imageKeys.getOrNull(index.coerceIn(0, imageKeys.lastIndex.coerceAtLeast(0))) ?: return
    Crossfade(targetState = key, animationSpec = tween(900), label = "artist") { k ->
        AlbumArt(imageKey = k, sizePx = 640, showPlaceholder = false, fetch = fetch, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Scrubber(position: Int, length: Int, onSeek: (Int) -> Unit, onPlayPause: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 8.dp else 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .onFocusState { focused = it }
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> { onSeek(-10); true }
                        Key.DirectionRight -> { onSeek(10); true }
                        Key.DirectionCenter, Key.Enter -> { onPlayPause(); true }
                        else -> false
                    }
                },
        ) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(if (focused) RoonAccent else RoonTextPrimary))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(position), color = RoonTextSecondary, fontSize = 12.sp)
            Text(if (focused) "◀ / ▶ seek" else "-" + fmt((length - position).coerceAtLeast(0)), color = if (focused) RoonAccent else RoonTextSecondary, fontSize = 12.sp)
        }
    }
}

private fun fmt(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
