package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.quota.formatQuotaData

/**
 * Daily mobile usage for the last 7 days from the NetworkStatsManager readings, aggregated by
 * History's rule so both screens agree. An unmeasured day stays blank. Null [week] is loading.
 */
@Composable
internal fun HomeUsageTrend(week: HomeWeek?, onHistory: () -> Unit) {
    val today = MaterialTheme.colorScheme.secondary
    val bar = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            when {
                week == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading usage" })
                }
                !week.hasReadings -> Text("No measured usage yet. Daily totals appear here once tracking has run.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text("${formatQuotaData(week.totalBytes)} measured", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val description = week.days.joinToString("; ") { "${it.label}: ${it.bytes?.let(::formatQuotaData) ?: "not measured"}" }
                    Canvas(Modifier.fillMaxWidth().height(104.dp).semantics { contentDescription = "Daily mobile usage. $description" }) {
                        val inset = 4.dp.toPx()
                        val height = size.height - inset * 2
                        val width = size.width - inset * 2
                        val maximum = week.days.maxOf { it.bytes ?: 0 }.toFloat().coerceAtLeast(1f)
                        drawLine(grid, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset))
                        val slot = width / week.days.size
                        week.days.forEachIndexed { i, day ->
                            val bytes = day.bytes ?: return@forEachIndexed
                            val x = inset + slot * i + slot * 0.14f
                            val y = (inset + height * (1 - bytes / maximum)).coerceAtMost(size.height - inset - 2.dp.toPx())
                            drawRoundRect(if (i == week.days.lastIndex) today else bar,
                                topLeft = Offset(x, y), size = Size(slot * 0.72f, size.height - inset - y),
                                cornerRadius = CornerRadius(5.dp.toPx()))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(week.days.first().label, style = MaterialTheme.typography.labelSmall)
                        Text(week.days.last().label, style = MaterialTheme.typography.labelSmall)
                    }
                    week.latest?.let {
                        Text("${it.label}: ${formatQuotaData(it.bytes ?: 0)}", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            TextButton(onClick = onHistory, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("See full history") }
        }
    }
}
