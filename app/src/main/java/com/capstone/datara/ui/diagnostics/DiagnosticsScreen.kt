package com.capstone.datara.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.ui.components.DataraAppBar
import com.capstone.datara.ui.theme.QuotaTheme

/** The hidden developer screen (D9): opened by tapping the version in Settings 7 times. */
@Composable
fun DiagnosticsRoute(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    DiagnosticsScreen(state, onBack, viewModel::refresh)
}

@Composable
fun DiagnosticsScreen(state: DiagnosticsUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    BackHandler(onBack = onBack)
    QuotaTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = { DataraAppBar("ML diagnostics", onBack = onBack, actions = {
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Developer view · read-only · real on-device data", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    return@Column
                }
                ActiveModelCard(state)
                state.sections.forEach { DiagnosticsCard(it) }
                if (state.table.isNotEmpty()) ForecastTable(state.table)
                Text("Updated ${state.updatedAt} · refreshes every 15 s", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActiveModelCard(state: DiagnosticsUiState) {
    val local = state.activeModel == ActiveModel.LOCAL
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(
        containerColor = if (local) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Active model", style = MaterialTheme.typography.labelLarge)
            Text(state.activeModel.uppercase(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(state.reason, style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { state.learnedProgress }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Text(state.learnedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticsCard(section: DiagnosticsSection) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            section.rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Text(row.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        color = if (row.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                }
            }
        }
    }
}

@Composable
private fun ForecastTable(rows: List<ForecastTableRow>) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Last ${rows.size} forecasts (MB)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TableLine("Hour", "Model", "Global", "Local", "Left", header = true)
                rows.forEach { TableLine(it.hour, it.active, it.globalMb, it.localMb, it.remainingMb) }
            }
        }
    }
}

@Composable
private fun TableLine(vararg cells: String, header: Boolean = false) {
    val widths = listOf(110, 64, 72, 72, 88)
    Row {
        cells.forEachIndexed { i, c ->
            Text(c, Modifier.width(widths[i].dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, textAlign = if (i >= 2) TextAlign.End else TextAlign.Start)
        }
    }
}

@Preview(widthDp = 360, heightDp = 1400)
@Composable
private fun DiagnosticsPreview() {
    DiagnosticsScreen(DiagnosticsUiState(loading = false, reason = "Global: the local layer has learned 40 of 168 hours.",
        learnedProgress = 0.24f, learnedLabel = "40 of 168 hours learned",
        sections = listOf(DiagnosticsSection("Telemetry health", listOf(DiagnosticsRow("Tracking service", "Running")))),
        table = listOf(ForecastTableRow("Nov 7 14:00", "GLOBAL", "12.30", "—", "820.00"))), {}, {})
}
