package com.roonshield.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.BrowsePage
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.components.SectionHeader
import com.roonshield.ui.components.onFocusState
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonBackground
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.launch

/**
 * Enhanced Roon Search screen. Owns its own isolated "search" browse session, presents a prominent
 * text field, submits the query via [com.roonshield.roon.BrowseSession.input], and renders the
 * returned results — grouped into sections where Roon supplies header rows. Drilling into a result
 * pushes a level; Back pops it.
 */
@Composable
fun SearchScreenV2(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val session = remember { viewModel.browseSession("search-v2", "search") }

    // Result levels pushed after a search. Empty == the friendly "type to search" state.
    val stack = remember { mutableStateListOf<BrowsePage>() }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val fieldFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    // Prime the search session (Roon requires the hierarchy to be at home before input) and park
    // focus on the text field so the D-pad always has an anchor.
    LaunchedEffect(Unit) {
        loading = true
        runCatching { session.home() }
        loading = false
        runCatching { fieldFocus.requestFocus() }
    }

    val page = stack.lastOrNull()

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true
            submitted = true
            val res = runCatching { session.input(q) }.getOrNull()
            stack.clear()
            if (res != null) stack.add(res)
            loading = false
        }
    }

    fun open(item: BrowseItem) {
        val prompt = item.inputPrompt
        val key = item.itemKey
        if (prompt == null && key.isNullOrEmpty()) return // header / non-actionable row
        scope.launch {
            loading = true
            val next = runCatching {
                // Items carrying an inputPrompt expect text input rather than a plain open.
                if (prompt != null) session.input(query.trim()) else session.open(key!!)
            }.getOrNull()
            // "action" items (Play Now, etc.) just perform an action — don't push a dead level.
            if (next != null && item.hint != "action" && next.items.isNotEmpty()) {
                stack.add(next)
            }
            loading = false
        }
    }

    // A query requested from another screen (e.g. Listen Later) runs automatically on entry.
    LaunchedEffect(Unit) {
        viewModel.consumePendingSearch()?.let { q ->
            query = q
            loading = true
            submitted = true
            runCatching { session.home() }
            val res = runCatching { session.input(q) }.getOrNull()
            stack.clear()
            if (res != null) stack.add(res)
            loading = false
        }
    }

    // Back pops drilled-in result levels; at the top results level it clears back to the empty state.
    BackHandler(enabled = stack.isNotEmpty()) {
        scope.launch {
            loading = true
            if (stack.size > 1) {
                val parent = runCatching { session.back() }.getOrNull()
                stack.removeAt(stack.lastIndex)
                if (parent != null) stack[stack.lastIndex] = parent
            } else {
                stack.clear()
                submitted = false
                runCatching { fieldFocus.requestFocus() }
            }
            loading = false
        }
    }

    // When a level of results arrives, move focus onto the first actionable row.
    LaunchedEffect(page) {
        if (page != null && page.items.any { it.isActionable() }) {
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoonBackground)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = page?.title?.ifEmpty { "Search" } ?: "Search",
                color = RoonTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stack.size > 1) {
                Spacer(Modifier.width(12.dp))
                Text("· Back goes up", color = RoonTextSecondary, fontSize = 12.sp)
            }
            if (loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(color = RoonAccent, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search artists, albums, tracks…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoonAccent,
                    unfocusedBorderColor = RoonTextSecondary,
                    focusedLabelColor = RoonAccent,
                    unfocusedLabelColor = RoonTextSecondary,
                    focusedTextColor = RoonTextPrimary,
                    unfocusedTextColor = RoonTextPrimary,
                    cursorColor = RoonAccent,
                    focusedContainerColor = RoonSurface,
                    unfocusedContainerColor = RoonSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldFocus),
            )
            Spacer(Modifier.width(12.dp))
            SearchButton(onClick = { runSearch() })
        }

        Spacer(Modifier.height(16.dp))

        val items = page?.items ?: emptyList()
        when {
            items.isNotEmpty() -> {
                val firstActionable = items.indexOfFirst { it.isActionable() }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items,
                        key = { i, it -> it.itemKey ?: "hdr-$i-${it.title.orEmpty()}" },
                    ) { index, item ->
                        if (item.isActionable()) {
                            val rowMod = if (index == firstActionable) {
                                Modifier.focusRequester(firstItemFocus)
                            } else {
                                Modifier
                            }
                            RoonListItem(
                                title = item.title ?: "—",
                                subtitle = item.subtitle,
                                imageKey = item.imageKey,
                                fetchArt = viewModel::loadArt,
                                modifier = rowMod,
                                onClick = { open(item) },
                            )
                        } else {
                            // Roon supplies group headers as non-actionable rows (Artists, Albums…).
                            SectionHeader(title = item.title ?: "", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            else -> EmptyState(
                message = when {
                    loading -> "Searching…"
                    submitted && !page?.message.isNullOrEmpty() -> page?.message ?: ""
                    submitted -> "No results. Try another search."
                    else -> "Type to search…"
                },
            )
        }
    }
}

/** An item is actionable (a real result / action) when it can be opened or prompts for input. */
private fun BrowseItem.isActionable(): Boolean =
    inputPrompt != null || !itemKey.isNullOrEmpty()

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = RoonTextSecondary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(message, color = RoonTextSecondary, fontSize = 15.sp)
        }
    }
}

/** A focusable, D-pad-selectable search button styled with the Roon accent. */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .clip(shape)
            .background(if (focused) RoonAccent else RoonSurfaceVariant)
            .border(
                if (focused) BorderStroke(2.dp, RoonAccent) else BorderStroke(1.dp, Color.Transparent),
                shape,
            )
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = if (focused) Color.White else RoonTextPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Search",
            color = if (focused) Color.White else RoonTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
