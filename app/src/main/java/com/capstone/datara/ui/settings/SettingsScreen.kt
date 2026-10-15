package com.capstone.datara.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.BuildConfig
import com.capstone.datara.R
import com.capstone.datara.ui.auth.AuthViewModel
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraSignOutDialog
import com.capstone.datara.ui.navigation.DataraTopLevelDestination
import com.capstone.datara.ui.navigation.DataraTopLevelScaffold
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraCardBg
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraDivider
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraHeroStart
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning

@Composable
fun SettingsScreen(
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSignedOut: () -> Unit,
    onManageProfileClick: () -> Unit,
    onUsageAccessClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onDeveloperDiagnosticsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    SettingsContent(
        email = viewModel.currentUserEmail ?: stringResource(R.string.profile_default_name),
        versionName = BuildConfig.VERSION_NAME,
        onHomeClick = onHomeClick,
        onHistoryClick = onHistoryClick,
        onManageProfileClick = onManageProfileClick,
        onUsageAccessClick = onUsageAccessClick,
        onNotificationSettingsClick = onNotificationSettingsClick,
        onPrivacyClick = onPrivacyClick,
        onTermsClick = onTermsClick,
        onDeveloperDiagnosticsClick = onDeveloperDiagnosticsClick,
        onSignOutClick = { viewModel.signOut(onComplete = onSignedOut) },
        modifier = modifier
    )
}

@Composable
private fun SettingsContent(
    email: String,
    versionName: String,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onManageProfileClick: () -> Unit,
    onUsageAccessClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onDeveloperDiagnosticsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var petWarningsEnabled by rememberSaveable { mutableStateOf(true) }
    var milestoneAlertsEnabled by rememberSaveable { mutableStateOf(true) }
    var promoRemindersEnabled by rememberSaveable { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    var versionTapCount by rememberSaveable { mutableIntStateOf(0) }

    DataraTopLevelScaffold(
        selected = DataraTopLevelDestination.SETTINGS,
        onHomeClick = onHomeClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = {},
        modifier = modifier
    ) { innerPadding, expanded ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (expanded) 900.dp else maxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { SettingsHeader() }
                item { AccountCard(email, onManageProfileClick) }
                item {
                    DataraNoticeCard(
                        title = stringResource(R.string.status_local_preview),
                        message = stringResource(R.string.settings_local_toggles_notice),
                        accent = DataraBlueSoft
                    )
                }
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_notifications_section),
                        supporting = stringResource(R.string.settings_notifications_support)
                    ) {
                        ToggleSettingRow(
                            title = stringResource(R.string.settings_pet_warnings),
                            description = stringResource(R.string.settings_pet_warnings_body),
                            checked = petWarningsEnabled,
                            onCheckedChange = { petWarningsEnabled = it }
                        )
                        SectionDivider()
                        ToggleSettingRow(
                            title = stringResource(R.string.settings_milestones),
                            description = stringResource(R.string.settings_milestones_body),
                            checked = milestoneAlertsEnabled,
                            onCheckedChange = { milestoneAlertsEnabled = it }
                        )
                        SectionDivider()
                        ToggleSettingRow(
                            title = stringResource(R.string.settings_promo_reminders),
                            description = stringResource(R.string.settings_promo_reminders_body),
                            checked = promoRemindersEnabled,
                            onCheckedChange = { promoRemindersEnabled = it }
                        )
                        SectionDivider()
                        ActionSettingRow(
                            title = stringResource(R.string.settings_system_notifications),
                            description = stringResource(R.string.settings_system_notifications_body),
                            status = stringResource(R.string.action_review),
                            statusColor = DataraWarning,
                            onClick = onNotificationSettingsClick
                        )
                    }
                }
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_data_permissions_section),
                        supporting = stringResource(R.string.settings_data_permissions_support)
                    ) {
                        StatusSettingRow(
                            title = stringResource(R.string.settings_data_tracking),
                            description = stringResource(R.string.settings_data_tracking_body),
                            status = stringResource(R.string.settings_not_active),
                            statusColor = DataraTextSecondary
                        )
                        SectionDivider()
                        ActionSettingRow(
                            title = stringResource(R.string.settings_usage_access),
                            description = stringResource(R.string.settings_usage_access_body),
                            status = stringResource(R.string.action_review),
                            statusColor = DataraBlueSoft,
                            onClick = onUsageAccessClick
                        )
                        SectionDivider()
                        StatusSettingRow(
                            title = stringResource(R.string.settings_carrier_detection),
                            description = stringResource(R.string.settings_carrier_detection_body),
                            status = stringResource(R.string.settings_waiting),
                            statusColor = DataraTextSecondary
                        )
                    }
                }
                item { CloudSyncCard() }
                item {
                    SettingsSection(title = stringResource(R.string.settings_privacy_section)) {
                        ActionSettingRow(
                            title = stringResource(R.string.settings_privacy),
                            description = stringResource(R.string.settings_privacy_body),
                            onClick = onPrivacyClick
                        )
                        SectionDivider()
                        ActionSettingRow(
                            title = stringResource(R.string.settings_terms),
                            description = stringResource(R.string.settings_terms_body),
                            onClick = onTermsClick
                        )
                        SectionDivider()
                        VersionSettingRow(
                            versionName = versionName,
                            tapCount = versionTapCount,
                            onClick = {
                                val nextCount = versionTapCount + 1
                                if (nextCount >= 5) {
                                    versionTapCount = 0
                                    onDeveloperDiagnosticsClick()
                                } else {
                                    versionTapCount = nextCount
                                }
                            }
                        )
                    }
                }
                item { SignOutButton(onClick = { showSignOutDialog = true }) }
                item {
                    Text(
                        stringResource(R.string.settings_measurement_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = DataraTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    if (showSignOutDialog) {
        DataraSignOutDialog(
            title = stringResource(R.string.dashboard_sign_out_title),
            message = stringResource(R.string.dashboard_sign_out_message),
            confirmLabel = stringResource(R.string.settings_sign_out),
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showSignOutDialog = false },
            onConfirm = {
                showSignOutDialog = false
                onSignOutClick()
            }
        )
    }
}

@Composable
private fun SettingsHeader() {
    Column {
        Text(
            stringResource(R.string.settings_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            color = DataraBlueSoft,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun AccountCard(email: String, onManageProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(DataraHeroStart, DataraCardBg)), RoundedCornerShape(22.dp))
            .border(1.dp, DataraNeonBlue.copy(alpha = .25f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onManageProfileClick)
            .semantics { role = Role.Button }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Brush.linearGradient(listOf(DataraPrimaryBlue, DataraNeonBlue)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = email.firstOrNull()?.uppercaseChar()?.toString() ?: "D",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_account_title), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.settings_manage), style = MaterialTheme.typography.labelLarge, color = DataraBlueSoft)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    supporting: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = DataraTextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DataraCardBg, RoundedCornerShape(20.dp))
                .border(1.dp, DataraInputBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingText(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DataraDarkBg,
                checkedTrackColor = DataraNeonGreen,
                uncheckedThumbColor = DataraTextSecondary,
                uncheckedTrackColor = DataraDivider,
                uncheckedBorderColor = DataraDivider
            )
        )
    }
}

