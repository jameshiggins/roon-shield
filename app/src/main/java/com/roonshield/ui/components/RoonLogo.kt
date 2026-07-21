package com.roonshield.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roonshield.R
import com.roonshield.ui.theme.RoonTextPrimary

/** The official Roon wordmark, tinted. Width follows the wordmark's ~3.33:1 aspect ratio. */
@Composable
fun RoonLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    tint: androidx.compose.ui.graphics.Color = RoonTextPrimary,
) {
    Image(
        painter = painterResource(R.drawable.roon_wordmark),
        contentDescription = "Roon",
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
            .height(height)
            .width(height * 3.33f),
    )
}
