package com.roonshield.ui.listenlater

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/**
 * App-local "Listen Later" bookmarks. Selecting an entry re-resolves it via Search (item_keys are
 * session-scoped and can't be replayed directly), landing on results you can play.
 */
@Composable
fun ListenLaterScreen(
    viewModel: RoonViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.listenLater.collectAsState()
    val firstFocus = remember { FocusRequester() }
    // D-pad anchor: land focus on the first saved entry when the screen opens / entries arrive.
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Listen Later", color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.WatchLater, contentDescription = null, tint = RoonTextSecondary, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing saved yet.", color = RoonTextSecondary, fontSize = 15.sp)
                    Text("Open the actions on any track or album and choose \"Add to Listen Later\".", color = RoonTextSecondary, fontSize = 13.sp)
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(entries, key = { _, it -> it.title }) { index, entry ->
                RoonListItem(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    imageKey = entry.imageKey,
                    fetchArt = viewModel::loadArt,
                    trailing = { RemoveButton { viewModel.removeListenLater(entry.title) } },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        viewModel.requestSearch(entry.query)
                        onNavigate("search")
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (focused) RoonAccent else RoonSurfaceVariant)
            .onFocusState { focused = it }
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = if (focused) Color.White else RoonTextSecondary, modifier = Modifier.size(18.dp))
    }
}
