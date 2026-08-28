package com.iratrack.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0F0B)
val Surface = Color(0xFF141A15)
val Surface2 = Color(0xFF1B221C)
val Accent = Color(0xFF67D28A)
val Muted = Color(0xFFA6B0A7)
val Warning = Color(0xFFFFC857)

private val IraDark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF061008),
    background = Bg,
    onBackground = Color(0xFFEAF0EA),
    surface = Surface,
    onSurface = Color(0xFFEAF0EA),
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted
)

@Composable
fun IraTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IraDark, content = content)
}
