package com.roonshield.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun QueueScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val queue by viewModel.queue.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Queue", color = RoonTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("The queue is empty.", color = RoonTextSecondary, fontSize = 15.sp)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { index, item -> item.queueItemId ?: index.toLong() }) { index, item ->
                val lines = item.twoLine ?: item.oneLine ?: item.threeLine
                RoonListItem(
                    title = lines?.line1 ?: "—",
                    subtitle = lines?.line2,
                    imageKey = item.imageKey,
                    fetchArt = viewModel::loadArt,
                    trailing = {
                        Text("#${index + 1}", color = RoonTextSecondary, fontSize = 13.sp)
                    },
                    onClick = { item.queueItemId?.let { viewModel.playFromQueue(it) } },
                )
            }
        }
    }
}
