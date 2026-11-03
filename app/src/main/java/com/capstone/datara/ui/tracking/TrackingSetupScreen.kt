package com.capstone.datara.ui.tracking

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.capstone.datara.telemetry.TelemetryAccess
import com.capstone.datara.telemetry.TrackingAccess
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.DataraActionButton
import com.capstone.datara.ui.components.DataraAppBar
import com.capstone.datara.ui.components.TonalIcon
import com.capstone.datara.ui.theme.QuotaTheme

@Composable
fun TrackingSetupScreen(onBack: () -> Unit, viewModel: TrackingSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Grants change in Android Settings, outside the app: re-check whenever the screen returns
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // After a denial Android may stop showing the dialog; then only App info can grant it
    var runtimeAsked by rememberSaveable { mutableStateOf(false) }
    val requestRuntime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        runtimeAsked = true
        viewModel.refresh()
    }
    QuotaTheme {
        TrackingSetupContent(
            state = state,
            runtimeAsked = runtimeAsked,
            onBack = onBack,
            onOpenUsageAccess = { context.openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            onRequestRuntime = { requestRuntime.launch(TelemetryAccess.RUNTIME_PERMISSIONS) },
            onOpenAppSettings = { context.openSettings(context.appDetailsIntent()) },
            onAllowBackground = { context.openBatterySettings() },
            onStart = viewModel::start,
            onStop = viewModel::stop
        )
    }
}

@Composable
private fun TrackingSetupContent(
    state: TrackingSetupUiState,
    runtimeAsked: Boolean,
    onBack: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onRequestRuntime: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAllowBackground: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val access = state.access
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar("Tracking setup", onBack = onBack) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("DATAra measures mobile data on this phone to forecast when you’ll reach your quota. Wi-Fi isn’t counted, messages are never read, and nothing is blocked.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SetupStep(1, "Usage Access", DataraIcons.Data, granted = access.usageAccess, required = true,
                body = "Android grants this in its Settings app, not in a pop-up. Find DATAra in the list and turn on usage access. DATAra uses it to read your phone’s mobile data totals.") {
                if (!access.usageAccess) OutlinedButton(onClick = onOpenUsageAccess, Modifier.heightIn(min = 48.dp)) { Text("Open Usage access settings") }
            }

            SetupStep(2, "Notifications and phone state", DataraIcons.Bell, granted = access.notifications && access.phoneState,
                required = !access.notifications,
                body = "Notifications show while tracking runs. Phone state lets DATAra tell 5G, LTE and 3G apart; without it the network is recorded as “cellular”. Calls and messages are never read.") {
                GrantRow("Notifications", access.notifications, required = true)
                GrantRow("Phone state", access.phoneState, required = false)
                if (!access.notifications || !access.phoneState) {
                    if (runtimeAsked) {
                        OutlinedButton(onClick = onOpenAppSettings, Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                    } else {
                        OutlinedButton(onClick = onRequestRuntime, Modifier.heightIn(min = 48.dp)) { Text("Allow permissions") }
                    }
                }
            }

            SetupStep(3, "Keep tracking running", DataraIcons.Shield, granted = access.batteryUnrestricted, required = false,
                body = "Android may stop tracking in the background to save battery. Letting DATAra run unrestricted lets it restart on its own. Some phones also need Autostart turned on in their own settings.") {
                if (!access.batteryUnrestricted) OutlinedButton(onClick = onAllowBackground, Modifier.heightIn(min = 48.dp)) { Text("Allow background running") }
            }

            TrackingStatusCard(state, onStart, onStop)
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, icon: ImageVector, granted: Boolean, required: Boolean, body: String,
    actions: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalIcon(if (granted) DataraIcons.CheckCircle else icon, positive = granted)
                Column(Modifier.weight(1f)) {
                    Text("Step $number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                StatusPill(granted, required)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

@Composable
private fun GrantRow(label: String, granted: Boolean, required: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StatusPill(granted, required)
    }
}

@Composable
private fun StatusPill(granted: Boolean, required: Boolean) {
    val (text, container, content) = when {
        granted -> Triple("Allowed", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        required -> Triple("Needed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("Recommended", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun TrackingStatusCard(state: TrackingSetupUiState, onStart: () -> Unit, onStop: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tracking · ${state.status.label}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(when (state.status) {
                TrackingStatus.ON -> "Measuring mobile data every 30 seconds, with a fuller check every 10 minutes. A notification stays visible while tracking runs."
                TrackingStatus.PAUSED -> "Android stopped tracking. It restarts on its own when it can, or start it again now."
                TrackingStatus.OFF -> "Start tracking to begin measuring. A notification stays visible while it runs."
            }, style = MaterialTheme.typography.bodyMedium)
            Text("Readings stay on this phone for now and are cleared when Android closes the app. Saved history arrives in a later update.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.isRunning) {
                DataraActionButton(if (state.status == TrackingStatus.PAUSED) "Resume tracking" else "Start tracking", onStart, enabled = state.canStart)
                state.startBlockedReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            if (state.canStop) {
                OutlinedButton(onClick = onStop, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text("Stop tracking")
                }
            }
        }
    }
}

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

// The exemption is the only way the 15-minute watchdog can restart a killed foreground service
// on Android 12+; the user approved asking for it (see ROADMAP D4).
@SuppressLint("BatteryLife")
private fun Context.openBatterySettings() {
    try {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
    } catch (e: ActivityNotFoundException) {
        openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Preview(name = "Nothing granted", widthDp = 360, heightDp = 1200)
@Composable
private fun SetupEmptyPreview() {
    QuotaTheme { TrackingSetupContent(TrackingSetupUiState(), false, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Tracking on", widthDp = 360, heightDp = 1200)
@Preview(name = "Large text", widthDp = 360, heightDp = 1600, fontScale = 2f)
@Composable
private fun SetupRunningPreview() {
    QuotaTheme {
        TrackingSetupContent(TrackingSetupUiState(TrackingAccess(true, true, true, false), isRunning = true, shouldCollect = true),
            false, {}, {}, {}, {}, {}, {}, {})
    }
}
