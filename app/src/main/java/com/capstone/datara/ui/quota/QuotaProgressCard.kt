package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun QuotaProgressCard(state: HomeUiState) {
    val missing = state.scenario == HomeSampleState.MISSING_PERMISSION
    val progress by animateFloatAsState(if (missing) 0f else state.progress.coerceIn(0f, 1f), tween(350), label = "Quota gauge")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.4f
                val gauge: @Composable () -> Unit = {
                    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().semantics {
                            contentDescription = if (missing) "Usage unavailable" else "${(state.progress * 100).roundToInt()} percent of quota used"
                        }, strokeWidth = 9.dp, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (missing) "—" else "${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
                            Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val remaining: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (state.isEnded) "Unused at end" else "Remaining quota", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (missing) "Unavailable" else formatQuotaData(state.remainingBytes), style = MaterialTheme.typography.headlineLarge)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { gauge(); remaining() }
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    gauge(); Box(Modifier.weight(1f)) { remaining() }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Value(if (state.isEnded) "Used at end" else "Used", if (missing) "Unavailable" else formatQuotaData(state.usedBytes), Modifier.weight(1f))
                Value("Starting quota", formatQuotaData(state.amountBytes), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Value(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
