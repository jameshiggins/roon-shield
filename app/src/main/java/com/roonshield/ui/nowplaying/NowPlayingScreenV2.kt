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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.roonshield.roon.model.QueueItem
import com.roonshield.roon.model.Zone
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun NowPlayingScreenV2(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val zone by viewModel.activeZone.collectAsState()
    val queue by viewModel.queue.collectAsState()
    if (zone == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No zone selected. Open the footer speaker icon to pick a zone.", color = RoonTextSecondary, fontSize = 16.sp)
        }
        return
    }
    val z = zone!!
    val np = z.nowPlaying
    val lines = np?.threeLine ?: np?.twoLine ?: np?.oneLine
    val settings = z.settings

    Box(modifier = modifier.fillMaxSize()) {
        AlbumArt(imageKey = np?.imageKey, sizePx = 64, showPlaceholder = false, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xE60B0C10), Color(0xF20B0C10)))))

        Row(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            // Cover
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
            ) {
                AlbumArt(imageKey = np?.imageKey, sizePx = 800, fetch = viewModel::loadArt, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(40.dp))

            // Metadata + controls
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text((z.displayName ?: "Zone").uppercase(), color = RoonAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(lines?.line1 ?: "Nothing playing", color = RoonTextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp)
                lines?.line2?.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(10.dp)); Text(it, color = RoonTextPrimary.copy(alpha = 0.9f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                lines?.line3?.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(4.dp)); Text(it, color = RoonTextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(24.dp))
                Scrubber(np?.seekPosition ?: 0, np?.length ?: 0, viewModel::seekBy, viewModel::playPause)

                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TransportButton(Icons.Filled.Shuffle, "Shuffle", size = 46, active = settings?.shuffle == true, onClick = viewModel::toggleShuffle)
                    TransportButton(Icons.Filled.SkipPrevious, "Previous", size = 54, onClick = viewModel::previous)
                    TransportButton(if (z.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", size = 74, filled = true, onClick = viewModel::playPause)
                    TransportButton(Icons.Filled.SkipNext, "Next", size = 54, onClick = viewModel::next)
                    val loop = settings?.loop
                    TransportButton(if (loop == "loop_one") Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", size = 46, active = loop == "loop" || loop == "loop_one", onClick = viewModel::cycleLoop)
                    TransportButton(Icons.Filled.Radio, "Roon Radio", size = 46, active = settings?.autoRadio == true, onClick = viewModel::toggleRadio)
                }
            }

            Spacer(Modifier.width(28.dp))
            UpNextPanel(queue = queue, viewModel = viewModel, modifier = Modifier.width(320.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun UpNextPanel(queue: List<QueueItem>, viewModel: RoonViewModel, modifier: Modifier) {
    val upcoming = if (queue.size > 1) queue.drop(1) else emptyList()
    Column(modifier = modifier) {
        Text("UP NEXT", color = RoonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            Text("Nothing queued.", color = RoonTextSecondary, fontSize = 13.sp)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(upcoming, key = { it.queueItemId ?: it.hashCode().toLong() }) { item ->
                val l = item.twoLine ?: item.oneLine ?: item.threeLine
                RoonListItem(
                    title = l?.line1 ?: "—",
                    subtitle = l?.line2,
                    imageKey = item.imageKey,
                    fetchArt = viewModel::loadArt,
                    onClick = { item.queueItemId?.let { viewModel.playFromQueue(it) } },
                )
            }
        }
    }
}

@Composable
private fun Scrubber(position: Int, length: Int, onSeek: (Int) -> Unit, onPlayPause: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth(0.94f)) {
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
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(if (focused) RoonAccent else RoonTextPrimary))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(position), color = RoonTextSecondary, fontSize = 13.sp)
            Text(if (focused) "◀ / ▶ seek" else "-" + fmt((length - position).coerceAtLeast(0)), color = if (focused) RoonAccent else RoonTextSecondary, fontSize = 13.sp)
        }
    }
}

private fun fmt(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
