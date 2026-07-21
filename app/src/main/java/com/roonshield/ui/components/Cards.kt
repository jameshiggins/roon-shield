package com.roonshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roonshield.ui.theme.RoonAccent
import com.roonshield.ui.theme.RoonTextPrimary
import com.roonshield.ui.theme.RoonTextSecondary

/** Vertical poster card: album/artist art with title + subtitle, focus-scaled for the D-pad. */
@Composable
fun PosterCard(
    title: String?,
    subtitle: String?,
    imageKey: String?,
    fetchArt: suspend (String, Int, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (circular) 999.dp else 10.dp)
    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            }
            .onFocusState { focused = it }
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .border(
                    if (focused) BorderStroke(2.dp, RoonAccent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    shape,
                ),
        ) {
            AlbumArt(imageKey = imageKey, sizePx = 320, fetch = fetchArt, modifier = Modifier)
        }
        if (title != null) {
            Text(
                text = title,
                color = if (focused) RoonTextPrimary else RoonTextPrimary.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = RoonTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = RoonTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(action, color = RoonAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A titled horizontal shelf of cards. `content` is a LazyRow scope so callers add items(...) themselves. */
@Composable
fun CardShelf(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, action = action)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
