package com.capstone.datara.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraSectionTitle
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary

data class DeveloperDiagnosticsUiState(
    val activeModelMode: String,
    val runtimeStatus: String,
    val inferenceLatency: String,
    val lastPrediction: String,
    val mae: String,
    val rmse: String,
    val rSquared: String,
    val tensorShape: String,
    val tensorDataType: String,
    val tensorFeatures: String,
    val isPreview: Boolean = false
)

@Composable
fun DeveloperDiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    state: DeveloperDiagnosticsUiState = unavailableDiagnosticsState()
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DataraDarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    DataraPageHeader(
                        eyebrow = stringResource(R.string.diagnostics_eyebrow),
                        title = stringResource(R.string.diagnostics_title),
                        subtitle = stringResource(R.string.diagnostics_subtitle),
                        onBack = onBack,
                        backContentDescription = stringResource(R.string.action_back),
                        trailingContent = {
                            DataraStatusBadge(
                                text = if (state.isPreview) {
                                    stringResource(R.string.status_design_preview)
                                } else {
                                    stringResource(R.string.diagnostics_hidden_badge)
                                },
                                color = DataraBlueSoft
                            )
                        }
                    )
                }
            }
            item {
                DataraNoticeCard(
                    title = stringResource(R.string.diagnostics_notice_title),
                    message = stringResource(R.string.diagnostics_notice_body),
                    accent = DataraBlueSoft,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
            item {
                DiagnosticsSection(
                    title = stringResource(R.string.diagnostics_runtime_section),
                    values = listOf(
                        stringResource(R.string.diagnostics_active_model) to state.activeModelMode,
                        stringResource(R.string.diagnostics_runtime_status) to state.runtimeStatus,
                        stringResource(R.string.diagnostics_latency) to state.inferenceLatency,
                        stringResource(R.string.diagnostics_last_prediction) to state.lastPrediction
                    )
                )
            }
            item {
                DiagnosticsSection(
                    title = stringResource(R.string.diagnostics_accuracy_section),
                    values = listOf(
                        stringResource(R.string.diagnostics_mae) to state.mae,
                        stringResource(R.string.diagnostics_rmse) to state.rmse,
                        stringResource(R.string.diagnostics_r_squared) to state.rSquared
                    )
                )
            }
            item {
                DiagnosticsSection(
                    title = stringResource(R.string.diagnostics_input_section),
                    values = listOf(
                        stringResource(R.string.diagnostics_tensor_shape) to state.tensorShape,
                        stringResource(R.string.diagnostics_tensor_dtype) to state.tensorDataType,
                        stringResource(R.string.diagnostics_tensor_features) to state.tensorFeatures
                    )
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(title: String, values: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DataraSectionTitle(title)
        DataraSurfaceCard {
            values.forEachIndexed { index, (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.labelLarge, color = DataraTextPrimary)
                }
                if (index != values.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun unavailableDiagnosticsState(): DeveloperDiagnosticsUiState {
    val unavailable = stringResource(R.string.diagnostics_value_unavailable)
    return DeveloperDiagnosticsUiState(
        activeModelMode = unavailable,
        runtimeStatus = unavailable,
        inferenceLatency = unavailable,
        lastPrediction = unavailable,
        mae = unavailable,
        rmse = unavailable,
        rSquared = unavailable,
        tensorShape = unavailable,
        tensorDataType = unavailable,
        tensorFeatures = unavailable
    )
}

@Composable
private fun previewDiagnosticsState(): DeveloperDiagnosticsUiState = DeveloperDiagnosticsUiState(
    activeModelMode = stringResource(R.string.diagnostics_preview_model),
    runtimeStatus = stringResource(R.string.diagnostics_preview_ready),
    inferenceLatency = stringResource(R.string.diagnostics_preview_latency),
    lastPrediction = stringResource(R.string.diagnostics_preview_time),
    mae = stringResource(R.string.diagnostics_preview_mae),
    rmse = stringResource(R.string.diagnostics_preview_rmse),
    rSquared = stringResource(R.string.diagnostics_preview_r_squared),
    tensorShape = stringResource(R.string.diagnostics_preview_shape),
    tensorDataType = stringResource(R.string.diagnostics_preview_dtype),
    tensorFeatures = stringResource(R.string.diagnostics_preview_features),
    isPreview = true
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun DiagnosticsUnavailablePreview() {
    DATAraTheme { DeveloperDiagnosticsScreen(onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun DiagnosticsReadyPreview() {
    DATAraTheme {
        DeveloperDiagnosticsScreen(onBack = {}, state = previewDiagnosticsState())
    }
}

