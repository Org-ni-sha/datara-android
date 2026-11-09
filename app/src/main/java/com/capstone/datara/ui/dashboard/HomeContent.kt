package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.capstone.datara.data.cycle.CycleStatus
import androidx.compose.ui.text.style.TextAlign

@Composable
internal fun HomeContent(state: HomeUiState, onCreateQuota: () -> Unit,
    onSelectScenario: (HomeSampleState) -> Unit, onSignOut: () -> Unit,
    modifier: Modifier = Modifier, onViewQuota: () -> Unit = {}, onHistory: () -> Unit = {},
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, onSettings: () -> Unit = {},
    onTrackingSetup: () -> Unit = {}, live: QuotaDetailsModel? = null, liveLoading: Boolean = false,
    trackingIssue: TrackingIssue? = null, week: HomeWeek? = null, onShowRealData: () -> Unit = {},
    greeting: String = "Hello", insights: List<HomeInsight> = emptyList(), onSampleEnded: () -> Unit = {},
    onRefresh: () -> Unit = {}) {
    var showInfo by remember { mutableStateOf(false) }
    val hero = HeroContent.select(state, live, liveLoading, trackingIssue)
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar(greeting, showLogo = true,
            subtitle = homeSubtitle(hero.name.takeIf { !hero.empty && !hero.loading && !hero.ended && !hero.reached }), actions = {
            NotificationBell(unreadCount, onNotifications)
            // Review tool for the defense screens; removed before the freeze
            PreviewMenu(listOf("Show my real quota" to onShowRealData) +
                HomeSampleState.entries.map { option -> "Sample: ${option.label}" to { onSelectScenario(option) } } +
                listOf("Sample: Cycle ended" to onSampleEnded))
        }) }, bottomBar = { QuotaBottomBar(false, {}, onHistory, onSettings) }) { insets ->
        // Pull to re-read usage and tracking status, as on History; the spinner is brief because Room reads are fast
        var refreshing by remember { mutableStateOf(false) }
        LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(700); refreshing = false } }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; onRefresh() }, modifier = Modifier.fillMaxSize().padding(insets)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.previewing) {
                StatusPill("Sample screen · Preview options", StatusTone.NEUTRAL)
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
                    // Real measured data only, so not beside a sample hero
                    if (!state.previewing) HomeInsightsPanel(insights)
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
    }
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("What counts?") },
        text = { Text("Mobile data on this phone counts, including promo freebies. Wi-Fi doesn’t. Your quota isn’t your carrier balance, and DATAra never blocks your internet.") },
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
    val loading: Boolean = false,
    /** Collecting: measured and needed hours of the last 24. */
    val observedHours: Int? = null,
    val neededHours: Int? = null,
    /** The PET's date ("Nov 27"); null without a PET. */
    val reachDate: String? = null
) {
    val collecting: Boolean get() = !loading && !empty && !missing && !ended && !reached && (hideUsage || observedHours != null)
    val tone: QuotaTone get() = quotaStatusTone(progress,
        when { ended -> CycleStatus.ENDED; reached -> CycleStatus.REACHED; else -> CycleStatus.ACTIVE })
    val percentUsed: Int get() = (progress.coerceAtLeast(0f) * 100).toInt()
    private val beyond: Boolean get() = headline == "Beyond 30 days"

    /** The ring's big centre value: hours measured, the PET ("19 days") or the share used. */
    val ringValue: String get() = when {
        missing -> "—"
        collecting -> if (observedHours != null && neededHours != null) "$observedHours of $neededHours" else "…"
        reached || ended -> "$percentUsed%"
        beyond -> "30+ days"
        headline.startsWith("About ") -> headline.removePrefix("About ")
        else -> "$percentUsed%"
    }
    val ringCaption: String get() = when {
        missing -> "Usage unavailable"
        collecting -> "hours measured"
        ended -> "used at end"
        reached -> "used"
        beyond -> "not within 30 days"
        reachDate != null -> "around $reachDate"
        headline.startsWith("About ") -> "to reach your quota"
        else -> "used"
    }

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

        private val REACH_FORMAT = java.time.format.DateTimeFormatter.ofPattern("MMM d")
        private fun reachDate(millis: Long) = REACH_FORMAT.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

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
                remainingBytes = state.remainingBytes, progress = state.progress, isLive = false,
                observedHours = if (collecting) 4 else null, neededHours = if (collecting) 6 else null,
                reachDate = if (!empty && !reached && !missing && !collecting && !state.isEnded)
                    reachDate(System.currentTimeMillis() + 12 * 3_600_000L) else null
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
                issue = issue.takeIf { !details.isEnded && !details.isReached },
                observedHours = forecast?.observedHours, neededHours = forecast?.neededHours,
                reachDate = forecast?.reachAtMillis?.let(::reachDate)
            )
        }
    }
}