@Composable
private fun StatusSettingRow(title: String, description: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingText(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        StatusBadge(status, statusColor)
    }
}

@Composable
private fun ActionSettingRow(
    title: String,
    description: String,
    status: String? = null,
    statusColor: Color = DataraBlueSoft,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingText(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (status != null) StatusBadge(status, statusColor)
        else Text("›", color = DataraTextSecondary, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SettingText(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = .1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun CloudSyncCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(20.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_cloud_sync), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                Text(stringResource(R.string.settings_cloud_sync_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
            }
            StatusBadge(stringResource(R.string.status_not_connected), DataraTextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DataraDivider.copy(alpha = .55f), RoundedCornerShape(12.dp))
                .padding(13.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_pending_records), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
            Text("—", style = MaterialTheme.typography.labelLarge, color = DataraTextSecondary)
        }
    }
}

@Composable
private fun VersionSettingRow(versionName: String, tapCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_app_version), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
            Text(
                if (tapCount == 0) {
                    stringResource(R.string.settings_pre_defense_build)
                } else {
                    stringResource(R.string.settings_diagnostics_taps, 5 - tapCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DataraTextSecondary
            )
        }
        Text(versionName, style = MaterialTheme.typography.labelLarge, color = DataraBlueSoft)
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(DataraError.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .border(1.dp, DataraError.copy(alpha = .4f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.settings_sign_out), style = MaterialTheme.typography.labelLarge, color = DataraError)
    }
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DataraDivider))
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun SettingsPreview() {
    DATAraTheme {
        SettingsContent(
            email = "student@ustp.edu.ph",
            versionName = "0.1.0",
            onHomeClick = {},
            onHistoryClick = {},
            onManageProfileClick = {},
            onUsageAccessClick = {},
            onNotificationSettingsClick = {},
            onPrivacyClick = {},
            onTermsClick = {},
            onDeveloperDiagnosticsClick = {},
            onSignOutClick = {}
        )
    }
}

