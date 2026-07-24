package com.hexcorp.ringr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val RingRColorScheme = lightColorScheme(
    primary = RingDark,
    onPrimary = RingWhite,
    secondary = RingAccent,
    background = RingBg,
    onBackground = RingDark,
    surface = RingPanel,
    onSurface = RingDark,
)

val RingRTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

@Composable
fun RingRTheme(content: @Composable () -> Unit) {
    // Intentionally ignoring system dark theme — the mockups are a single fixed
    // light palette. isSystemInDarkTheme() left here as a hook if that changes.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = RingRColorScheme,
        typography = RingRTypography,
        content = content,
    )
}
