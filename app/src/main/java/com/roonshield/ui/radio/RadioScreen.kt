package com.roonshield.ui.radio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.PosterCard
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

/**
 * Live Radio: browse internet radio stations. Root shows a grid of station poster cards; most
 * stations are direct-play (`hint == "action"`) so selecting one starts playback and shows a
 * transient "Playing …" confirmation. Category items drill in (pushed onto the stack). Back goes up.
 */
@Composable
fun RadioScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("radio", "internet_radio") }
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var loading by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val p = session.home()
        stack.clear()
        stack.add(p)
        loading = false
    }

    val depth = stack.size

    BackHandler(enabled = depth > 1) {
        scope.launch {
            loading = true
            val parent = session.back()
            if (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
                stack[stack.lastIndex] = parent
            }
            loading = false
        }
    }

    // Auto-dismiss the transient confirmation.
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2600)
            toast = null
        }
    }

    fun onItem(item: BrowseItem) {
        val key = item.itemKey ?: return
        scope.launch {
            loading = true
            val next = session.open(key)
            if (item.hint == "action") {
                // Direct-play station: playback already invoked, just confirm.
                toast = "Playing ${item.title ?: next.title.ifEmpty { "station" }}"
            } else if (next.items.isNotEmpty() || next.message == null) {
                // Category / sub-list: drill in.
                stack.add(next)
            } else if (next.message != null) {
                toast = next.message
            }
            loading = false
        }
    }

    val page = stack.lastOrNull()
    val items = page?.items ?: emptyList()

    // Anchor for the D-pad: focus the first station once a level loads.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(page, items.size) {
        if (items.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = page?.title?.ifEmpty { "Live Radio" } ?: "Live Radio",
                    color = RoonTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (depth > 1) {
                    Spacer(Modifier.width(12.dp))
                    Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp)
                }
                if (loading) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                items.isEmpty() && !loading -> EmptyState(page?.message)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    itemsIndexed(
                        items,
                        key = { _, it -> it.itemKey ?: it.title.orEmpty() },
                    ) { index, item ->
                        PosterCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageKey = item.imageKey,
                            fetchArt = viewModel::loadArt,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onItem(item) },
                        )
                    }
                }
            }
        }

        // Transient "Playing …" confirmation.
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        ) {
            NowPlayingToast(text = toast.orEmpty())
        }
    }
}

@Composable
private fun EmptyState(message: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message ?: "No stations here.",
            color = RoonTextSecondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun NowPlayingToast(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(RoonSurfaceVariant)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = RoonAccent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = RoonTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
