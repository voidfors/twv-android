package com.twv.viewers.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Цвета вне схемы Material: «в эфире» и золотая звезда избранного. */
val LiveRed = Color(0xFFEB3B3B)
val StarGold = Color(0xFFFFC857)

private val TwvDarkColors = darkColorScheme(
    primary = Color(0xFFB794FF),
    onPrimary = Color(0xFF1B0B3D),
    primaryContainer = Color(0xFF3A2670),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFB9A8E0),
    onSecondary = Color(0xFF1B0B3D),
    secondaryContainer = Color(0xFF3A2670),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFEFEFF1),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFEFEFF1),
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFADADB8),
    surfaceContainerLowest = Color(0xFF0A0A0C),
    surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF242429),
    surfaceContainerHighest = Color(0xFF2E2E34),
    outline = Color(0xFF4A4A55),
    outlineVariant = Color(0xFF2E2E36),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF2B0000)
)

/** Приложение всегда тёмное: цвета заданы явно, от системной темы телефона не зависят. */
@Composable
fun TwvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TwvDarkColors, content = content)
}
