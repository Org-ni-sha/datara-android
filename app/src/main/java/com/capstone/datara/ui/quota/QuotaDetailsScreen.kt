package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.theme.*
import com.capstone.datara.ui.components.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun formatQuotaData(bytes: Long): String {
    val unit = if (bytes >= QuotaUnit.GB.bytes) QuotaUnit.GB else QuotaUnit.MB
    return "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(bytes.toDouble() / unit.bytes)} ${unit.name}"
}

private fun formatCycleDate(millis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun QuotaDetailsScreen(state: HomeUiState, onBack: () -> Unit, onEndCycle: () -> Unit,
    onCreateQuota: () -> Unit, modifier: Modifier = Modifier, readOnly: Boolean = false) {
    QuotaTheme { QuotaDetailsContent(state, onBack, onEndCycle, onCreateQuota, modifier, readOnly) }
}

@Composable
private fun QuotaDetailsContent(
    state: HomeUiState,
    onBack: () -> Unit,
    onEndCycle: () -> Unit,
    onCreateQuota: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val missing = state.scenario == HomeSampleState.MISSING_PERMISSION
    val empty = state.scenario == HomeSampleState.NO_QUOTA
    val reached = state.scenario == HomeSampleState.QUOTA_REACHED
    val status = when {
        empty -> "No active quota"
        reached -> "Quota reached"
        state.isEnded -> "Cycle ended"
        missing -> "Access needed"
        state.scenario == HomeSampleState.COLLECTING -> "Collecting usage"
        else -> "Tracking · sample"
    }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background, topBar = { DataraAppBar("Quota details", onBack) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle(if (empty) "No active quota" else state.name)
            SampleLabel(status)
            if (!empty) {
                QuotaProgressCard(state)
                DetailsCard(prominent = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TonalIcon(DataraIcons.Clock, positive = true)
                        Text("Your forecast", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(when {
                        state.isEnded -> "Forecast stopped"
                        reached -> "Your quota has been reached"
                        missing -> "Waiting for Usage Access"
                        state.scenario == HomeSampleState.COLLECTING -> "Getting to know your usage"
                        else -> "About 12 hours"
                    }, style = MaterialTheme.typography.headlineMedium)
                    Text(when {
                        state.isEnded -> "This cycle has ended. Start another quota when you’re ready."
                        reached -> "Reaching a quota does not block your internet."
                        missing -> "Usage is unavailable until access is restored. No current balance is assumed."
                        state.scenario == HomeSampleState.COLLECTING -> "A forecast will appear once there’s enough usage."
                        else -> "Illustrative estimate based on sample usage, not a live prediction."
                    }, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                }
                DetailsCard {
                    Text("Cycle information", style = MaterialTheme.typography.titleMedium)
                    DetailValue("Started", formatCycleDate(state.startedAtMillis))
                    state.endedAtMillis?.let { DetailValue("Ended", formatCycleDate(it)) }
                    DetailValue("Tracking scope", "This device · Mobile data")
                    Text("Uploads and downloads, including promo freebies. Wi-Fi is excluded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (readOnly) {
                Text("Sample past cycle · Read-only details", style = MaterialTheme.typography.labelLarge)
            } else if (empty || state.isEnded || reached) {
                DataraActionButton(if (empty) "Create a quota" else "Create another quota", onCreateQuota)
            } else {
                OutlinedButton(onClick = { confirmEnd = true }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("End cycle")
                }
            }
            if (!readOnly) Text("Preview cycles are kept only for this session. Creating another quota replaces these sample details; saved history will come later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (confirmEnd && state.canEnd && !readOnly) {
        AlertDialog(onDismissRequest = { confirmEnd = false },
            title = { Text("End this cycle?") },
            text = { Text("Stop this sample cycle? You can still review its details until you create another quota. Your internet stays on.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndCycle() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("End cycle") }
            }, dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep tracking") } })
    }
}

@Composable
private fun DetailsCard(prominent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = if (prominent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
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
