package com.capstone.datara.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.capstone.datara.alerts.AlertPreferences
import com.capstone.datara.alerts.QuotaAlertNotifications
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.BuildConfig
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.profile.ProfileScreen
import com.capstone.datara.ui.profile.ProfileUiState
import com.capstone.datara.ui.quota.QuotaBottomBar
import com.capstone.datara.ui.theme.QuotaTheme

@Composable
fun SettingsScreen(email: String?, onSaveName: (String) -> String?,
    onMilestonesChange: (Boolean) -> Unit, onForecastsChange: (Boolean) -> Unit,
    onHome: () -> Unit, onHistory: () -> Unit, onSignOut: () -> Unit,
    isSigningOut: Boolean = false, signOutError: String? = null,
    profile: ProfileUiState = ProfileUiState(), onNameEdited: () -> Unit = {}, onTrackingSetup: () -> Unit = {},
    alertPreferences: AlertPreferences = AlertPreferences(), status: SettingsStatus = SettingsStatus(),
    onRefreshStatus: () -> Unit = {}, onOpenDiagnostics: () -> Unit = {}) {
    QuotaTheme {
        SettingsContent(email, onSaveName, onMilestonesChange, onForecastsChange,
            onHome, onHistory, onSignOut, isSigningOut, signOutError, profile, onNameEdited, onTrackingSetup,
            alertPreferences, status, onRefreshStatus, onOpenDiagnostics)
    }
}

/** Taps on the version that open the hidden diagnostics screen (D9). */
const val DIAGNOSTICS_TAPS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(email: String?, onSaveName: (String) -> String?,
    onMilestonesChange: (Boolean) -> Unit, onForecastsChange: (Boolean) -> Unit,
    onHome: () -> Unit, onHistory: () -> Unit, onSignOut: () -> Unit,
    isSigningOut: Boolean, signOutError: String?,
    profileState: ProfileUiState, onNameEdited: () -> Unit, onTrackingSetup: () -> Unit,
    alertPreferences: AlertPreferences, status: SettingsStatus, onRefreshStatus: () -> Unit,
    onOpenDiagnostics: () -> Unit) {
    var profile by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(profile) { profile = false }
    val bottomBar: @Composable () -> Unit = {
        QuotaBottomBar(false, onHome, onHistory, { profile = false }, settingsSelected = true)
    }
    if (profile) {
        ProfileScreen(profileState, email, onBack = { profile = false }, onSaveName = onSaveName,
            onNameEdited = onNameEdited, bottomBar = bottomBar)
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar, topBar = { DataraCollapsingAppBar("Settings", scrollBehavior) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Profile card: tonal avatar initial, name, email, chevron
            Card(onClick = { profile = true }, shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarInitial(profileState.name, size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profileState.name.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge)
                        Text(email ?: "Email unavailable", style = MaterialTheme.typography.bodyMedium)
                        Text("View and edit profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(DataraIcons.Chevron, null, modifier = Modifier.size(18.dp))
                }
            }
            SettingsCard("Notification preferences") {
                PreferenceSwitch(PreferenceKey.MILESTONES, "Quota milestones", "At 75%, 90% and 100% of your quota", alertPreferences.milestones, onMilestonesChange)
                PreferenceSwitch(PreferenceKey.FORECASTS, "Forecast reminders", "When your quota is likely to be reached within 24 hours and within 3 hours",
                    alertPreferences.forecasts, onForecastsChange)
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Each alert is sent once per quota. Reaching a quota never cuts your connection.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SystemNotificationStatus()
                }
            }
            SettingsCard("Tracking status") {
                LifecycleResumeEffect(Unit) {
                    onRefreshStatus()
                    onPauseOrDispose { }
                }
                StatusLine(DataraIcons.Data, "Tracking service", status.serviceRunning, "Running", "Stopped")
                StatusLine(DataraIcons.Shield, "Usage Access", status.access.usageAccess, "Granted", "Not granted")
                StatusLine(DataraIcons.Bell, "Notifications", status.access.notifications, "Allowed", "Off")
                StatusLine(DataraIcons.Lock, "Battery optimization", status.access.batteryUnrestricted, "Unrestricted", "Restricted")
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Usage Access and notifications are needed to measure mobile data; an unrestricted battery lets tracking restart on its own.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onTrackingSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open tracking setup") }
                }
            }
            // The "Cloud sync" card returns when sync (C3) exists; until then there is nothing to show.
            SettingsCard("About DATAra") {
                var taps by remember { mutableIntStateOf(0) }
                ListItem(leadingContent = { TonalIcon(DataraIcons.CheckCircle) },
                    headlineContent = { Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("Forecast when mobile usage reaches a quota you choose.") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(interactionSource = null, indication = null) {
                        taps++
                        if (taps >= DIAGNOSTICS_TAPS) { taps = 0; onOpenDiagnostics() }
                    })
                ListItem(leadingContent = { TonalIcon(DataraIcons.Shield) },
                    headlineContent = { Text("Privacy and tracking", style = MaterialTheme.typography.titleMedium) },
                    trailingContent = { Icon(DataraIcons.Chevron, null, modifier = Modifier.size(18.dp)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(role = Role.Button) { dialog = "Privacy and tracking" })
            }
            OutlinedButton(onClick = { dialog = "Sign out" }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Sign out") }
        }
    }
    dialog?.let { title ->
        val signingOut = title == "Sign out"
        AlertDialog(onDismissRequest = { if (!isSigningOut) dialog = null }, title = { Text(if (signingOut) "Sign out of DATAra?" else title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (title) {
                    "Sign out" -> "You’ll need to sign in again to use DATAra. Your quotas and usage stay on this phone."
                    else -> PRIVACY_TEXT
                })
                if (signingOut && signOutError != null) Text(signOutError, color = MaterialTheme.colorScheme.error)
            } }, confirmButton = {
                TextButton(onClick = { if (signingOut) onSignOut() else dialog = null }, enabled = !isSigningOut) {
                    Text(if (signingOut && isSigningOut) "Signing out…" else if (signingOut) "Confirm sign out" else "Got it")
                }
            }, dismissButton = { if (signingOut) TextButton(onClick = { dialog = null }, enabled = !isSigningOut) { Text("Cancel") } })
    }
}

/** Whether Android shows the alerts; they stay in the in-app inbox either way. */
@Composable
private fun SystemNotificationStatus() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(QuotaAlertNotifications.canPost(context)) }
    // The grant changes in Android Settings, outside the app: re-check when the screen returns
    LifecycleResumeEffect(Unit) {
        allowed = QuotaAlertNotifications.canPost(context)
        onPauseOrDispose { }
    }
    // After a denial Android may stop showing the dialog; then only the app's notification settings can allow it
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        allowed = QuotaAlertNotifications.canPost(context)
    }
    if (allowed) return
    Text("Android notifications are off for DATAra. Alerts still appear in the in-app inbox.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = {
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canAsk) request.launch(Manifest.permission.POST_NOTIFICATIONS)
        else runCatching {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow notifications") }
}

