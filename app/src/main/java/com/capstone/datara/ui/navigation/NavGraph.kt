package com.capstone.datara.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capstone.datara.ui.auth.ForgotPasswordScreen
import com.capstone.datara.ui.auth.LoginScreen
import com.capstone.datara.ui.auth.RegisterScreen
import com.capstone.datara.ui.auth.UsernameScreen
import com.capstone.datara.ui.dashboard.DashboardScreen

/** Route names, centralised so a typo is a compile error rather than a silent dead end. */
object DataraRoute {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val USERNAME = "username"
    const val DASHBOARD = "dashboard"
}

@Composable
fun DataraNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = DataraRoute.LOGIN
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(DataraRoute.LOGIN) {
            LoginScreen(
                onLoginSuccess = { needsUsername -> navController.navigateAfterSignIn(needsUsername) },
                onNavigateToRegister = { navController.navigate(DataraRoute.REGISTER) },
                onForgotPasswordClick = { navController.navigate(DataraRoute.FORGOT_PASSWORD) }
            )
        }
        composable(DataraRoute.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { needsUsername -> navController.navigateAfterSignIn(needsUsername) },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.USERNAME) {
            UsernameScreen(
                onUsernameSaved = { navController.navigateClearingStack(DataraRoute.DASHBOARD) },
                onSignedOut = { navController.navigateClearingStack(DataraRoute.LOGIN) }
            )
        }
        composable(DataraRoute.DASHBOARD) {
            DashboardScreen(
                // Drop the whole authenticated back stack so the system back button
                // cannot return to a signed-in screen after signing out.
                onSignedOut = { navController.navigateClearingStack(DataraRoute.LOGIN) }
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
