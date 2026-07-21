package com.roonshield.ui.connect

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.ConnectionState
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.TransportButton
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh

@Composable
fun ConnectScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.connectionState.collectAsState()
    val cores by viewModel.discovered.collectAsState()

    // Kick off discovery when this screen first appears.
    LaunchedEffect(Unit) { viewModel.discover() }

    Box(modifier = modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp),
        ) {
            Text("Roon Shield", color = RoonAccent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is ConnectionState.WaitingForAuthorization -> AuthorizationPanel()
                is ConnectionState.Connecting -> StatusLine("Connecting to ${s.host}…", showSpinner = true)
                is ConnectionState.Error -> StatusLine("Not connected — ${s.message}", showSpinner = false)
                else -> StatusLine("Select your Roon Core", showSpinner = false)
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cores on your network", color = RoonTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                TransportButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Rescan",
                    size = 40,
                    onClick = { viewModel.discover() },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (cores.isEmpty()) {
                Text(
                    "No cores found yet. Make sure Roon is running on the same network, then rescan.",
                    color = RoonTextSecondary,
                    fontSize = 14.sp,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                ) {
                    items(cores, key = { it.coreId }) { core ->
                        RoonListItem(
                            title = core.name,
                            subtitle = "${core.host}:${core.port}  ·  Roon ${core.displayVersion}",
                            onClick = { viewModel.connectDiscovered(core) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            ManualEntry(onConnect = { host, port -> viewModel.connect(host, port) })
        }
    }
}

@Composable
private fun StatusLine(text: String, showSpinner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSpinner) {
            CircularProgressIndicator(color = RoonAccent, modifier = Modifier.width(20.dp).height(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = RoonTextSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun AuthorizationPanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Waiting for authorization", color = RoonAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "On any Roon remote, open  Settings → Extensions  and click  Enable  next to \"Roon Shield\". " +
                "This is only needed the first time.",
            color = RoonTextSecondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun ManualEntry(onConnect: (String, Int) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9330") }
    Column {
        Text("Or enter a Core address manually", color = RoonTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP") },
                singleLine = true,
                modifier = Modifier.width(300.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(12.dp))
            RoonListItem(
                title = "Connect",
                subtitle = null,
                modifier = Modifier.width(160.dp),
                onClick = {
                    val p = port.toIntOrNull()
                    if (host.isNotBlank() && p != null) onConnect(host.trim(), p)
                },
            )
        }
    }
}
