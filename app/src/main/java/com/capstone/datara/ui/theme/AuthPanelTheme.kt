package com.capstone.datara.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Navy auth panel palette, checked against WCAG on the panel (DataraNavyAccent, #14213A) and
// the field fill (#1D2C4A): primary 8.4 / 7.3:1, muted 8.4 / 7.3:1, white 16 / 13.9:1,
// field border 3.7 / 3.2:1 (non-text minimum is 3:1).
val DataraPanelField = Color(0xFF1D2C4A)
val DataraPanelBorder = Color(0xFF64799F)
val DataraPanelMuted = Color(0xFFAFBCD3)
val DataraPanelPrimary = Color(0xFF9DBBFF)
val DataraPanelTeal = Color(0xFF5EE0C3)

private val AuthPanelColors = darkColorScheme(
    primary = DataraPanelPrimary,
    onPrimary = DataraNavyAccent,
    secondary = DataraPanelPrimary,
    onSecondary = DataraNavyAccent,
    tertiary = DataraPanelTeal,
    onTertiary = DataraNavyAccent,
    background = DataraNavyAccent,
    onBackground = Color.White,
    surface = DataraPanelField,
    onSurface = Color.White,
    surfaceVariant = DataraPanelField,
    onSurfaceVariant = DataraPanelMuted,
    outline = DataraPanelBorder,
    outlineVariant = DataraPanelBorder,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Dark navy scheme for the Login/Register panel on the light welcome screen. Only colors
 * change; typography and shapes come from the enclosing [DATAraTheme]. In this scheme
 * `background` is the panel itself and `surface` is the slightly lighter field fill.
 */
@Composable
fun AuthPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuthPanelColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
