package com.roonshield.ui.zones

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.Zone
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** Modal overlay: choose the active zone and adjust its volume. */
@Composable
fun ZoneOverlay(
    zones: List<Zone>,
    activeZoneId: String?,
    onSelect: (String) -> Unit,
    onNudgeVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RoonSurface)
                .padding(24.dp)
                .clickable(enabled = false) {},
        ) {
            Text("Zones", color = RoonTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().height(280.dp),
            ) {
                items(zones, key = { it.zoneId }) { zone ->
                    val np = zone.nowPlaying?.twoLine ?: zone.nowPlaying?.oneLine
                    RoonListItem(
                        title = zone.displayName ?: "Zone",
                        subtitle = np?.line1?.let { "${zone.state ?: ""} · $it" } ?: (zone.state ?: "idle"),
                        trailing = {
                            if (zone.zoneId == activeZoneId) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = RoonAccent)
                            }
                        },
                        onClick = { onSelect(zone.zoneId) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val active = zones.firstOrNull { it.zoneId == activeZoneId }
            val muted = active?.outputs?.firstOrNull()?.volume?.isMuted == true
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Volume", color = RoonTextSecondary, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                TransportButton(Icons.Filled.VolumeDown, "Volume down", size = 46) { onNudgeVolume(-2) }
                TransportButton(
                    icon = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Mute",
                    size = 46,
                    onClick = onToggleMute,
                )
                TransportButton(Icons.Filled.VolumeUp, "Volume up", size = 46) { onNudgeVolume(2) }
            }
        }
    }
}
