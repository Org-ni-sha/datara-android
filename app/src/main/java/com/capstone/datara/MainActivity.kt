package com.capstone.datara

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.navigation.DataraNavGraph
import com.capstone.datara.ui.navigation.DataraRoute
import com.capstone.datara.ui.session.SessionState
import com.capstone.datara.ui.session.SessionViewModel
import com.capstone.datara.ui.splash.SplashScreen
import com.capstone.datara.ui.theme.DATAraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars with no contrast scrim. The default would put a translucent light scrim
        // behind 3-button navigation, washing out the navy auth panel that runs underneath it.
        // Screens choose icon colors themselves (QuotaTheme, AuthEntryScreen).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            DATAraTheme {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                val sessionState by sessionViewModel.sessionState.collectAsState()

                if (sessionState == SessionState.Initializing) {
                    SplashScreen()
                } else {
                    // Latched on the first resolved state: the graph's start destination must not
                    // change when the user later signs out, or NavHost would be rebuilt underneath
                    // a live back stack. Sign-out navigates explicitly instead.
                    val startDestination = rememberSaveable {
                        when (sessionState) {
                            SessionState.Authenticated -> DataraRoute.DASHBOARD
                            SessionState.NeedsUsername -> DataraRoute.USERNAME
                            else -> DataraRoute.WELCOME
                        }
                    }
                    DataraNavGraph(startDestination = startDestination)
                }
            }
        }
    }
}
