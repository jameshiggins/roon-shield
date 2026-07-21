package com.roonshield.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.RoonLogo
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

/**
 * Settings. Renders the Roon "settings" browse hierarchy as a native-style list; opening an item
 * drills in or invokes it (e.g. profile switching, toggles). Below the list sits an About area with
 * the Roon wordmark and the app version. Back walks up the hierarchy; the first row auto-focuses.
 */
@Composable
fun SettingsScreen(
    viewModel: RoonViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("settings", "settings") }
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var loading by remember { mutableStateOf(true) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        loading = true
        stack.add(session.home())
        loading = false
    }

    BackHandler(enabled = stack.size > 1) {
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

    fun open(item: BrowseItem) {
        val key = item.itemKey ?: return
        scope.launch {
            loading = true
            val next = session.open(key)
            // Toggles / profile switches often return a message-only page — invoke without pushing.
            if (item.hint != "action" && (next.items.isNotEmpty() || next.message == null)) {
                stack.add(next)
            }
            loading = false
        }
    }

    val page = stack.lastOrNull()
    val isRoot = stack.size <= 1

    LaunchedEffect(page, loading) {
        if (!loading && page != null && page.items.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = page?.title?.ifEmpty { "Settings" } ?: "Settings",
                color = RoonTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isRoot) {
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
        if (items.isEmpty()) {
            page?.message?.let { Text(it, color = RoonTextSecondary, fontSize = 14.sp) }
        }

        // The scrolling settings list takes the remaining space; About stays pinned below it.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(items, key = { _, it -> it.itemKey ?: it.title.orEmpty() }) { index, item ->
                RoonListItem(
                    title = item.title ?: "—",
                    subtitle = item.subtitle,
                    imageKey = item.imageKey,
                    fetchArt = viewModel::loadArt,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { open(item) },
                )
            }
        }

        AboutArea()
    }
}

/** Roon wordmark + app version, shown beneath the settings list. */
@Composable
private fun AboutArea() {
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        RoonLogo(height = 28.dp, tint = RoonTextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Roon Shield 0.1.0",
            color = RoonTextSecondary,
            fontSize = 13.sp,
        )
    }
}
