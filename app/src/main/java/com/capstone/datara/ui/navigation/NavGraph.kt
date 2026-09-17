package com.capstone.datara.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capstone.datara.ui.auth.ForgotPasswordScreen
import com.capstone.datara.ui.auth.LoginScreen
import com.capstone.datara.ui.auth.RegisterScreen
import com.capstone.datara.ui.dashboard.DashboardPlaceholderScreen

/** Route names, centralised so a typo is a compile error rather than a silent dead end. */
object DataraRoute {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
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
                onLoginSuccess = { navController.navigateToDashboard() },
                onNavigateToRegister = { navController.navigate(DataraRoute.REGISTER) },
                onForgotPasswordClick = { navController.navigate(DataraRoute.FORGOT_PASSWORD) }
            )
        }
        composable(DataraRoute.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigateToDashboard() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.DASHBOARD) {
            DashboardPlaceholderScreen(
                onSignedOut = {
                    navController.navigate(DataraRoute.LOGIN) {
                        // Drop the whole authenticated back stack so the system back button
                        // cannot return to a signed-in screen after signing out.
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

/**
 * Clears the auth screens from the back stack on the way to the dashboard, so back from the
 * dashboard exits the app instead of returning to login with a live session.
 */
private fun NavHostController.navigateToDashboard() {
    navigate(DataraRoute.DASHBOARD) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
