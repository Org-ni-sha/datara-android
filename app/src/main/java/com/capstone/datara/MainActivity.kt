package com.capstone.datara

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.alerts.AlertNavigation
import com.capstone.datara.telemetry.TelemetryController
import com.capstone.datara.ui.navigation.DataraNavGraph
import com.capstone.datara.ui.navigation.DataraRoute
import com.capstone.datara.ui.session.SessionState
import com.capstone.datara.ui.session.SessionViewModel
import com.capstone.datara.ui.splash.SplashScreen
import com.capstone.datara.ui.theme.DATAraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var telemetryController: TelemetryController

    /** Hands a tapped quota alert to the signed-in screens, which open its cycle. */
    @Inject lateinit var alertNavigation: AlertNavigation

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alertNavigation.handle(intent)
    }

    override fun onStart() {
        super.onStart()
        // Resume tracking the user left on if Android or the vendor stopped it ("Open DATAra to
        // resume"). A start from the foreground is always allowed, unlike the watchdog's.
        telemetryController.restartIfWanted("App opened")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Only a fresh launch: a recreated activity must not re-open an alert already handled
        if (savedInstanceState == null) alertNavigation.handle(intent)
        // Transparent bars with no contrast scrim. The default would put a translucent light scrim
        // behind 3-button navigation over the auth panel, which runs underneath the navigation bar.
        // Screens choose icon colors themselves (QuotaTheme, AuthEntryScreen).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            DATAraTheme {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                val sessionState by sessionViewModel.sessionState.collectAsState()

                // Cross-fade out of the loading screen. On the way to welcome both show the logo at
                // the same spot, so only the loading text fades; the welcome intro then moves it.
                Crossfade(
                    targetState = sessionState == SessionState.Initializing,
                    animationSpec = tween(300),
                    label = "SessionGate"
                ) { initializing ->
                    if (initializing) {
                        SplashScreen()
                    } else {
                        // Latched on the first resolved state: the graph's start destination must
                        // not change when the user later signs out, or NavHost would be rebuilt
                        // underneath a live back stack. Sign-out navigates explicitly instead.
                        val startDestination = rememberSaveable {
                            when (sessionState) {
                                SessionState.Authenticated -> DataraRoute.DASHBOARD
                                SessionState.NeedsUsername -> DataraRoute.USERNAME
                                else -> DataraRoute.WELCOME
                            }
                        }
                        DataraNavGraph(
                            startDestination = startDestination,
                            playWelcomeIntro = startDestination == DataraRoute.WELCOME
                        )
                    }
                }
            }
        }
    }
}
