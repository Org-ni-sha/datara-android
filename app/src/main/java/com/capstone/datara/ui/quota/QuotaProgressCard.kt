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
import com.capstone.datara.ui.components.*

@Composable
internal fun QuotaProgressCard(state: QuotaDetailsModel) {
    val missing = state.usageUnavailable
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.4f
                val tone = detailsQuotaTone(state)
                val gauge: @Composable () -> Unit = {
                    // Echoes the Home ring: same status colour, smaller
                    QuotaRing(progress = if (missing) 0f else state.progress, color = tone.accent(), trackColor = tone.colors().container,
                        description = if (missing) "Usage unavailable" else "${(state.progress * 100).roundToInt()} percent of quota used",
                        size = 124.dp, stroke = 11.dp, modifier = Modifier.width(124.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (missing) "—" else "${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
                            Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val remaining: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (state.isEnded) "Unused at end" else "Remaining quota", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (missing) "Unavailable" else formatQuotaData(state.remainingBytes ?: 0), style = MaterialTheme.typography.headlineLarge)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { gauge(); remaining() }
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    gauge(); Box(Modifier.weight(1f)) { remaining() }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val used = if (missing) "Unavailable" else formatQuotaData(state.usedBytes ?: 0)
            val usedLabel = if (state.isEnded) "Used at end" else "Used"
            // At large font sizes the two values stack instead of squeezing side by side
            if (largeFont) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Value(usedLabel, used, Modifier.fillMaxWidth())
                Value("Starting quota", formatQuotaData(state.quotaBytes), Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Value(usedLabel, used, Modifier.weight(1f))
                Value("Starting quota", formatQuotaData(state.quotaBytes), Modifier.weight(1f))
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
