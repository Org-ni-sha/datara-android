package com.capstone.datara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                        if (sessionState == SessionState.Authenticated) {
                            DataraRoute.DASHBOARD
                        } else {
                            DataraRoute.LOGIN
                        }
                    }
                    DataraNavGraph(startDestination = startDestination)
                }
            }
        }
    }
}
