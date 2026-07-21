package com.roonshield.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.roon.model.BrowseItem
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** Icon chosen from an action's label so whatever Roon names them, we show something sensible. */
fun actionIcon(title: String?): ImageVector {
    val t = title.orEmpty().lowercase()
    return when {
        "listen later" in t || "later" in t -> Icons.Filled.WatchLater
        "play now" in t || t == "play" || "play from here" in t -> Icons.Filled.PlayArrow
        "next" in t -> Icons.AutoMirrored.Filled.PlaylistAdd
        "queue" in t -> Icons.AutoMirrored.Filled.QueueMusic
        "radio" in t -> Icons.Filled.Radio
        "shuffle" in t -> Icons.Filled.Shuffle
        "library" in t -> Icons.Filled.LibraryAdd
        "playlist" in t -> Icons.AutoMirrored.Filled.PlaylistAdd
        "tag" in t || "add" in t -> Icons.Filled.Add
        else -> Icons.Filled.ChevronRight
    }
}

/**
 * Native-style bottom/centered action sheet: given the actions Roon returned for an item
 * (Play Now / Play Next / Add to Queue / Start Radio / …), plus any app actions, presents them
 * as a focusable list. Back dismisses.
 */
@Composable
fun ActionSheet(
    heading: String?,
    subheading: String?,
    imageKey: String?,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    actions: List<BrowseItem>,
    extraActions: List<SheetAction> = emptyList(),
    onPick: (BrowseItem) -> Unit,
    onExtra: (SheetAction) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(actions, extraActions) { runCatching { focus.requestFocus() } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RoonSurface)
                .padding(20.dp)
                .clickable(enabled = false) {},
        ) {
            if (!heading.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!imageKey.isNullOrEmpty()) {
                        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))) {
                            AlbumArt(imageKey = imageKey, sizePx = 128, fetch = fetchArt, modifier = Modifier)
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text(heading, color = RoonTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!subheading.isNullOrEmpty()) {
                            Text(subheading, color = RoonTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            val total = actions.size + extraActions.size
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightForCount(total),
            ) {
                itemsIndexed(actions, key = { _, a -> a.itemKey ?: a.title.orEmpty() }) { index, action ->
                    SheetRow(
                        icon = actionIcon(action.title),
                        label = action.title ?: "—",
                        modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                        onClick = { onPick(action) },
                    )
                }
                itemsIndexed(extraActions, key = { _, a -> "extra_${a.id}" }) { index, extra ->
                    SheetRow(
                        icon = extra.icon,
                        label = extra.label,
                        accent = true,
                        modifier = if (actions.isEmpty() && index == 0) Modifier.focusRequester(focus) else Modifier,
                        onClick = { onExtra(extra) },
                    )
                }
            }
        }
    }
}

/** An app-provided (non-Roon) action, e.g. "Add to Listen Later". */
data class SheetAction(val id: String, val label: String, val icon: ImageVector)

private fun Modifier.heightForCount(count: Int): Modifier {
    val rows = count.coerceIn(1, 8)
    return this.height((rows * 56 + 8).dp)
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) RoonAccent else RoonSurfaceVariant)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.White else if (accent) RoonAccent else RoonTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (focused) Color.White else RoonTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
