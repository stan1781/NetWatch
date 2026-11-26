package com.netwatch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EAF1),
    onPrimaryContainer = DeepTealDark,
    secondary = AccentCyan,
    onSecondary = Color.White,
    tertiary = WarmAmber,
    error = AlertRed,
    background = SurfaceLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFE3ECEF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD4E3),
    onPrimary = DeepTealDark,
    primaryContainer = DeepTeal,
    onPrimaryContainer = Color(0xFFD0EAF1),
    secondary = Color(0xFF8ED4DC),
    tertiary = Color(0xFFF4D35E),
    error = Color(0xFFFFB4AB),
    background = SurfaceDark,
    surface = Color(0xFF152024),
    surfaceVariant = Color(0xFF243338),
)

@Composable
fun NetWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
