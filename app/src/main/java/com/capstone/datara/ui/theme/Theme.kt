package com.capstone.datara.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DataraPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = DataraCardBg,
    onPrimaryContainer = Color.White,
    secondary = DataraNeonBlue,
    onSecondary = Color.White,
    tertiary = DataraNeonGreen,
    onTertiary = DataraDarkBg,
    background = DataraDarkBg,
    onBackground = DataraTextPrimary,
    surface = DataraCardBg,
    onSurface = DataraTextPrimary,
    surfaceVariant = DataraInputBg,
    onSurfaceVariant = DataraTextSecondary,
    outline = DataraInputBorder,
    error = DataraError,
    onError = Color.White
)

@Composable
fun DATAraTheme(
    darkTheme: Boolean = true, // Default to dark theme for consistent branded experience
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DataraDarkBg.toArgb()
            window.navigationBarColor = DataraDarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}