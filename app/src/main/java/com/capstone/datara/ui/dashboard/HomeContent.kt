package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.notifications.NotificationBell
import com.capstone.datara.ui.quota.*

@Composable
internal fun HomeContent(state: HomeUiState, onCreateQuota: () -> Unit,
    onSelectScenario: (HomeSampleState) -> Unit, onSignOut: () -> Unit,
    modifier: Modifier = Modifier, onViewQuota: () -> Unit = {}, onHistory: () -> Unit = {},
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, onSettings: () -> Unit = {}) {
    var info by remember { mutableStateOf<String?>(null) }
    val empty = state.scenario == HomeSampleState.NO_QUOTA
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar("DATAra", actions = {
            NotificationBell(unreadCount, onNotifications)
            PreviewMenu(HomeSampleState.entries.map { option -> option.label to { onSelectScenario(option) } })
        }) }, bottomBar = { QuotaBottomBar(false, {}, onHistory, onSettings) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your mobile usage", style = MaterialTheme.typography.headlineMedium)
                Text("Forecasting against your chosen quota.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ForecastHero(state, onCreateQuota, onAccess = { info = "Usage Access" }, onViewQuota = onViewQuota)
            if (!empty) {
                if (state.scenario == HomeSampleState.FORECAST_AVAILABLE || state.scenario == HomeSampleState.QUOTA_REACHED) {
                    HomeUsageTrend()
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(leadingContent = { TonalIcon(DataraIcons.Data) },
                            headlineContent = { Text(if (state.scenario == HomeSampleState.MISSING_PERMISSION) "Usage unavailable" else "Usage trend pending") },
                            supportingContent = { Text(if (state.scenario == HomeSampleState.MISSING_PERMISSION) "Restore access to read mobile usage." else "Your trend will appear after usage readings are collected.") })
                    }
                }
            } else {
                SectionTitle("How tracking works")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    listOf(Triple(DataraIcons.Plus, "Choose your quota", "An amount in MB or GB, just for you."),
                        Triple(DataraIcons.Data, "Follow your usage", "Mobile uploads and downloads count."),
                        Triple(DataraIcons.Clock, "Look a little ahead", "See when you’re likely to reach it.")).forEach { (icon, title, description) ->
                        ListItem(leadingContent = { TonalIcon(icon) }, headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(description) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SampleLabel()
                TextButton(onClick = { info = "What counts?" }) {
                    Icon(DataraIcons.Shield, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("What counts?")
                }
            }
        }
    }
    info?.let { title -> AlertDialog(onDismissRequest = { info = null }, title = { Text(title) },
        text = { Text(if (title == "Usage Access") "This preview doesn’t request Android permissions. Preview enabled access to see the collecting state."
        else "Your quota counts mobile uploads and downloads on this device, including promo freebies. Wi-Fi is excluded. It does not represent your carrier balance or block internet access. This preview uses sample data.") },
        confirmButton = { TextButton(onClick = { if (title == "Usage Access") onSelectScenario(HomeSampleState.COLLECTING); info = null }) {
            Text(if (title == "Usage Access") "Preview enabled access" else "Got it") } },
        dismissButton = { TextButton(onClick = { info = null }) { Text("Close") } }) }
}

@Composable
private fun ForecastHero(state: HomeUiState, onCreate: () -> Unit, onAccess: () -> Unit, onViewQuota: () -> Unit) {
    val empty = state.scenario == HomeSampleState.NO_QUOTA
    val reached = state.scenario == HomeSampleState.QUOTA_REACHED
    val missing = state.scenario == HomeSampleState.MISSING_PERMISSION
    val collecting = state.scenario == HomeSampleState.COLLECTING && !state.isEnded
    val statusColor = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalIcon(if (empty) DataraIcons.Plus else if (missing) DataraIcons.Shield else DataraIcons.Clock, positive = !missing)
                Text(when {
                    empty -> "YOUR TRACKING REFERENCE"
                    state.isEnded -> "CYCLE ENDED"
                    missing -> "ACCESS NEEDED"
                    reached -> "QUOTA REACHED"
                    collecting -> "COLLECTING USAGE"
                    else -> "ESTIMATED TIME TO QUOTA"
                }, style = MaterialTheme.typography.labelLarge, color = statusColor)
            }
            Text(when {
                state.isEnded -> "Cycle ended."
                empty -> "Set a tracking quota"
                reached -> "Quota reached."
                missing -> "Usage access needed"
                collecting -> "Collecting usage"
                else -> "About 12 hours"
            }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Surface(color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (missing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium) {
                Text(when {
                    state.isEnded -> "The sample forecast has stopped. You can start a new quota."
                    empty -> "Choose an amount in MB or GB. DATAra estimates when you will reach it."
                    reached -> "You have reached your chosen amount. Your internet stays on."
                    missing -> "Mobile usage cannot be read until Usage Access is enabled."
                    collecting -> "Waiting for sample readings"
                    else -> "Illustrative forecast based on sample usage. Your actual pace may change."
                }, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (!empty) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.name, style = MaterialTheme.typography.titleLarge)
                    Text("Tracking ${formatQuotaData(state.amountBytes)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    missing -> Text("Remaining quota unavailable", style = MaterialTheme.typography.bodyMedium)
                    collecting -> Text("Usage has not been measured yet.", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        val progress by animateFloatAsState(state.progress.coerceIn(0f, 1f), tween(350), label = "Quota progress")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                            contentDescription = "${(state.progress * 100).toInt()} percent of quota used"
                        }, color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            drawStopIndicator = {})
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuotaMetric("${formatQuotaData(state.remainingBytes)} ${if (state.isEnded) "unused at end" else "remaining"}", "Current cycle", Modifier.fillMaxWidth())
                                    QuotaMetric(formatQuotaData(state.usedBytes), if (state.isEnded) "Used at end" else "Used this cycle", Modifier.fillMaxWidth())
                                }
                            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                QuotaMetric("${formatQuotaData(state.remainingBytes)} ${if (state.isEnded) "unused at end" else "remaining"}", "Current cycle", Modifier.weight(1f))
                                QuotaMetric(formatQuotaData(state.usedBytes), if (state.isEnded) "Used at end" else "Used this cycle", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (empty || reached || state.isEnded || missing) {
                DataraActionButton(if (missing && !state.isEnded) "About Usage Access" else if (empty) "Create a quota" else "Create another quota",
                    if (missing && !state.isEnded) onAccess else onCreate)
            }
            if (!empty) FilledTonalButton(onClick = onViewQuota, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                Text("View quota details"); Spacer(Modifier.width(12.dp)); Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuotaMetric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
