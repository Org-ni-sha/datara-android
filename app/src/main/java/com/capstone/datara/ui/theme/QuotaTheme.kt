package com.capstone.datara.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val QuotaLightColors = lightColorScheme(
    primary = M3Primary,
    onPrimary = DataraLightSurface,
    primaryContainer = M3PrimaryContainer,
    onPrimaryContainer = M3Primary,
    secondary = M3Secondary,
    onSecondary = DataraLightSurface,
    secondaryContainer = M3SecondaryContainer,
    onSecondaryContainer = M3OnSecondaryContainer,
    tertiary = M3Tertiary,
    onTertiary = DataraLightSurface,
    tertiaryContainer = M3TertiaryContainer,
    onTertiaryContainer = M3OnTertiaryContainer,
    inversePrimary = M3InverseMint,
    inverseSurface = M3Primary,
    inverseOnSurface = DataraLightSurface,
    background = M3Canvas,
    onBackground = DataraLightInk,
    surface = DataraLightSurface,
    onSurface = DataraLightInk,
    surfaceVariant = M3SurfaceContainerHighest,
    onSurfaceVariant = DataraLightSecondary,
    surfaceTint = M3Primary,
    surfaceBright = DataraLightSurface,
    surfaceDim = M3SurfaceContainerHighest,
    surfaceContainerLowest = DataraLightSurface,
    surfaceContainerLow = M3SurfaceContainerLow,
    surfaceContainer = M3SurfaceContainer,
    surfaceContainerHigh = M3SurfaceContainerHigh,
    surfaceContainerHighest = M3SurfaceContainerHighest,
    outline = M3Outline,
    outlineVariant = M3OutlineVariant,
    error = DataraLightError,
    onError = DataraLightSurface,
    errorContainer = M3ErrorContainer,
    onErrorContainer = M3OnErrorContainer
)

@Composable
fun QuotaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    MaterialTheme(colorScheme = QuotaLightColors,
        typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