/** Plain-language privacy summary; keep it true to what the app does today. */
internal const val PRIVACY_TEXT =
    "DATAra measures your mobile data on this phone. Your quotas, usage, forecasts and alerts are stored here too.\n\n" +
    "Each install gets a random device ID. Your sign-in and profile name are handled by Supabase Auth.\n\n" +
    "DATAra never reads your SMS and never blocks your internet. Cloud sync isn’t active yet, so your usage data stays on this phone."

/** A status row: tinted leading icon, label, and its state as a pill (teal when fine, red when it needs attention). */
@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ok: Boolean, okText: String, badText: String) {
    ListItem(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        leadingContent = { TonalIcon(icon, positive = ok) },
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        // Pill sits under the label so long values and large text never squeeze the row
        supportingContent = { StatusPill(if (ok) okText else badText, if (ok) StatusTone.GOOD else StatusTone.PROBLEM, Modifier.padding(top = 6.dp)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}

/** The profile initial in a tonal circle (person icon when there is no name yet). */
@Composable
internal fun AvatarInitial(name: String, size: androidx.compose.ui.unit.Dp) {
    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (name.isBlank()) Icon(DataraIcons.Person, null, modifier = Modifier.size(size / 2))
            else Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

internal enum class PreferenceKey { MILESTONES, FORECASTS }

@Composable
private fun PreferenceSwitch(key: PreferenceKey, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        leadingContent = { TonalIcon(when (key) { PreferenceKey.MILESTONES -> DataraIcons.Data; PreferenceKey.FORECASTS -> DataraIcons.Clock }) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { heading() })
            content()
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Settings large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Settings landscape", widthDp = 800, heightDp = 360)
@Composable
private fun SettingsPreview() {
    SettingsScreen("student@example.com", { null }, {}, {}, {}, {}, {})
}
