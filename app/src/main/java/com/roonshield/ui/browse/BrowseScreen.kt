package com.roonshield.ui.browse

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
import androidx.compose.foundation.layout.width
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
import com.roonshield.ui.BrowseController
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.RoonListItem
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

@Composable
fun BrowseScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val controller = viewModel.library
    LaunchedEffect(Unit) {
        if (controller.items.value.isEmpty()) controller.home()
    }
    BrowseList(controller = controller, viewModel = viewModel, showSearch = false, modifier = modifier)
}

@Composable
fun SearchScreen(viewModel: RoonViewModel, modifier: Modifier = Modifier) {
    val controller = viewModel.search
    LaunchedEffect(Unit) {
        if (controller.items.value.isEmpty()) controller.home()
    }
    BrowseList(controller = controller, viewModel = viewModel, showSearch = true, modifier = modifier)
}

@Composable
private fun BrowseList(
    controller: BrowseController,
    viewModel: RoonViewModel,
    showSearch: Boolean,
    modifier: Modifier,
) {
    val items by controller.items.collectAsState()
    val title by controller.title.collectAsState()
    val loading by controller.loading.collectAsState()
    val message by controller.message.collectAsState()
    val level by controller.level.collectAsState()

    // Roon's Back navigates up the browse hierarchy while there's somewhere to go.
    BackHandler(enabled = level > 0) { controller.back() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.ifEmpty { if (showSearch) "Search" else "Browse" },
                color = RoonTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            if (level > 0) {
                Spacer(Modifier.width(12.dp))
                Text("· Back returns up", color = RoonTextSecondary, fontSize = 13.sp)
            }
            if (loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(color = RoonAccent, modifier = Modifier.height(18.dp).width(18.dp))
            }
        }

        if (showSearch) {
            Spacer(Modifier.height(12.dp))
            SearchField(onSubmit = { controller.submitInput(it) })
        }

        Spacer(Modifier.height(12.dp))

        message?.let {
            Text(it, color = RoonTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (items.isEmpty() && !loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (showSearch) "Type above and press enter to search." else "Nothing here.",
                    color = RoonTextSecondary,
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.itemKey ?: it.title.orEmpty() }) { item ->
                    RoonListItem(
                        title = item.title ?: "—",
                        subtitle = item.subtitle,
                        imageKey = item.imageKey,
                        fetchArt = viewModel::loadArt,
                        onClick = { controller.into(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(onSubmit: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search artists, albums, tracks") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.7f),
    )
    Spacer(Modifier.height(8.dp))
    RoonListItem(
        title = "Search",
        subtitle = null,
        modifier = Modifier.width(200.dp),
        onClick = { if (query.isNotBlank()) onSubmit(query.trim()) },
    )
}
