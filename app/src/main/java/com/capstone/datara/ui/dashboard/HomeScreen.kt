package com.capstone.datara.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.AuthViewModel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.quota.*
import com.capstone.datara.ui.history.HistoryScreen
import com.capstone.datara.ui.history.HistoryUiState
import com.capstone.datara.ui.history.HistoryViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.capstone.datara.ui.notifications.*
import com.capstone.datara.ui.settings.*
import com.capstone.datara.ui.profile.ProfileViewModel
import com.capstone.datara.ui.auth.AuthUiState
import com.capstone.datara.ui.theme.*
import com.capstone.datara.ui.tracking.TrackingSetupScreen
import com.capstone.datara.ui.diagnostics.DiagnosticsRoute

@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    viewModel: QuotaDemoViewModel = hiltViewModel(),
    cycleViewModel: QuotaCycleViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    notificationsViewModel: NotificationCenterViewModel = hiltViewModel(),
    settingsViewModel: SettingsStatusViewModel = hiltViewModel(),
    preferencesViewModel: NotificationPreferencesViewModel = hiltViewModel(),
    alertNavigationViewModel: AlertNavigationViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cycleDetails by cycleViewModel.details.collectAsState()
    val cycleAction by cycleViewModel.action.collectAsState()
    val notificationState by notificationsViewModel.uiState.collectAsState()
    val openedCycle by notificationsViewModel.openedCycle.collectAsState()
    val alertPreferences by preferencesViewModel.preferences.collectAsState()
    val alertTap by alertNavigationViewModel.tap.collectAsState()
    val settingsStatus by settingsViewModel.status.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    // Grants change in Android Settings, outside the app: Home's banner re-checks on every resume
    LifecycleResumeEffect(Unit) { settingsViewModel.refresh(); onPauseOrDispose { } }
    var creating by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var tracking by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()
    // An alert's cycle: the current one opens Quota Details, a past one opens read-only from the inbox
    val openAlertCycle: (String) -> Unit = { cycleId ->
        if (cycleId == cycleDetails.cycleId) {
            notificationsViewModel.closeCycle()
            notifications = false; tracking = false; creating = false; details = true
        } else {
            notificationsViewModel.openCycle(cycleId)
            tracking = false; creating = false; details = false; notifications = true
        }
    }
    // A tapped Android notification: mark it read and open its cycle (or the inbox)
    LaunchedEffect(alertTap, cycleDetails.isLoading) {
        val tap = alertTap ?: return@LaunchedEffect
        if (cycleDetails.isLoading) return@LaunchedEffect
        notificationsViewModel.markRead(tap.notificationId)
        tap.cycleId?.let(openAlertCycle) ?: run { notifications = true }
        alertNavigationViewModel.consume()
    }
    BackHandler(!diagnostics && (tracking || creating || details || notifications || history || settings)) {
        if (tracking) tracking = false else if (creating) creating = false else if (details) details = false else if (notifications) notifications = false else { history = false; settings = false }
    }
    Box(Modifier.fillMaxSize()) {
    if (diagnostics) {
        DiagnosticsRoute(onBack = { diagnostics = false })
    } else if (tracking) {
        TrackingSetupScreen(onBack = { tracking = false }, onMessage = notify,
            onHome = { tracking = false; settings = false; history = false; details = false })
    } else if (creating) {
        CreateQuotaScreen(onBack = { cycleViewModel.clearError(); creating = false }, onCreateQuota = { bytes, name ->
            cycleViewModel.createQuota(bytes, name) {
                // Home's hero shows the real cycle; keep the preview fixtures in step for its other parts
                viewModel.createQuota(bytes, name)
                creating = false
                details = false
                notify("Quota created")
                // A new quota is only measured while tracking runs: guide the user there when it can't
                settingsViewModel.refresh()
                if (!settingsViewModel.status.value.let { it.access.canStart && it.serviceRunning }) tracking = true
            }
        }, isSaving = cycleAction.isSaving, errorMessage = cycleAction.errorMessage,
            replacingName = cycleDetails.takeIf { it.hasQuota && !it.isEnded }?.name)
    } else if (details) {
        // Quota Details shows the real cycle (D3) and its on-device forecast (D6)
        QuotaDetailsScreen(cycleDetails, onBack = { cycleViewModel.clearError(); details = false },
            onEndCycle = { cycleViewModel.endCycle { viewModel.endCycle(); notify("Cycle ended") } }, onCreateQuota = { creating = true },
            onTrackingSetup = { tracking = true }, actionError = cycleAction.errorMessage)
    } else if (notifications) {
        NotificationsScreen(notificationState,
            onBack = { notifications = false }, onMarkRead = notificationsViewModel::markRead,
            onMarkAllRead = notificationsViewModel::markAllRead, onOpenCycle = openAlertCycle,
            openedCycle = openedCycle, onCloseCycle = notificationsViewModel::closeCycle)
    } else if (settings) {
        screenStates.SaveableStateProvider("settings") {
            SettingsScreen(authViewModel.currentUserEmail,
                onSaveName = profileViewModel::saveName,
                onMilestonesChange = preferencesViewModel::setMilestones,
                onForecastsChange = preferencesViewModel::setForecasts,
                onHome = { settings = false; history = false },
                onHistory = { settings = false; history = true },
                onSignOut = { authViewModel.signOut(onComplete = onSignedOut) },
                isSigningOut = authState is AuthUiState.Loading,
                signOutError = (authState as? AuthUiState.Error)?.message,
                profile = profileState,
                onNameEdited = profileViewModel::onNameEdited,
                onTrackingSetup = { tracking = true },
                alertPreferences = alertPreferences,
                status = settingsStatus,
                onRefreshStatus = settingsViewModel::refresh,
                onOpenDiagnostics = { diagnostics = true })
        }
    } else if (history) {
        screenStates.SaveableStateProvider("history") {
            HistoryScreen(onHome = { history = false }, onSettings = { settings = true; history = false },
                onNotifications = { notifications = true }, unreadCount = notificationState.unreadCount)
        }
    } else {
        HomeScreen(state, onCreateQuota = { creating = true },
            onSelectScenario = viewModel::selectScenario,
            onSignOut = { authViewModel.signOut(onComplete = onSignedOut) },
            onViewQuota = { details = true }, onHistory = { history = true },
            onNotifications = { notifications = true }, unreadCount = notificationState.unreadCount,
            onSettings = { settings = true; history = false },
            onTrackingSetup = { tracking = true },
            live = cycleDetails.takeIf { it.hasQuota && !it.isSample },
            liveLoading = cycleDetails.isLoading,
            trackingIssue = TrackingIssue.of(settingsStatus.access, settingsStatus.serviceRunning,
                cycleActive = cycleDetails.hasQuota && !cycleDetails.isEnded && !cycleDetails.isReached),
            week = (historyState as? HistoryUiState.Ready)?.data?.week?.let(HomeWeek::from),
            onShowRealData = viewModel::showRealData)
    }
    // One snackbar host above the docked navigation bar, for every signed-in screen
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onCreateQuota: () -> Unit,
    onSelectScenario: (HomeSampleState) -> Unit, onSignOut: () -> Unit,
    modifier: Modifier = Modifier, onViewQuota: () -> Unit = {}, onHistory: () -> Unit = {},
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, onSettings: () -> Unit = {},
    onTrackingSetup: () -> Unit = {}, live: QuotaDetailsModel? = null, liveLoading: Boolean = false,
    trackingIssue: TrackingIssue? = null, week: HomeWeek? = null, onShowRealData: () -> Unit = {}) {
    QuotaTheme { HomeContent(state, onCreateQuota, onSelectScenario, onSignOut, modifier, onViewQuota, onHistory, onNotifications,
        unreadCount, onSettings, onTrackingSetup, live, liveLoading, trackingIssue, week, onShowRealData) }
}

