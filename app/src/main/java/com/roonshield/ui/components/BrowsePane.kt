package com.roonshield.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

enum class BrowseStyle { Grid, Circle, List }

/**
 * A reusable Roon browsing pane: owns one isolated browse session, renders the current level as a
 * grid/list, drills into items, and handles Back to go up a level. Screens set [rootHierarchy] and
 * [style] and get a full native-style browser for free.
 */
@Composable
fun BrowsePane(
    vm: RoonViewModel,
    sessionKey: String,
    modifier: Modifier = Modifier,
    rootHierarchy: String = "browse",
    style: BrowseStyle = BrowseStyle.Grid,
    rootTitle: String? = null,
) {
    val scope = rememberCoroutineScope()
    val session = remember(sessionKey) { vm.browseSession(sessionKey, rootHierarchy) }
    val stack = remember(sessionKey) { mutableStateListOf<BrowsePage>() }
    var loading by remember(sessionKey) { mutableStateOf(true) }

    LaunchedEffectKey(sessionKey) {
        loading = true
        val p = session.home()
        stack.clear()
        stack.add(p)
        loading = false
    }

    val page = stack.lastOrNull()
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

    fun onItem(item: BrowseItem) {
        val key = item.itemKey ?: return
        scope.launch {
            loading = true
            val next = session.open(key)
            if (next.items.isNotEmpty() || next.message == null) {
                stack.add(next)
            }
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = page?.title?.ifEmpty { rootTitle ?: "Browse" } ?: (rootTitle ?: "Browse"),
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

        Spacer(Modifier.height(12.dp))

        val items = page?.items ?: emptyList()
        page?.message?.let {
            if (items.isEmpty()) Text(it, color = RoonTextSecondary, fontSize = 14.sp)
        }

        when (style) {
            BrowseStyle.List -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                    RoonListItem(
                        title = item.title ?: "—",
                        subtitle = item.subtitle,
                        imageKey = item.imageKey,
                        fetchArt = vm::loadArt,
                        onClick = { onItem(item) },
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (style == BrowseStyle.Circle) 150.dp else 170.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                    PosterCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        imageKey = item.imageKey,
                        fetchArt = vm::loadArt,
                        circular = style == BrowseStyle.Circle,
                        onClick = { onItem(item) },
                    )
                }
            }
        }
    }
}

/** Small wrapper so callers can key the initial load to the session. */
@Composable
private fun LaunchedEffectKey(key: Any, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}
