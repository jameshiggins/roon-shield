package com.roonshield.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun ZonePickerScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val zones by viewModel.zones.collectAsState()
    val activeId by viewModel.activeZoneId.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Zones", color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (zones.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No zones available yet.", color = RoonTextSecondary, fontSize = 15.sp)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(zones, key = { it.zoneId }) { zone ->
                val np = zone.nowPlaying?.twoLine ?: zone.nowPlaying?.oneLine
                val summary = when {
                    np?.line1 != null -> "${zone.state ?: ""} · ${np.line1}"
                    else -> zone.state ?: "idle"
                }
                RoonListItem(
                    title = zone.displayName ?: "Zone",
                    subtitle = summary,
                    trailing = {
                        if (zone.zoneId == activeId) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = RoonAccent)
                        }
                    },
                    onClick = { viewModel.setZone(zone.zoneId) },
                )
            }
        }
    }
}
