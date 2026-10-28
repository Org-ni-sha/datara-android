package com.capstone.datara.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capstone.datara.R
import com.capstone.datara.ui.auth.ForgotPasswordScreen
import com.capstone.datara.ui.auth.LoginScreen
import com.capstone.datara.ui.auth.RegisterScreen
import com.capstone.datara.ui.dashboard.ActivePromoUiModel
import com.capstone.datara.ui.dashboard.DashboardScreen
import com.capstone.datara.ui.dashboard.DashboardUiState
import com.capstone.datara.ui.dashboard.DataBalanceUiModel
import com.capstone.datara.ui.dashboard.PetUiState
import com.capstone.datara.ui.diagnostics.DeveloperDiagnosticsScreen
import com.capstone.datara.ui.history.HistoryScreen
import com.capstone.datara.ui.legal.PrivacyScreen
import com.capstone.datara.ui.legal.TermsScreen
import com.capstone.datara.ui.notifications.NotificationCenterScreen
import com.capstone.datara.ui.onboarding.NotificationIntroScreen
import com.capstone.datara.ui.onboarding.NotificationIntroUiState
import com.capstone.datara.ui.onboarding.UsageAccessScreen
import com.capstone.datara.ui.onboarding.UsageAccessUiState
import com.capstone.datara.ui.profile.ProfileScreen
import com.capstone.datara.ui.promos.FrontendPromoSelection
import com.capstone.datara.ui.promos.PromoSetupScreen
import com.capstone.datara.ui.settings.SettingsScreen

/** Route names, centralised so a typo is a compile error rather than a silent dead end. */
object DataraRoute {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val USAGE_ACCESS_ONBOARDING = "onboarding/usage_access"
    const val NOTIFICATION_INTRO_ONBOARDING = "onboarding/notifications"
    const val PROMO_SETUP_ONBOARDING = "onboarding/promo"
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PROMO_SETUP = "promo_setup"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val USAGE_ACCESS_REVIEW = "settings/usage_access"
    const val NOTIFICATION_INTRO_REVIEW = "settings/notifications"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val DIAGNOSTICS = "developer/diagnostics"
}

