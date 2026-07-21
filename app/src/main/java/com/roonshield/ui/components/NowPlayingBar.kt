package com.roonshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.Zone
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** Persistent bottom transport bar shown across the main tabs. */
@Composable
fun NowPlayingBar(
    zone: Zone?,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenZones: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val np = zone?.nowPlaying
    val lines = np?.twoLine ?: np?.oneLine
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(RoonSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenNowPlaying),
        ) {
            AlbumArt(imageKey = np?.imageKey, sizePx = 128, fetch = fetchArt, modifier = Modifier)
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .weight(1f)
                .clickable(onClick = onOpenNowPlaying),
        ) {
            Text(
                text = lines?.line1 ?: "Nothing playing",
                color = RoonTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!lines?.line2.isNullOrEmpty()) {
                Text(
                    text = lines.line2!!,
                    color = RoonTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarButton(Icons.Filled.Speaker, "Zones", onOpenZones)
            BarButton(
                icon = if (zone?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = "Play/Pause",
                onClick = onPlayPause,
                accent = true,
            )
            BarButton(Icons.Filled.SkipNext, "Next", onNext)
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> RoonAccent
        accent -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .onFocusState { focused = it }
            .clickable(onClick = onClick),
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) Color.White else RoonTextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}
