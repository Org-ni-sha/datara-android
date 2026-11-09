package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.theme.*
import com.capstone.datara.ui.components.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun formatQuotaData(bytes: Long): String {
    // A value that would round to "1,000 MB" (e.g. 999,999,999 B) reads as 1 GB instead
    val unit = if (bytes >= QuotaUnit.GB.bytes || Math.round(bytes / 10_000.0) >= 100_000) QuotaUnit.GB else QuotaUnit.MB
    return "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(bytes.toDouble() / unit.bytes)} ${unit.name}"
}

internal fun formatCycleDate(millis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Rounded to minutes, e.g. "1 h 20 min". */
internal fun formatDuration(millis: Long): String {
    val minutes = (millis + 30_000) / 60_000
    return when {
        minutes < 1 -> "less than a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Sample cycles (History and Notifications previews). */
@Composable
fun QuotaDetailsScreen(state: HomeUiState, onBack: () -> Unit, onEndCycle: () -> Unit,
    onCreateQuota: () -> Unit, modifier: Modifier = Modifier, readOnly: Boolean = false) {
    QuotaDetailsScreen(state.toDetailsModel(), onBack, onEndCycle, onCreateQuota, modifier, readOnly)
}

@Composable
fun QuotaDetailsScreen(state: QuotaDetailsModel, onBack: () -> Unit, onEndCycle: () -> Unit,
    onCreateQuota: () -> Unit, modifier: Modifier = Modifier, readOnly: Boolean = false,
    onTrackingSetup: (() -> Unit)? = null, actionError: String? = null, readOnlyNote: String? = null) {
    QuotaTheme { QuotaDetailsContent(state, onBack, onEndCycle, onCreateQuota, modifier, readOnly, onTrackingSetup, actionError, readOnlyNote) }
}

internal fun detailsStatus(state: QuotaDetailsModel): String {
    val scenario = state.sampleScenario
    return when {
        state.isLoading -> "Loading"
        !state.hasQuota -> "No active quota"
        state.isReached && state.isEnded && scenario == null -> "Quota reached · Cycle ended"
        state.isReached -> "Quota reached"
        state.isEnded -> "Cycle ended"
        scenario == HomeSampleState.MISSING_PERMISSION -> "Access needed"
        scenario == HomeSampleState.COLLECTING -> "Collecting usage"
        scenario != null -> "Tracking · sample"
        state.measurement?.measuredThroughMillis == null -> "Waiting for usage readings"
        else -> "Tracking"
    }
}

private fun detailsTone(state: QuotaDetailsModel): StatusTone = when {
    state.sampleScenario == HomeSampleState.MISSING_PERMISSION -> StatusTone.PROBLEM
    state.isReached || state.isEnded || !state.hasQuota -> StatusTone.NEUTRAL
    else -> StatusTone.GOOD
}

private fun forecastTitle(state: QuotaDetailsModel): String = when {
    state.isEnded -> "Forecast stopped"
    state.isReached -> "Your quota has been reached"
    state.sampleScenario == HomeSampleState.MISSING_PERMISSION -> "Waiting for Usage Access"
    state.sampleScenario == HomeSampleState.COLLECTING -> "Getting to know your usage"
    state.sampleScenario != null -> "About 12 hours"
    state.forecast != null -> state.forecast.title
    else -> "Forecast not available yet"
}

private fun forecastBody(state: QuotaDetailsModel): String = when {
    state.isEnded -> "This cycle has ended. Start another quota when you’re ready."
    state.isReached -> state.reachedAtMillis?.let { "Reached around ${formatCycleDate(it)}. Reaching a quota does not block your internet." }
        ?: "Reaching a quota does not block your internet."
    state.sampleScenario == HomeSampleState.MISSING_PERMISSION -> "Usage is unavailable until access is restored. No current balance is assumed."
    state.sampleScenario == HomeSampleState.COLLECTING -> "A forecast will appear once there’s enough usage."
    state.sampleScenario != null -> "Illustrative estimate based on sample usage, not a live prediction."
    state.forecast != null -> state.forecast.body
    else -> "The forecast is calculated on this device from your measured usage, once an hour."
}

@Composable
private fun QuotaDetailsContent(
    state: QuotaDetailsModel,
    onBack: () -> Unit,
    onEndCycle: () -> Unit,
    onCreateQuota: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onTrackingSetup: (() -> Unit)? = null,
    actionError: String? = null,
    readOnlyNote: String? = null
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val empty = !state.hasQuota
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background, topBar = { DataraAppBar("Quota details", onBack) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
                return@Column
            }
            SectionTitle(if (empty) "No active quota" else state.name)
            StatusPill(detailsStatus(state), detailsTone(state))
            if (!empty) {
                QuotaProgressCard(state)
                state.measurement?.let { MeasurementCard(it, state.isEnded, onTrackingSetup) }
                DetailsCard(prominent = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TonalIcon(DataraIcons.Clock, positive = true)
                        Text("Your forecast", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(forecastTitle(state), style = MaterialTheme.typography.headlineMedium)
                    Text(forecastBody(state), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                }
                DetailsCard {
                    Text("Cycle information", style = MaterialTheme.typography.titleMedium)
                    DetailValue("Started", formatCycleDate(state.startedAtMillis))
                    if (!state.isSample) state.reachedAtMillis?.let { DetailValue("Reached", formatCycleDate(it)) }
                    state.endedAtMillis?.let { DetailValue("Ended", formatCycleDate(it)) }
                    DetailValue("Tracking scope", "This device · Mobile data")
                    Text("Uploads and downloads, including promo freebies. Wi-Fi is excluded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (readOnly) {
                Text(readOnlyNote ?: if (state.isSample) "Sample past cycle · Read-only details" else "Opened from an alert · Read-only details", style = MaterialTheme.typography.labelLarge)
            } else if (empty || state.isEnded || state.isReached) {
                if (!state.isLoading) DataraActionButton(if (empty) "Create a quota" else "Create another quota", onCreateQuota)
            } else {
                OutlinedButton(onClick = { confirmEnd = true }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("End cycle")
                }
            }
            if (!readOnly) Text(
                if (state.isSample) "Preview cycles are kept only for this session. Creating another quota replaces these sample details; saved history will come later."
                else "Cycles are saved on this phone. Past cycles are in History.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (confirmEnd && state.canEnd && !readOnly) {
        AlertDialog(onDismissRequest = { confirmEnd = false },
            title = { Text("End this cycle?") },
            text = { Text(if (state.isSample) "Stop this sample cycle? You can still review its details until you create another quota. Your internet stays on."
                else "Stop tracking this quota? You can still review its details until you create another quota. Your internet stays on.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndCycle() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("End cycle") }
            }, dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep tracking") } })
    }
}

/** When usage was last measured, and whether part of the cycle could not be measured. */
@Composable
private fun MeasurementCard(measurement: CycleMeasurement, ended: Boolean, onTrackingSetup: (() -> Unit)?) {
    DetailsCard {
        Text("Measured usage", style = MaterialTheme.typography.titleMedium)
        val through = measurement.measuredThroughMillis
        Text(when {
            through == null -> "No usage readings yet. Readings arrive every 10 minutes while tracking is on."
            ended -> "Measured up to ${formatCycleDate(through)}."
            else -> "Measured up to ${formatCycleDate(through)}. Updates every 10 minutes while tracking is on."
        }, style = MaterialTheme.typography.bodyMedium)
        if (measurement.isIncomplete) {
            Text(if (measurement.unmeasuredMillis > 0) "Some usage may be missing: tracking was off for about ${formatDuration(measurement.unmeasuredMillis)} of this cycle."
                else "Some readings could not be measured, so actual usage may be higher.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (onTrackingSetup != null && !ended && (through == null || measurement.isIncomplete)) {
            TextButton(onClick = onTrackingSetup) { Text("Check tracking") }
        }
    }
}

@Composable
private fun DetailsCard(prominent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = if (prominent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(name = "Details", widthDp = 360, heightDp = 800)
@Preview(name = "Details large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Details landscape", widthDp = 800, heightDp = 360)
@Composable
private fun DetailsPreview() { QuotaTheme { QuotaDetailsScreen(HomeUiState(HomeSampleState.FORECAST_AVAILABLE), {}, {}, {}) } }

@Preview(name = "Ended cycle", widthDp = 360, heightDp = 800)
@Composable
private fun EndedPreview() { QuotaTheme { QuotaDetailsScreen(HomeUiState(HomeSampleState.FORECAST_AVAILABLE, endedAtMillis = System.currentTimeMillis()), {}, {}, {}) } }

@Preview(name = "Measured cycle", widthDp = 360, heightDp = 800)
@Composable
private fun MeasuredPreview() {
    val start = 1_793_000_000_000L
    QuotaTheme {
        QuotaDetailsScreen(QuotaDetailsModel(hasQuota = true, name = "Everyday data", quotaBytes = 5_000_000_000, usedBytes = 1_250_000_000,
            remainingBytes = 3_750_000_000, progress = 0.25f, startedAtMillis = start,
            measurement = CycleMeasurement(start + 7_200_000, 1_200_000, 0)), {}, {}, {}, onTrackingSetup = {})
    }
}
