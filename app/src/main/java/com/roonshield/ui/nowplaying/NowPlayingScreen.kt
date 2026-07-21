package com.roonshield.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.Zone
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.AlbumArt
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
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
    val output = zone.outputs.firstOrNull()
    val muted = output?.volume?.isMuted == true

    Row(modifier = modifier.fillMaxSize().padding(40.dp)) {
        // Album art
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            AlbumArt(
                imageKey = np?.imageKey,
                sizePx = 720,
                fetch = viewModel::loadArt,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(40.dp))

        // Metadata + controls
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                text = zone.displayName ?: "Zone",
                color = RoonAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, color = RoonTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!artist.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(artist, color = RoonTextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!album.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(album, color = RoonTextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(24.dp))
            SeekBar(position = np?.seekPosition ?: 0, length = np?.length ?: 0)

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TransportButton(Icons.Filled.Replay10, "Back 10 seconds", size = 48, onClick = { viewModel.seekBy(-10) })
                TransportButton(Icons.Filled.SkipPrevious, "Previous", onClick = viewModel::previous)
                TransportButton(
                    icon = if (zone.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    size = 72,
                    onClick = viewModel::playPause,
                )
                TransportButton(Icons.Filled.SkipNext, "Next", onClick = viewModel::next)
                TransportButton(Icons.Filled.Forward10, "Forward 10 seconds", size = 48, onClick = { viewModel.seekBy(10) })
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TransportButton(Icons.Filled.VolumeDown, "Volume down", size = 44, onClick = viewModel::volumeDown)
                TransportButton(
                    icon = if (muted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                    contentDescription = "Mute",
                    size = 44,
                    onClick = viewModel::toggleMute,
                )
                TransportButton(Icons.Filled.VolumeUp, "Volume up", size = 44, onClick = viewModel::volumeUp)
            }
        }
    }
}

@Composable
private fun SeekBar(position: Int, length: Int) {
    val progress = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
        LinearProgressIndicator(
            progress = { progress },
            color = RoonAccent,
            trackColor = RoonSurfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = RoonTextSecondary, fontSize = 12.sp)
            Text(formatTime(length), color = RoonTextSecondary, fontSize = 12.sp)
        }
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
