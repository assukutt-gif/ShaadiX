package com.assukutt.shaadix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Royal = Color(0xFF4C2678)
val Violet = Color(0xFF7045A2)
val Gold = Color(0xFFD8AE63)
val Ink = Color(0xFF241C2D)
val Muted = Color(0xFF857D8B)
val Canvas = Color(0xFFFAF8FC)
val Lilac = Color(0xFFF0EAF6)

@Composable
fun ShaadiXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Royal, onPrimary = Color.White, secondary = Gold,
        background = Canvas, surface = Color.White, onSurface = Ink,
        surfaceVariant = Lilac, onSurfaceVariant = Muted, error = Color(0xFFB54747)
    ), content = content)
}
