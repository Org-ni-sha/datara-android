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
import androidx.compose.ui.semantics.heading
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.notifications.NotificationBell
import com.capstone.datara.ui.quota.*

@Composable
internal fun HomeContent(state: HomeUiState, onCreateQuota: () -> Unit,
    onSelectScenario: (HomeSampleState) -> Unit, onSignOut: () -> Unit,
    modifier: Modifier = Modifier, onViewQuota: () -> Unit = {}, onHistory: () -> Unit = {},
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, onSettings: () -> Unit = {},
    onTrackingSetup: () -> Unit = {}, live: QuotaDetailsModel? = null, liveLoading: Boolean = false,
    trackingIssue: TrackingIssue? = null, week: HomeWeek? = null, onShowRealData: () -> Unit = {}) {
    var showInfo by remember { mutableStateOf(false) }
    val hero = HeroContent.select(state, live, liveLoading, trackingIssue)
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar("DATAra", actions = {
            NotificationBell(unreadCount, onNotifications)
            // Review tool for the defense screens; removed before the freeze
            PreviewMenu(listOf("Show my real quota" to onShowRealData) +
                HomeSampleState.entries.map { option -> "Sample: ${option.label}" to { onSelectScenario(option) } })
        }) }, bottomBar = { QuotaBottomBar(false, {}, onHistory, onSettings) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.previewing) {
                StatusPill("Sample screen · Preview options", StatusTone.NEUTRAL)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your mobile usage", style = MaterialTheme.typography.headlineMedium)
                Text("Forecasting against your chosen quota.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ForecastHero(hero, onCreateQuota, onAccess = onTrackingSetup, onViewQuota = onViewQuota)
            if (hero.loading) {
                // Nothing more until the cycle is known
            } else if (!hero.empty) {
                if (hero.missing || hero.hideUsage) {
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(leadingContent = { TonalIcon(DataraIcons.Data) },
                            headlineContent = { Text(if (hero.missing) "Usage unavailable" else "Usage trend pending") },
                            supportingContent = { Text(if (hero.missing) "Turn on Usage Access to read mobile usage." else "Your trend appears once usage has been measured.") },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                    }
                } else {
                    HomeUsageTrend(week, onHistory)
                }
            } else {
                SectionTitle("How tracking works")
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    listOf(Triple(DataraIcons.Plus, "Choose your quota", "An amount in MB or GB, just for you."),
                        Triple(DataraIcons.Data, "Follow your usage", "Mobile uploads and downloads count."),
                        Triple(DataraIcons.Clock, "Look a little ahead", "See when you’re likely to reach it.")).forEach { (icon, title, description) ->
                        ListItem(leadingContent = { TonalIcon(icon) }, headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(description) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            TextButton(onClick = { showInfo = true }, modifier = Modifier.align(Alignment.End)) {
                Icon(DataraIcons.Shield, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("What counts?")
            }
        }
    }
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("What counts?") },
        text = { Text("Mobile uploads and downloads on this phone, including promo freebies. Wi-Fi doesn’t count. Your quota isn’t your carrier balance, and DATAra never blocks your internet. The forecast is calculated on this phone.") },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it") } })
}

/** What the forecast hero shows: a real cycle with its live forecast, or a labelled sample. */
internal data class HeroContent(
    val empty: Boolean,
    val reached: Boolean,
    val ended: Boolean,
    val missing: Boolean,
    /** Usage figures are not shown yet (sample "collecting"). */
    val hideUsage: Boolean,
    val label: String,
    val headline: String,
    val body: String,
    val name: String,
    val amountBytes: Long,
    val usedBytes: Long,
    val remainingBytes: Long,
    val progress: Float,
    val isLive: Boolean,
    /** Shown as a warning banner inside the hero, with "Fix tracking". */
    val issue: TrackingIssue? = null,
    val loading: Boolean = false
) {
    companion object {
        /**
         * A Preview options scenario while one is picked; otherwise the real cycle (loading,
         * live, or the real empty state). A real cycle never shows sample data by default.
         */
        fun select(state: HomeUiState, live: QuotaDetailsModel?, liveLoading: Boolean, issue: TrackingIssue?): HeroContent = when {
            state.previewing -> sample(state)
            liveLoading -> LOADING
            live != null -> live(live, issue)
            else -> sample(HomeUiState())
        }

        val LOADING = HeroContent(empty = false, reached = false, ended = false, missing = false, hideUsage = true,
            label = "LOADING", headline = "Loading your quota…", body = "", name = "", amountBytes = 0, usedBytes = 0,
            remainingBytes = 0, progress = 0f, isLive = false, loading = true)

        fun sample(state: HomeUiState): HeroContent {
            if (state.scenario == HomeSampleState.LOADING) return LOADING
            if (state.scenario == HomeSampleState.TRACKING_OFF && !state.isEnded) {
                return sample(state.copy(scenario = HomeSampleState.FORECAST_AVAILABLE)).copy(issue = TrackingIssue.SERVICE_STOPPED)
            }
            val empty = state.scenario == HomeSampleState.NO_QUOTA
            val reached = state.scenario == HomeSampleState.QUOTA_REACHED
            val missing = state.scenario == HomeSampleState.MISSING_PERMISSION
            val collecting = state.scenario == HomeSampleState.COLLECTING && !state.isEnded
            return HeroContent(
                empty = empty, reached = reached, ended = state.isEnded, missing = missing, hideUsage = collecting,
                label = when {
                    empty -> "YOUR TRACKING REFERENCE"
                    state.isEnded -> "CYCLE ENDED"
                    missing -> "ACCESS NEEDED"
                    reached -> "QUOTA REACHED"
                    collecting -> "COLLECTING USAGE"
                    else -> "ESTIMATED TIME TO QUOTA"
                },
                headline = when {
                    state.isEnded -> "Cycle ended."
                    empty -> "Set a tracking quota"
                    reached -> "Quota reached."
                    missing -> "Usage access needed"
                    collecting -> "Collecting usage"
                    else -> "About 12 hours"
                },
                body = when {
                    state.isEnded -> "The sample forecast has stopped. You can start a new quota."
                    empty -> "Choose an amount in MB or GB. DATAra estimates when you will reach it."
                    reached -> "You have reached your chosen amount. Your internet stays on."
                    missing -> "Mobile usage cannot be read until Usage Access is enabled."
                    collecting -> "Waiting for sample readings"
                    else -> "Illustrative forecast based on sample usage. Your actual pace may change."
                },
                name = state.name, amountBytes = state.amountBytes, usedBytes = state.usedBytes,
                remainingBytes = state.remainingBytes, progress = state.progress, isLive = false
            )
        }

        /** A real cycle (D3) and its on-device forecast (D6). */
        fun live(details: QuotaDetailsModel, issue: TrackingIssue? = null): HeroContent {
            val forecast = details.forecast
            return HeroContent(
                empty = false, reached = details.isReached, ended = details.isEnded, missing = false, hideUsage = false,
                label = when {
                    details.isEnded -> "CYCLE ENDED"
                    details.isReached -> "QUOTA REACHED"
                    else -> forecast?.label ?: "FORECASTING"
                },
                headline = when {
                    details.isEnded -> "Cycle ended."
                    details.isReached -> "Quota reached."
                    else -> forecast?.title ?: "Preparing your forecast"
                },
                body = when {
                    details.isEnded -> "This cycle has ended. You can start a new quota."
                    details.isReached -> "You have reached your chosen amount. Your internet stays on."
                    else -> forecast?.body ?: "The forecast is calculated on this device from your measured usage."
                },
                name = details.name, amountBytes = details.quotaBytes, usedBytes = details.usedBytes ?: 0,
                remainingBytes = details.remainingBytes ?: 0, progress = details.progress, isLive = true,
                issue = issue.takeIf { !details.isEnded && !details.isReached }
            )
        }
    }
}

@Composable
private fun ForecastHero(hero: HeroContent, onCreate: () -> Unit, onAccess: () -> Unit, onViewQuota: () -> Unit) {
    val empty = hero.empty
    val missing = hero.missing
    val statusColor = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (hero.loading) {
            Column(Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                Text(hero.headline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Card
        }
        Column(Modifier.fillMaxWidth().animateContentSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            hero.issue?.let { TrackingBanner(it, onAccess) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalIcon(if (empty) DataraIcons.Plus else if (missing) DataraIcons.Shield else DataraIcons.Clock, positive = !missing)
                Text(hero.label, style = MaterialTheme.typography.labelLarge, color = statusColor, modifier = Modifier.weight(1f))
            }
            Text(hero.headline, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Surface(color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (missing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium) {
                Text(hero.body, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (!empty) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hero.name, style = MaterialTheme.typography.titleLarge)
                    Text("Tracking ${formatQuotaData(hero.amountBytes)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    missing -> Text("Remaining quota unavailable", style = MaterialTheme.typography.bodyMedium)
                    hero.hideUsage -> Text("Usage has not been measured yet.", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        val progress by animateFloatAsState(hero.progress.coerceIn(0f, 1f), tween(350), label = "Quota progress")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                            contentDescription = "${(hero.progress * 100).toInt()} percent of quota used"
                        }, color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            drawStopIndicator = {})
                        val remaining = "${formatQuotaData(hero.remainingBytes)} ${if (hero.ended) "unused at end" else "remaining"}"
                        val used = formatQuotaData(hero.usedBytes)
                        val usedLabel = if (hero.ended) "Used at end" else "Used this cycle"
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuotaMetric(remaining, "Current cycle", Modifier.fillMaxWidth())
                                    QuotaMetric(used, usedLabel, Modifier.fillMaxWidth())
                                }
                            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                QuotaMetric(remaining, "Current cycle", Modifier.weight(1f))
                                QuotaMetric(used, usedLabel, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (empty || hero.reached || hero.ended || missing) {
                DataraActionButton(if (missing && !hero.ended) "About Usage Access" else if (empty) "Create a quota" else "Create another quota",
                    if (missing && !hero.ended) onAccess else onCreate)
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

/** M3-style warning banner: what is wrong with tracking and one action to fix it. */
@Composable
private fun TrackingBanner(issue: TrackingIssue, onFix: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(DataraIcons.Shield, null, modifier = Modifier.size(20.dp))
                Text(issue.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            Text(issue.body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFix, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                Text("Fix tracking")
            }
        }
    }
}
