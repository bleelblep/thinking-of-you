package com.bleelblep.thinkingofyou.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Simple palette for the standalone app
data class ThinkingOfYouPalette(
    val brand: Color,
    val surface: Color,
    val accent: Color,
    val border: Color,
    val muted: Color
)

val LocalPalette = compositionLocalOf {
    ThinkingOfYouPalette(
        brand = BrandPrimary,
        surface = SurfaceLight,
        accent = HeartRed,
        border = CardBorder,
        muted = MutedText
    )
}

val MaterialTheme.palette: ThinkingOfYouPalette
    @Composable get() = LocalPalette.current

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF003355),
    secondary = BrandAccent,
    onSecondary = White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF006A60),
    onTertiary = White,
    tertiaryContainer = Color(0xFFDAEEFF),
    onTertiaryContainer = Color(0xFF3A6A90),
    error = Color(0xFFBA1A1A),
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = SurfaceLight,
    onSurface = DarkInk,
    surfaceVariant = BrandSurface,
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = White,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = BrandSurface,
    outline = Color(0xFF72777F),
    outlineVariant = CardBorder,
    background = SurfaceLight,
    onBackground = DarkInk,
    scrim = Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82C8FF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF1A3D5C),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFE85C6A),
    onSecondary = Color(0xFF40000F),
    secondaryContainer = Color(0xFF871828),
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFF4DC8B8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF004E47),
    onTertiaryContainer = Color(0xFFDAEEFF),
    error = Color(0xFFFF8C8C),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF060E1A),
    onSurface = Color(0xFFE2EBF5),
    surfaceVariant = Color(0xFF162C40),
    onSurfaceVariant = Color(0xFFB8CDD8),
    surfaceContainerLowest = Color(0xFF040B14),
    surfaceContainerLow = Color(0xFF1C3348),
    surfaceContainer = Color(0xFF243C54),
    surfaceContainerHigh = Color(0xFF2C4560),
    outline = Color(0xFF4A6A80),
    outlineVariant = Color(0xFF1E3A52),
    background = Color(0xFF060E1A),
    onBackground = Color(0xFFE2EBF5),
    scrim = Color(0xFF000000),
)

private val LightPalette = ThinkingOfYouPalette(
    brand = BrandPrimary,
    surface = SurfaceLight,
    accent = HeartRed,
    border = CardBorder,
    muted = MutedText
)

private val DarkPalette = ThinkingOfYouPalette(
    brand = Color(0xFFA8D1C8),
    surface = Color(0xFF060E1A),
    accent = Color(0xFFFF8A8A),
    border = Color(0xFF2D3B34),
    muted = Color(0xFF6A8A7A)
)

@Composable
fun ThinkingOfYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val palette = if (darkTheme) DarkPalette else LightPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.brand.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
