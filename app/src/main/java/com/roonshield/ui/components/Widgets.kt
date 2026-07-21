package com.roonshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonSurface
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** A focusable, D-pad-selectable list row: optional leading album art, title + subtitle. */
@Composable
fun RoonListItem(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    imageKey: String? = null,
    fetchArt: (suspend (String, Int, Int) -> ByteArray?)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) BorderStroke(2.dp, RoonAccent) else BorderStroke(1.dp, Color.Transparent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) 1.01f else 1f
                scaleY = if (focused) 1.01f else 1f
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) RoonSurfaceVariant else RoonSurface)
            .border(border, RoundedCornerShape(10.dp))
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (imageKey != null && fetchArt != null) {
            AlbumArt(
                imageKey = imageKey,
                sizePx = 96,
                fetch = fetchArt,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = if (imageKey != null) 12.dp else 0.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                color = RoonTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = RoonTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 8.dp)) { trailing() }
        }
    }
}

/** A round, focusable transport button. */
@Composable
fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Int = 56,
    active: Boolean = false,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> RoonAccent
        filled -> RoonTextPrimary
        else -> Color.Transparent
    }
    val tint = when {
        focused -> Color.White
        filled -> RoonSurface
        active -> RoonAccent
        else -> RoonTextPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.12f else 1f
                scaleY = if (focused) 1.12f else 1f
            }
            .clip(CircleShape)
            .background(bg)
            .onFocusState { focused = it }
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

/** A focusable navigation-rail entry. */
@Composable
fun NavRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> RoonAccent
        selected -> RoonSurfaceVariant
        else -> Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) Color.White else if (selected) RoonAccent else RoonTextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = if (focused) Color.White else RoonTextPrimary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
