package com.capstone.datara.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraLogo
import com.capstone.datara.ui.auth.components.SplashMatchedLogoSize
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.SystemBarIcons
import com.capstone.datara.ui.theme.WelcomeTheme
import kotlinx.coroutines.delay

/** Restoring a session usually finishes well inside this, so the status text rarely shows. */
private const val STATUS_DELAY_MS = 800L

/**
 * Shown while Supabase restores any persisted session. Without this the app would flash the
 * login screen for a moment before auto-login resolved, which reads as a bug to the user.
 *
 * It continues the native splash: same navy, and the logo at the exact size and window-center
 * position of the splash icon, so the handoff is invisible. The welcome screen then animates the
 * logo from this spot (see AuthEntryScreen's intro). The name, spinner and status fade in only if
 * restoring takes longer than [STATUS_DELAY_MS]; they stay in the tree for screen readers.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) = WelcomeTheme {
    SystemBarIcons(darkStatusIcons = false, darkNavigationIcons = false)

    var showStatus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STATUS_DELAY_MS)
        showStatus = true
    }
    val statusAlpha by animateFloatAsState(
        targetValue = if (showStatus) 1f else 0f,
        animationSpec = tween(300),
        label = "SplashStatusAlpha"
    )

    // Not inset-padded: the native splash centers its icon in the whole window.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DataraLogo(
            size = SplashMatchedLogoSize,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 150.dp)
                .graphicsLayer { alpha = statusAlpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "DATAra",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Restoring your session…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101522)
@Composable
private fun SplashScreenPreview() {
    DATAraTheme {
        SplashScreen()
    }
}