@Preview(name = "No quota", widthDp = 360, heightDp = 800)
@Composable
private fun EmptyHomePreview() { QuotaTheme { HomeScreen(HomeUiState(), {}, {}, {}) } }
@Preview(name = "Forecast", widthDp = 360, heightDp = 800)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun ForecastHomePreview() { QuotaTheme { HomeScreen(HomeUiState(previewing = true, scenario = HomeSampleState.FORECAST_AVAILABLE), {}, {}, {}) } }
@Preview(name = "Collecting", widthDp = 360, heightDp = 800)
@Composable
private fun CollectingHomePreview() { QuotaTheme { HomeScreen(HomeUiState(previewing = true, scenario = HomeSampleState.COLLECTING), {}, {}, {}) } }
@Preview(name = "Permission missing", widthDp = 360, heightDp = 800)
@Composable
private fun PermissionHomePreview() { QuotaTheme { HomeScreen(HomeUiState(previewing = true, scenario = HomeSampleState.MISSING_PERMISSION), {}, {}, {}) } }
@Preview(name = "Quota reached", widthDp = 360, heightDp = 800)
@Composable
private fun ReachedHomePreview() { QuotaTheme { HomeScreen(HomeUiState(previewing = true, scenario = HomeSampleState.QUOTA_REACHED), {}, {}, {}) } }

