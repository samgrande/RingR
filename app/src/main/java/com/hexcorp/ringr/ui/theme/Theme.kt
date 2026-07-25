package com.hexcorp.ringr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SeedColor = RingAccent

private val LightColorScheme = lightColorScheme(
    primary = RingDark,
    onPrimary = RingWhite,
    secondary = RingAccent,
    onSecondary = RingWhite,
    tertiary = RingAccentSoft,
    background = RingPanel,
    onBackground = RingDark,
    surface = RingPanel,
    onSurface = RingDark,
    surfaceVariant = RingPill,
    onSurfaceVariant = RingMuted,
    outline = RingAccentSoft,
)

private val DarkColorScheme = darkColorScheme(
    primary = RingWhite,
    onPrimary = RingDark,
    secondary = RingAccentSoft,
    onSecondary = RingDark,
    tertiary = RingAccent,
    background = RingDark,
    onBackground = RingWhite,
    surface = RingPanelInner,
    onSurface = RingDark,
    surfaceVariant = RingPill,
    onSurfaceVariant = RingMuted,
    outline = RingAccentSoft,
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
fun RingRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RingRTypography,
        content = content,
    )
}
