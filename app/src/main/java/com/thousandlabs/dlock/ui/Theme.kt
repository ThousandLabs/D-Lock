package com.thousandlabs.dlock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D1017)
val Card = Color(0xFF171B24)
val Card2 = Color(0xFF1F2430)
val Accent = Color(0xFF5B9DFF)
val TextMain = Color(0xFFEDEFF4)
val TextDim = Color(0xFF9AA1B2)
val Danger = Color(0xFFFF6B6B)
val Ok = Color(0xFF4CC38A)
val Warn = Color(0xFFF2B544)

@Composable
fun DLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF0B1220),
            secondary = Accent,
            background = Bg,
            onBackground = TextMain,
            surface = Card,
            onSurface = TextMain,
            surfaceVariant = Card2,
            onSurfaceVariant = TextDim,
            surfaceContainerHigh = Card2,
            error = Danger,
        ),
        content = content,
    )
}
