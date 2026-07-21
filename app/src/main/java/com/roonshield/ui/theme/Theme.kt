package com.roonshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RoonBackground = Color(0xFF0E0F13)
val RoonSurface = Color(0xFF17181E)
val RoonSurfaceVariant = Color(0xFF20222B)
val RoonAccent = Color(0xFF3E7BFA)
val RoonTextPrimary = Color(0xFFF2F3F5)
val RoonTextSecondary = Color(0xFF9AA0AA)

private val RoonColors = darkColorScheme(
    primary = RoonAccent,
    onPrimary = Color.White,
    background = RoonBackground,
    onBackground = RoonTextPrimary,
    surface = RoonSurface,
    onSurface = RoonTextPrimary,
    surfaceVariant = RoonSurfaceVariant,
    onSurfaceVariant = RoonTextSecondary,
)

@Composable
fun RoonShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoonColors, content = content)
}
