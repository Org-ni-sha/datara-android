package com.capstone.datara.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capstone.datara.ui.auth.AuthEntryScreen
import com.capstone.datara.ui.auth.ForgotPasswordScreen
import com.capstone.datara.ui.auth.UsernameScreen
import com.capstone.datara.ui.dashboard.DashboardScreen

/** Route names, centralised so a typo is a compile error rather than a silent dead end. */
object DataraRoute {
    /** Welcome screen; hosts the Login and Register panels (see AuthEntryScreen). */
    const val WELCOME = "welcome"
    const val FORGOT_PASSWORD = "forgot_password"
    const val USERNAME = "username"
    const val DASHBOARD = "dashboard"
}

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val TRANSITION_MS = 350

@Composable
fun DataraNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = DataraRoute.WELCOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Auth boundaries (sign-in, sign-out) cross-fade; drill-in screens below slide.
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) }
    ) {
        composable(
            DataraRoute.WELCOME,
            exitTransition = {
                if (targetState.destination.route == DataraRoute.FORGOT_PASSWORD) {
                    slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_MS, easing = Emphasized)) { it / 4 } +
                        fadeOut(tween(TRANSITION_MS))
                } else {
                    fadeOut(tween(TRANSITION_MS))
                }
            },
            popEnterTransition = {
                slideIntoContainer(SlideDirection.End, tween(TRANSITION_MS, easing = Emphasized)) { it / 4 } +
                    fadeIn(tween(TRANSITION_MS))
            }
        ) {
            AuthEntryScreen(
                onSignedIn = { needsUsername -> navController.navigateAfterSignIn(needsUsername) },
                onForgotPasswordClick = { navController.navigate(DataraRoute.FORGOT_PASSWORD) }
            )
        }
        composable(
            DataraRoute.FORGOT_PASSWORD,
            enterTransition = {
                slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MS, easing = Emphasized))
            },
            popExitTransition = {
                slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MS, easing = Emphasized))
            }
        ) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.USERNAME) {
            UsernameScreen(
                onUsernameSaved = { navController.navigateClearingStack(DataraRoute.DASHBOARD) },
                onSignedOut = { navController.navigateClearingStack(DataraRoute.WELCOME) }
            )
        }
        composable(DataraRoute.DASHBOARD) {
            DashboardScreen(
                // Drop the whole authenticated back stack so the system back button
                // cannot return to a signed-in screen after signing out.
                onSignedOut = { navController.navigateClearingStack(DataraRoute.WELCOME) }
            )
        }
    }
}

/** Sends a freshly signed-in user to the username screen or straight to the dashboard. */
private fun NavHostController.navigateAfterSignIn(needsUsername: Boolean) {
    navigateClearingStack(if (needsUsername) DataraRoute.USERNAME else DataraRoute.DASHBOARD)
}

/**
 * Navigates to [route] with an empty back stack. Used on every auth boundary, so back from the
 * dashboard or username screen exits the app instead of returning to login with a live session.
 */
private fun NavHostController.navigateClearingStack(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
