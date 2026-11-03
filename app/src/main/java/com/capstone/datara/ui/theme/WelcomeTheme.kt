package com.capstone.datara.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Brand navy shared by the launcher icon background, the native splash, the session-loading
 * screen and the welcome screen, so launch reads as one continuous surface. Must match
 * `datara_icon_background` and `datara_launch_background` in res/values.
 */
val DataraBrandNavy = Color(0xFF101522)

// Text and outlines checked for WCAG contrast on DataraBrandNavy: white 18.2:1, muted 9.5:1,
// outline 4.5:1 (non-text minimum 3:1).
private val DataraWelcomeMuted = Color(0xFFAFBCD3)
private val DataraWelcomeOutline = Color(0xFF6A7FA6)

private val WelcomeColors = darkColorScheme(
    primary = Color.White,
    onPrimary = DataraNavyAccent,
    background = DataraBrandNavy,
    onBackground = Color.White,
    surface = DataraBrandNavy,
    onSurface = Color.White,
    onSurfaceVariant = DataraWelcomeMuted,
    outline = DataraWelcomeOutline,
    outlineVariant = DataraWelcomeOutline
)

/**
 * Navy scheme for the signed-out welcome and session-loading screens. Only colors change;
 * typography and shapes come from the enclosing [DATAraTheme]. Forms (the auth panel, Forgot
 * password, Choose a username) stay in the light theme.
 */
@Composable
fun WelcomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WelcomeColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

/**
 * Sets system-bar icon colors while the calling screen is shown: `darkIcons = false` for white
 * icons over navy. Restores dark icons (the light app's default) when the screen leaves.
 */
@Composable
fun SystemBarIcons(darkStatusIcons: Boolean, darkNavigationIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, darkStatusIcons, darkNavigationIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = darkStatusIcons
        controller?.isAppearanceLightNavigationBars = darkNavigationIcons
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }
}
