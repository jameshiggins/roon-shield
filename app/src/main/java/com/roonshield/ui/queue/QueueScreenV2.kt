package com.roonshield.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.Lines
import com.roonshield.roon.model.QueueItem
import com.roonshield.roon.model.Zone
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun QueueScreenV2(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val queue by viewModel.queue.collectAsState()
    val zone by viewModel.activeZone.collectAsState()

    val totalRemaining = queue.sumOf { it.length ?: 0 }
    val firstRowFocus = remember { FocusRequester() }

    // Anchor the D-pad on the first queue row once items are available.
    LaunchedEffect(queue.isNotEmpty()) {
        if (queue.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
        // Header: title + stats
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Queue", color = RoonTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text(
                text = queueStats(queue.size, totalRemaining),
                color = RoonTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        // Highlighted NOW PLAYING pin
        zone?.let { NowPlayingPin(it, viewModel) }

        // Transport controls reflecting zone state / settings
        zone?.let {
            Spacer(Modifier.height(16.dp))
            TransportBar(it, viewModel)
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("The queue is empty.", color = RoonTextSecondary, fontSize = 15.sp)
            }
            return
        }

        Spacer(Modifier.height(20.dp))
        Text("UP NEXT", color = RoonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(queue, key = { index, item -> item.queueItemId ?: index.toLong() }) { index, item ->
                val lines = pickLines(item)
                val subtitle = listOfNotNull(lines?.line2, lines?.line3)
                    .joinToString(" · ").ifEmpty { null }
                val rowModifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier
                RoonListItem(
                    title = lines?.line1 ?: "—",
                    subtitle = subtitle,
                    modifier = rowModifier,
                    imageKey = item.imageKey,
                    fetchArt = viewModel::loadArt,
                    trailing = {
                        Text(formatDuration(item.length), color = RoonTextSecondary, fontSize = 13.sp)
                    },
                    onClick = { item.queueItemId?.let { viewModel.playFromQueue(it) } },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingPin(zone: Zone, viewModel: RoonViewModel) {
    val np = zone.nowPlaying ?: return
    val lines = pickLines(np.oneLine, np.twoLine, np.threeLine)
    Column {
        Text("NOW PLAYING", color = RoonAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RoonSurface)
                .border(2.dp, RoonAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            AlbumArt(
                imageKey = np.imageKey,
                sizePx = 128,
                fetch = viewModel::loadArt,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    lines?.line1 ?: "Nothing playing",
                    color = RoonTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(lines?.line2, lines?.line3).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        color = RoonTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = zone.state?.replaceFirstChar { it.uppercase() } ?: "",
                color = RoonAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun TransportBar(zone: Zone, viewModel: RoonViewModel) {
    val settings = zone.settings
    val loop = settings?.loop
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TransportButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle",
            size = 46,
            active = settings?.shuffle == true,
            onClick = viewModel::toggleShuffle,
        )
        TransportButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            size = 54,
            onClick = viewModel::previous,
        )
        TransportButton(
            icon = if (zone.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "Play/Pause",
            size = 68,
            filled = true,
            onClick = viewModel::playPause,
        )
        TransportButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            size = 54,
            onClick = viewModel::next,
        )
        TransportButton(
            icon = if (loop == "loop_one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = "Repeat",
            size = 46,
            active = loop == "loop" || loop == "loop_one",
            onClick = viewModel::cycleLoop,
        )
    }
}

private fun pickLines(item: QueueItem): Lines? =
    pickLines(item.oneLine, item.twoLine, item.threeLine)

private fun pickLines(oneLine: Lines?, twoLine: Lines?, threeLine: Lines?): Lines? =
    threeLine ?: twoLine ?: oneLine

private fun queueStats(count: Int, totalSeconds: Int): String {
    if (count == 0) return "empty"
    val noun = if (count == 1) "track" else "tracks"
    val time = when {
        totalSeconds >= 3600 -> "${totalSeconds / 3600}h ${(totalSeconds % 3600) / 60}m"
        totalSeconds >= 60 -> "${totalSeconds / 60} min"
        else -> "$totalSeconds sec"
    }
    return "$count $noun · $time remaining"
}

private fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return "--:--"
    if (s <= 0) return "--:--"
    return "%d:%02d".format(s / 60, s % 60)
}
