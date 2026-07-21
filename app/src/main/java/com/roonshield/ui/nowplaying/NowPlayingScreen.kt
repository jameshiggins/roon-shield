package com.roonshield.ui.nowplaying

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material3.Text
import com.roonshield.roon.model.Zone
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun NowPlayingScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val zone by viewModel.activeZone.collectAsState()
    if (zone == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No zone selected. Open Zones to pick an output.", color = RoonTextSecondary, fontSize = 16.sp)
        }
        return
    }
    NowPlayingContent(zone!!, viewModel, modifier)
}

@Composable
private fun NowPlayingContent(zone: Zone, viewModel: RoonViewModel, modifier: Modifier) {
    val np = zone.nowPlaying
    val lines = np?.threeLine ?: np?.twoLine ?: np?.oneLine
    val title = lines?.line1 ?: "Nothing playing"
    val artist = lines?.line2
    val album = lines?.line3
    val settings = zone.settings

    Box(modifier = modifier.fillMaxSize()) {
        // Blurred album-art backdrop (a tiny image stretched up + dark scrim), the Roon look.
        AlbumArt(
            imageKey = np?.imageKey,
            sizePx = 64,
            showPlaceholder = false,
            fetch = viewModel::loadArt,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xE60B0C10), Color(0xF20B0C10))),
            ),
        )

        Row(modifier = Modifier.fillMaxSize().padding(56.dp)) {
            // Crisp cover
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.86f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AlbumArt(
                    imageKey = np?.imageKey,
                    sizePx = 800,
                    fetch = viewModel::loadArt,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(48.dp))

            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = (zone.displayName ?: "Zone").uppercase(),
                    color = RoonAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(18.dp))
                Text(title, color = RoonTextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp)
                if (!artist.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(artist, color = RoonTextPrimary.copy(alpha = 0.9f), fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!album.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(album, color = RoonTextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(28.dp))
                Scrubber(
                    position = np?.seekPosition ?: 0,
                    length = np?.length ?: 0,
                    onSeek = viewModel::seekBy,
                    onPlayPause = viewModel::playPause,
                )

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TransportButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        size = 46,
                        active = settings?.shuffle == true,
                        onClick = viewModel::toggleShuffle,
                    )
                    TransportButton(Icons.Filled.SkipPrevious, "Previous", size = 56, onClick = viewModel::previous)
                    TransportButton(
                        icon = if (zone.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        size = 76,
                        filled = true,
                        onClick = viewModel::playPause,
                    )
                    TransportButton(Icons.Filled.SkipNext, "Next", size = 56, onClick = viewModel::next)
                    val loop = settings?.loop
                    TransportButton(
                        icon = if (loop == "loop_one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        size = 46,
                        active = loop == "loop" || loop == "loop_one",
                        onClick = viewModel::cycleLoop,
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportButton(
                        icon = Icons.Filled.Radio,
                        contentDescription = "Roon Radio",
                        size = 46,
                        active = settings?.autoRadio == true,
                        onClick = viewModel::toggleRadio,
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrubber(position: Int, length: Int, onSeek: (Int) -> Unit, onPlayPause: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f

    Column(modifier = Modifier.fillMaxWidth(0.92f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
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
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (focused) RoonAccent else RoonTextPrimary),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = RoonTextSecondary, fontSize = 13.sp)
            Text(
                text = if (focused) "◀ / ▶ seek" else "-" + formatTime((length - position).coerceAtLeast(0)),
                color = if (focused) RoonAccent else RoonTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