@Composable
fun DataraNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = DataraRoute.LOGIN,
    darkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {}
) {
    // Frontend-only promo state. Primitive fields survive configuration changes, but nothing is
    // written to a database or carrier service and the dashboard labels the result as a preview.
    var previewPromoName by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPromoCarrier by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPromoTotalGb by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPromoRemainingGb by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPromoValidityDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var previewPromoExpiry by rememberSaveable { mutableStateOf<String?>(null) }

    fun rememberPromo(selection: FrontendPromoSelection) {
        previewPromoName = selection.name
        previewPromoCarrier = selection.carrier
        previewPromoTotalGb = selection.totalGb.toString()
        previewPromoRemainingGb = selection.remainingGb.toString()
        previewPromoValidityDays = selection.validityDays
        previewPromoExpiry = selection.expiryLabel
    }

    val dashboardState = if (
        previewPromoName != null &&
        previewPromoCarrier != null &&
        previewPromoTotalGb?.toDoubleOrNull() != null &&
        previewPromoRemainingGb?.toDoubleOrNull() != null &&
        previewPromoValidityDays != null &&
        previewPromoExpiry != null
    ) {
        DashboardUiState(
            pet = PetUiState.Collecting(daysCollected = 0),
            balance = DataBalanceUiModel(
                remainingGb = previewPromoRemainingGb!!.toDouble(),
                totalGb = previewPromoTotalGb!!.toDouble(),
                consumedGb = (previewPromoTotalGb!!.toDouble() - previewPromoRemainingGb!!.toDouble())
                    .coerceAtLeast(0.0)
            ),
            promo = ActivePromoUiModel(
                name = previewPromoName!!,
                carrier = previewPromoCarrier!!,
                expiresIn = stringResource(
                    R.string.dashboard_validity_days,
                    previewPromoValidityDays!!
                ),
                expiresOn = previewPromoExpiry!!
            ),
            previewNotice = stringResource(R.string.dashboard_preview_notice)
        )
    } else {
        DashboardUiState()
    }

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
                onRegisterSuccess = {
                    navController.navigate(DataraRoute.USAGE_ACCESS_ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.USAGE_ACCESS_ONBOARDING) {
            UsageAccessScreen(
                state = UsageAccessUiState(isOnboarding = true),
                onBack = { navController.navigateToDashboard() },
                onContinue = { navController.navigate(DataraRoute.NOTIFICATION_INTRO_ONBOARDING) },
                onNotNow = { navController.navigate(DataraRoute.NOTIFICATION_INTRO_ONBOARDING) }
            )
        }
        composable(DataraRoute.NOTIFICATION_INTRO_ONBOARDING) {
            NotificationIntroScreen(
                state = NotificationIntroUiState(isOnboarding = true),
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(DataraRoute.PROMO_SETUP_ONBOARDING) },
                onNotNow = { navController.navigate(DataraRoute.PROMO_SETUP_ONBOARDING) }
            )
        }
        composable(DataraRoute.PROMO_SETUP_ONBOARDING) {
            PromoSetupScreen(
                onBack = { navController.popBackStack() },
                onSkip = { navController.navigateToDashboard() },
                onPromoSaved = { selection ->
                    rememberPromo(selection)
                    navController.navigateToDashboard()
                }
            )
        }
        composable(
            route = DataraRoute.DASHBOARD,
            enterTransition = { topLevelEnterTransition() },
            exitTransition = { topLevelExitTransition() },
            popEnterTransition = { topLevelEnterTransition() },
            popExitTransition = { topLevelExitTransition() }
        ) {
            DashboardScreen(
                state = dashboardState,
                onHistoryClick = { navController.navigateToTopLevel(DataraRoute.HISTORY) },
                onSettingsClick = { navController.navigateToTopLevel(DataraRoute.SETTINGS) },
                onAddPromoClick = { navController.navigate(DataraRoute.PROMO_SETUP) },
                onNotificationsClick = { navController.navigate(DataraRoute.NOTIFICATIONS) },
                onSignedOut = { navController.navigateToLogin() }
            )
        }
        composable(
            route = DataraRoute.HISTORY,
            enterTransition = { topLevelEnterTransition() },
            exitTransition = { topLevelExitTransition() },
            popEnterTransition = { topLevelEnterTransition() },
            popExitTransition = { topLevelExitTransition() }
        ) {
            HistoryScreen(
                onHomeClick = { navController.navigateToTopLevel(DataraRoute.DASHBOARD) },
                onSettingsClick = { navController.navigateToTopLevel(DataraRoute.SETTINGS) },
                onReviewAccessClick = { navController.navigate(DataraRoute.USAGE_ACCESS_REVIEW) }
            )
        }
        composable(
            route = DataraRoute.SETTINGS,
            enterTransition = { topLevelEnterTransition() },
            exitTransition = { topLevelExitTransition() },
            popEnterTransition = { topLevelEnterTransition() },
            popExitTransition = { topLevelExitTransition() }
        ) {
            SettingsScreen(
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                onHomeClick = { navController.navigateToTopLevel(DataraRoute.DASHBOARD) },
                onHistoryClick = { navController.navigateToTopLevel(DataraRoute.HISTORY) },
                onManageProfileClick = { navController.navigate(DataraRoute.PROFILE) },
                onUsageAccessClick = { navController.navigate(DataraRoute.USAGE_ACCESS_REVIEW) },
                onNotificationSettingsClick = {
                    navController.navigate(DataraRoute.NOTIFICATION_INTRO_REVIEW)
                },
                onPrivacyClick = { navController.navigate(DataraRoute.PRIVACY) },
                onTermsClick = { navController.navigate(DataraRoute.TERMS) },
                onDeveloperDiagnosticsClick = { navController.navigate(DataraRoute.DIAGNOSTICS) },
                onSignedOut = { navController.navigateToLogin() }
            )
        }
        composable(DataraRoute.PROMO_SETUP) {
            PromoSetupScreen(
                onBack = { navController.popBackStack() },
                onSkip = { navController.popBackStack() },
                onPromoSaved = { selection ->
                    rememberPromo(selection)
                    navController.popBackStack()
                }
            )
        }
        composable(DataraRoute.NOTIFICATIONS) {
            NotificationCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(DataraRoute.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(DataraRoute.USAGE_ACCESS_REVIEW) {
            UsageAccessScreen(
                state = UsageAccessUiState(isOnboarding = false),
                onBack = { navController.popBackStack() },
                onContinue = {},
                onNotNow = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.NOTIFICATION_INTRO_REVIEW) {
            NotificationIntroScreen(
                state = NotificationIntroUiState(isOnboarding = false),
                onBack = { navController.popBackStack() },
                onContinue = {},
                onNotNow = { navController.popBackStack() }
            )
        }
        composable(DataraRoute.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(DataraRoute.TERMS) {
            TermsScreen(onBack = { navController.popBackStack() })
        }
        composable(DataraRoute.DIAGNOSTICS) {
            DeveloperDiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Removes auth and onboarding routes so back cannot reopen a signed-out setup screen. */
private fun NavHostController.navigateToDashboard() {
    navigate(DataraRoute.DASHBOARD) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToLogin() {
    navigate(DataraRoute.LOGIN) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/** Keeps Dashboard as the authenticated root while avoiding duplicate top-level destinations. */
private fun NavHostController.navigateToTopLevel(route: String) {
    if (route == DataraRoute.DASHBOARD) {
        popBackStack(route = DataraRoute.DASHBOARD, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(DataraRoute.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val topLevelRouteOrder = listOf(
    DataraRoute.DASHBOARD,
    DataraRoute.HISTORY,
    DataraRoute.SETTINGS
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelDirection(): Int {
    val from = topLevelRouteOrder.indexOf(initialState.destination.route).coerceAtLeast(0)
    val to = topLevelRouteOrder.indexOf(targetState.destination.route).coerceAtLeast(0)
    return if (to >= from) 1 else -1
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelEnterTransition(): EnterTransition {
    val direction = topLevelDirection()
    return slideInHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> direction * fullWidth }
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelExitTransition(): ExitTransition {
    val direction = topLevelDirection()
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -direction * fullWidth }
    )
}
