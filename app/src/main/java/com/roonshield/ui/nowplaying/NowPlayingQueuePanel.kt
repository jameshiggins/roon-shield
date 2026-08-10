package com.roonshield.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/**
 * Queue overlay on Now Playing: previously played tracks (observed this app run — Roon's API
 * serves no history), the current track, and the upcoming queue. Selecting an upcoming track
 * plays from there. ▲/▼ move through the list, Back (or ◀) closes.
 *
 * Roon's public API cannot reorder queue items, so there is deliberately no "move" affordance.
 */
@Composable
internal fun NowPlayingQueuePanel(
    viewModel: RoonViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by viewModel.queue.collectAsState()
    val zone by viewModel.activeZone.collectAsState()
    val historyByZone by viewModel.recentlyPlayed.collectAsState()
    val history = zone?.zoneId?.let { historyByZone[it] }.orEmpty()

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(queue.size > 1) {
        if (queue.size > 1) runCatching { firstFocus.requestFocus() }
    }

    val nowPlaying = queue.firstOrNull()
    val upcoming = if (queue.isEmpty()) emptyList() else queue.drop(1)
    val remainingSec = upcoming.sumOf { it.length ?: 0 }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(520.dp)
            .background(Color(0xF5121214))
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    onClose(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("QUEUE", color = RoonAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                text = zone?.displayName ?: "",
                color = RoonTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (upcoming.isEmpty()) "Nothing up next" else "${upcoming.size} up next  ·  ${fmtDuration(remainingSec)} remaining",
            color = RoonTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))

        if (queue.isEmpty() && history.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("The queue is empty.", color = RoonTextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (history.isNotEmpty()) {
                    item { SectionLabel("PREVIOUSLY PLAYED") }
                    itemsIndexed(history.asReversed(), key = { i, _ -> "hist-$i" }) { _, played ->
                        HistoryRow(played.title ?: "—", played.subtitle, played.imageKey, viewModel)
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                if (nowPlaying != null) {
                    item { SectionLabel("NOW PLAYING") }
                    item { NowPlayingRow(nowPlaying, viewModel) }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                if (upcoming.isNotEmpty()) {
                    item { SectionLabel("UP NEXT") }
                    itemsIndexed(upcoming, key = { i, it -> it.queueItemId ?: i.toLong() }) { index, item ->
                        RoonListItem(
                            title = item.oneLine?.line1 ?: item.twoLine?.line1 ?: "—",
                            subtitle = listOfNotNull(item.twoLine?.line2, item.length?.let(::fmtDuration)).joinToString("  ·  ").ifEmpty { null },
                            imageKey = item.imageKey,
                            fetchArt = viewModel::loadArt,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { item.queueItemId?.let(viewModel::playFromQueue) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "OK plays from a track   ·   ▲ ▼ browse   ·   Back closes",
            color = RoonTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = RoonTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
}

/** Dimmed, non-interactive row for a track that already played. */
@Composable
private fun HistoryRow(title: String, subtitle: String?, imageKey: String?, viewModel: RoonViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp),
    ) {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp))) {
            AlbumArt(imageKey, sizePx = 96, fetch = viewModel::loadArt, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = RoonTextPrimary.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = RoonTextSecondary.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun NowPlayingRow(item: QueueItem, viewModel: RoonViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RoonAccent.copy(alpha = 0.16f))
            .padding(vertical = 7.dp, horizontal = 8.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))) {
            AlbumArt(item.imageKey, sizePx = 120, fetch = viewModel::loadArt, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(item.oneLine?.line1 ?: item.twoLine?.line1 ?: "—", color = RoonAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.twoLine?.line2?.let { Text(it, color = RoonTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun fmtDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
