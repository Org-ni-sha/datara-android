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
import com.capstone.datara.ui.notifications.*
import com.capstone.datara.ui.settings.*
import com.capstone.datara.ui.auth.AuthUiState
import com.capstone.datara.ui.theme.*

@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    viewModel: QuotaDemoViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    notificationsViewModel: NotificationPreviewViewModel = hiltViewModel(),
    settingsViewModel: SettingsPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val notificationState by notificationsViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()
    BackHandler(creating || details || notifications || history || settings) {
        if (creating) creating = false else if (details) details = false else if (notifications) notifications = false else { history = false; settings = false }
    }
    if (creating) {
        CreateQuotaScreen(onBack = { creating = false }, onCreateQuota = { bytes, name ->
            viewModel.createQuota(bytes, name)
            creating = false
            details = false
        })
    } else if (details) {
        QuotaDetailsScreen(state, onBack = { details = false },
            onEndCycle = { viewModel.endCycle() }, onCreateQuota = { creating = true })
    } else if (notifications) {
        NotificationsScreen(notificationState, notificationsViewModel.sampleCycle,
            onBack = { notifications = false }, onMarkRead = notificationsViewModel::markRead,
            onMarkAllRead = notificationsViewModel::markAllRead)
    } else if (settings) {
        screenStates.SaveableStateProvider("settings") {
            SettingsScreen(settingsState, authViewModel.currentUserEmail,
                onSaveName = settingsViewModel::saveName,
                onMilestonesChange = settingsViewModel::setMilestones,
                onForecastsChange = settingsViewModel::setForecasts,
                onSelectSync = settingsViewModel::selectSync,
                onPreviewSync = settingsViewModel::previewSync,
                onHome = { settings = false; history = false },
                onHistory = { settings = false; history = true },
                onSignOut = { authViewModel.signOut(onComplete = onSignedOut) },
                isSigningOut = authState is AuthUiState.Loading,
                signOutError = (authState as? AuthUiState.Error)?.message)
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
            onSettings = { settings = true; history = false })
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onCreateQuota: () -> Unit,
    onSelectScenario: (HomeSampleState) -> Unit, onSignOut: () -> Unit,
    modifier: Modifier = Modifier, onViewQuota: () -> Unit = {}, onHistory: () -> Unit = {},
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, onSettings: () -> Unit = {}) {
    QuotaTheme { HomeContent(state, onCreateQuota, onSelectScenario, onSignOut, modifier, onViewQuota, onHistory, onNotifications, unreadCount, onSettings) }
}

@Preview(name = "No quota", widthDp = 360, heightDp = 800)
@Composable
private fun EmptyHomePreview() { QuotaTheme { HomeScreen(HomeUiState(), {}, {}, {}) } }
@Preview(name = "Forecast", widthDp = 360, heightDp = 800)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun ForecastHomePreview() { QuotaTheme { HomeScreen(HomeUiState(HomeSampleState.FORECAST_AVAILABLE), {}, {}, {}) } }
@Preview(name = "Collecting", widthDp = 360, heightDp = 800)
@Composable
private fun CollectingHomePreview() { QuotaTheme { HomeScreen(HomeUiState(HomeSampleState.COLLECTING), {}, {}, {}) } }
@Preview(name = "Permission missing", widthDp = 360, heightDp = 800)
@Composable
private fun PermissionHomePreview() { QuotaTheme { HomeScreen(HomeUiState(HomeSampleState.MISSING_PERMISSION), {}, {}, {}) } }
@Preview(name = "Quota reached", widthDp = 360, heightDp = 800)
@Composable
private fun ReachedHomePreview() { QuotaTheme { HomeScreen(HomeUiState(HomeSampleState.QUOTA_REACHED), {}, {}, {}) } }

