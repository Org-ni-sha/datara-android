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
fun SettingsScreen(state: SettingsPreviewState, email: String?, onSaveName: (String) -> String?,
    onMilestonesChange: (Boolean) -> Unit, onForecastsChange: (Boolean) -> Unit,
    onSelectSync: (SyncPreview) -> Unit, onPreviewSync: () -> Unit,
    onHome: () -> Unit, onHistory: () -> Unit, onSignOut: () -> Unit,
    isSigningOut: Boolean = false, signOutError: String? = null,
    profile: ProfileUiState = ProfileUiState(), onNameEdited: () -> Unit = {}, onTrackingSetup: () -> Unit = {},
    alertPreferences: AlertPreferences = AlertPreferences()) {
    QuotaTheme {
        SettingsContent(state, email, onSaveName, onMilestonesChange, onForecastsChange, onSelectSync,
            onPreviewSync, onHome, onHistory, onSignOut, isSigningOut, signOutError, profile, onNameEdited, onTrackingSetup,
            alertPreferences)
    }
}

@Composable
private fun SettingsContent(state: SettingsPreviewState, email: String?, onSaveName: (String) -> String?,
    onMilestonesChange: (Boolean) -> Unit, onForecastsChange: (Boolean) -> Unit,
    onSelectSync: (SyncPreview) -> Unit, onPreviewSync: () -> Unit,
    onHome: () -> Unit, onHistory: () -> Unit, onSignOut: () -> Unit,
    isSigningOut: Boolean, signOutError: String?,
    profileState: ProfileUiState, onNameEdited: () -> Unit, onTrackingSetup: () -> Unit,
    alertPreferences: AlertPreferences) {
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar,
        topBar = { DataraAppBar("Settings", actions = { PreviewMenu(SyncPreview.entries.map { option -> "Sync preview: ${option.label}" to { onSelectSync(option) } }) }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle("App preferences", "Manage alerts, access and your profile.")
            Surface(onClick = { profile = true }, color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                        Icon(DataraIcons.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                    Text(profileState.name.ifBlank { "Your profile" }, style = MaterialTheme.typography.headlineMedium)
                    Text(email ?: "Email unavailable", style = MaterialTheme.typography.bodyMedium)
                    Text("View and edit profile →", style = MaterialTheme.typography.labelLarge)
                }
            }
            SettingsCard("Notification preferences") {
                PreferenceSwitch("Quota milestones", "At 75%, 90% and 100% of your quota", alertPreferences.milestones, onMilestonesChange)
                PreferenceSwitch("Forecast reminders", "When your quota is likely to be reached within 24 hours and within 3 hours",
                    alertPreferences.forecasts, onForecastsChange)
                Text("Each alert is sent once per quota. Reaching a quota never cuts your connection.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SystemNotificationStatus()
            }
            SettingsCard("Tracking status") {
                Text("Usage Access and permissions", style = MaterialTheme.typography.titleMedium)
                Text("Grant access and start or stop measuring mobile data. Home and History still show sample values.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onTrackingSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open tracking setup") }
            }
            SettingsCard("Cloud sync") {
                Text("Sample status · ${state.sync.label}", style = MaterialTheme.typography.titleMedium)
                Text("${state.pendingItems} sample items pending")
                Text(if (state.sync == SyncPreview.SYNCED) "Last sync: just now · simulated" else "Last sync: not connected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (state.sync) {
                    SyncPreview.PENDING -> "Preview how queued readings will appear."
                    SyncPreview.SYNCED -> "Simulated sync complete. No data was uploaded."
                    SyncPreview.OFFLINE -> "Sample offline state. Tracking history would remain on the device."
                    SyncPreview.ERROR -> "Sample sync error. You can preview a successful retry."
                }, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPreviewSync, shape = MaterialTheme.shapes.medium, enabled = state.sync != SyncPreview.OFFLINE && state.sync != SyncPreview.SYNCED) {
                    Text("Preview manual sync")
                }
            }
            SettingsCard("About DATAra") {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text("Forecast when mobile usage reaches a quota you choose.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { dialog = "Privacy and tracking" }) { Text("Privacy and tracking") }
            }
            SampleLabel("Cloud sync status is a preview")
            OutlinedButton(onClick = { dialog = "Sign out" }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Sign out") }
        }
    }
    dialog?.let { title ->
        val signingOut = title == "Sign out"
        AlertDialog(onDismissRequest = { if (!isSigningOut) dialog = null }, title = { Text(if (signingOut) "Sign out of DATAra?" else title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (title) {
                    "Sign out" -> "You’ll need to sign in again to access your account. Settings stay on this phone; cloud sync is not connected yet."
                    else -> "Usage, quotas, forecasts and alerts are measured and kept on this phone; History and sync are still previews. Signing in uses Supabase Auth. DATAra does not read SMS or block internet access. Data retention, deletion and real sync controls will be documented when storage is connected."
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

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        leadingContent = { TonalIcon(if (title == "Quota milestones") DataraIcons.Data else DataraIcons.Clock) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) })
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Settings large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Settings landscape", widthDp = 800, heightDp = 360)
@Composable
private fun SettingsPreview() {
    SettingsScreen(SettingsPreviewState(), "student@example.com", { null }, {}, {}, {}, {}, {}, {}, {})
}