/** The navy forecast hero: a ring of the quota used with the PET (or the state) in its centre. */
@Composable
private fun ForecastHero(hero: HeroContent, onCreate: () -> Unit, onAccess: () -> Unit, onViewQuota: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    val soft = onHero.copy(alpha = 0.82f)
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primary, contentColor = onHero)) {
        if (hero.loading) {
            Column(Modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = scheme.inversePrimary, trackColor = onHero.copy(alpha = 0.16f))
                Text(hero.headline, style = MaterialTheme.typography.bodyLarge, color = soft)
            }
            return@Card
        }
        Column(Modifier.fillMaxWidth().animateContentSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            hero.issue?.let { TrackingBanner(it, onAccess) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hero.label, style = MaterialTheme.typography.labelLarge, color = soft, modifier = Modifier.weight(1f))
                if (!hero.empty && !hero.missing && !hero.collecting) QuotaStatusChip(hero.tone)
            }
            if (hero.empty) {
                Text(hero.headline, style = MaterialTheme.typography.headlineLarge)
                Text(hero.body, style = MaterialTheme.typography.bodyMedium, color = soft)
                HeroPrimaryButton("Create a quota", onCreate)
                return@Column
            }
            val accent = if (hero.missing) scheme.inversePrimary.copy(alpha = 0f) else hero.tone.onPrimaryAccent()
            val below = ringTextBelow()
            val ringText: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CountUpText(hero.ringValue, style = MaterialTheme.typography.displaySmall, color = onHero, textAlign = TextAlign.Center)
                    Text(hero.ringCaption, style = MaterialTheme.typography.labelLarge, color = soft, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuotaRing(progress = when { hero.collecting -> null; hero.missing -> 0f; else -> hero.progress },
                    color = if (hero.collecting) scheme.inversePrimary else accent, trackColor = onHero.copy(alpha = 0.16f),
                    description = ringDescription(hero), size = if (below) 120.dp else 216.dp, stroke = if (below) 10.dp else 14.dp) {
                    if (!below) ringText()
                }
                if (below) ringText()
            }
            Text(if (hero.collecting) "Learning your usage… ${hero.body}" else hero.body,
                style = MaterialTheme.typography.bodyMedium, color = soft)
            if (!hero.missing && !hero.hideUsage) {
                val remaining = formatQuotaData(hero.remainingBytes)
                val used = formatQuotaData(hero.usedBytes)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuotaMetric(remaining, if (hero.ended) "Unused at end" else "Left of ${formatQuotaData(hero.amountBytes)}", Modifier.fillMaxWidth())
                            QuotaMetric(used, if (hero.ended) "Used at end" else "Used this cycle", Modifier.fillMaxWidth())
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuotaMetric(remaining, if (hero.ended) "Unused at end" else "Left of ${formatQuotaData(hero.amountBytes)}", Modifier.weight(1f))
                        QuotaMetric(used, if (hero.ended) "Used at end" else "Used this cycle", Modifier.weight(1f))
                    }
                }
            }
            if (hero.reached || hero.ended || hero.missing) {
                HeroPrimaryButton(if (hero.missing && !hero.ended) "About Usage Access" else "Create another quota",
                    if (hero.missing && !hero.ended) onAccess else onCreate)
            }
            FilledTonalButton(onClick = onViewQuota, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = onHero.copy(alpha = 0.12f), contentColor = onHero)) {
                Text("View quota details"); Spacer(Modifier.width(12.dp)); Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun ringDescription(hero: HeroContent): String = when {
    hero.missing -> "Usage unavailable. Usage Access is needed."
    hero.collecting -> "Learning your usage. ${hero.ringValue} ${hero.ringCaption}."
    else -> "${hero.percentUsed} percent of quota used. ${hero.ringValue}, ${hero.ringCaption}. ${hero.tone.label}."
}

/** The hero's main action: mint on navy. */
@Composable
private fun HeroPrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inversePrimary,
            contentColor = MaterialTheme.colorScheme.primary)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun QuotaMetric(value: String, label: String, modifier: Modifier) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    Surface(modifier, color = onHero.copy(alpha = 0.10f), contentColor = onHero, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CountUpText(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = onHero.copy(alpha = 0.82f))
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
