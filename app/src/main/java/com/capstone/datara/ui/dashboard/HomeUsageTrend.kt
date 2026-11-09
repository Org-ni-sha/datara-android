package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.quota.formatQuotaData

/**
 * Daily mobile usage for the last 7 days from the NetworkStatsManager readings, aggregated by
 * History's rule so both screens agree. An unmeasured day stays blank. Null [week] is loading.
 */
@Composable
internal fun HomeUsageTrend(week: HomeWeek?, onHistory: () -> Unit) {
    // Same colours as History's 7-day chart, so one day reads the same on both screens
    val bar = MaterialTheme.colorScheme.secondary
    val today = MaterialTheme.colorScheme.tertiary
    val average = MaterialTheme.colorScheme.primary
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
                    val measured = week.days.mapNotNull { it.bytes }
                    // History's average (per measured hour × 24), the same figure as the Daily average card
                    val dailyAverage = week.averageDailyBytes.toDouble()
                    Text("${formatQuotaData(week.totalBytes)} measured · ${formatQuotaData(week.averageDailyBytes)} a day on average",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val description = week.days.joinToString("; ") { "${it.label}: ${it.bytes?.let(::formatQuotaData) ?: "not measured"}" }
                    Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = "Daily mobile usage. $description" }) {
                        val inset = 4.dp.toPx()
                        val base = size.height - inset
                        val height = size.height - inset * 2
                        val width = size.width - inset * 2
                        val maximum = maxOf((measured.maxOrNull() ?: 0).toDouble(), dailyAverage).toFloat().coerceAtLeast(1f)
                        drawLine(grid, Offset(inset, base), Offset(size.width - inset, base))
                        val slot = width / week.days.size
                        week.days.forEachIndexed { i, day ->
                            val bytes = day.bytes ?: return@forEachIndexed
                            val x = inset + slot * i + slot * 0.16f
                            val y = (inset + height * (1 - bytes / maximum)).coerceAtMost(base - 2.dp.toPx())
                            val color = if (i == week.days.lastIndex) today else bar
                            drawRoundRect(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.45f)), startY = y, endY = base),
                                topLeft = Offset(x, y), size = Size(slot * 0.68f, base - y),
                                cornerRadius = CornerRadius(6.dp.toPx()))
                        }
                        // Dashed daily-average line over the measured days, as on History
                        val averageY = inset + height * (1 - (dailyAverage / maximum).toFloat())
                        drawLine(average, Offset(inset, averageY), Offset(size.width - inset, averageY), strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    }
                    // One date under each bar; an unmeasured day keeps its label so the gap is clear
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        week.days.forEachIndexed { i, day ->
                            Text(day.label.substringAfter(' '), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == week.days.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrendKey(today, "Today")
                        TrendKey(average, "Daily average", dashed = true)
                    }
                }
            }
            TextButton(onClick = onHistory, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("See full history") }
        }
    }
}

@Composable
private fun TrendKey(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Box(Modifier.size(5.dp, 2.dp).background(color)) } }
        else Box(Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
