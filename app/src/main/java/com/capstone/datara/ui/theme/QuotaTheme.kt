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
    primary = DataraNavyAccent,
    onPrimary = DataraLightSurface,
    primaryContainer = DataraNavyContainer,
    onPrimaryContainer = DataraLightInk,
    secondary = DataraTeal,
    onSecondary = DataraLightSurface,
    secondaryContainer = DataraReferencePanel,
    onSecondaryContainer = DataraLightInk,
    tertiary = DataraTeal,
    onTertiary = DataraLightSurface,
    tertiaryContainer = DataraTealContainer,
    onTertiaryContainer = DataraTealInk,
    inversePrimary = DataraLimeAccent,
    inverseSurface = DataraNavyAccent,
    inverseOnSurface = DataraLightSurface,
    background = DataraReferenceCanvas,
    onBackground = DataraLightInk,
    surface = DataraLightSurface,
    onSurface = DataraLightInk,
    surfaceVariant = DataraNavyContainer,
    onSurfaceVariant = DataraLightSecondary,
    surfaceContainer = DataraLightSurface,
    surfaceContainerHigh = DataraLightSurface,
    surfaceContainerLow = DataraReferencePanel,
    outline = DataraLightBorder,
    outlineVariant = DataraLightBorder,
    error = DataraLightError,
    onError = DataraLightSurface
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
        typography = Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.0).sp, fontFeatureSettings = "tnum"),
            headlineLarge = Typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
            headlineMedium = Typography.headlineMedium.copy(letterSpacing = (-0.6).sp),
            titleLarge = Typography.titleLarge.copy(letterSpacing = (-0.3).sp),
            bodyLarge = Typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)),
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
