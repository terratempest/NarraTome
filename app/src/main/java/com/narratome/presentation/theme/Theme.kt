package com.narratome.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable


@Composable
fun AudiobookTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = AccentGold,
        onPrimary = BgDeep,
        background = BgDeep,
        onBackground = TextMain,
        surface = BgCard,
        onSurface = TextMain,
        surfaceVariant = BgHover,
        onSurfaceVariant = TextDim,
        outline = BorderDark,
        secondary = AccentGold,
        onSecondary = BgDeep,
        tertiary = TextMuted,
        onTertiary = TextMain
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
