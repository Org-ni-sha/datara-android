package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.history.*
import com.capstone.datara.ui.quota.formatQuotaData
import java.time.LocalDate

/** Independent History fixture, never presented as readings from the current quota. */
@Composable
internal fun HomeUsageTrend() {
    val today = remember { LocalDate.now() }
    val snapshot = remember(today) { historySnapshot(HistoryWindow.WEEK, historyCycles(today).first(), today) }
    val line = MaterialTheme.colorScheme.tertiary
    val bar = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Text("${formatQuotaData(snapshot.totalBytes)} total · Sample history", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val description = snapshot.points.joinToString("; ") { "${it.label}: ${formatQuotaData(it.bytes)}" }
            Text("Daily mobile usage · MB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(104.dp).semantics { contentDescription = "Sample mobile usage. $description" }) {
                val inset = 4.dp.toPx()
                val height = size.height - inset * 2
                val width = size.width - inset * 2
                val maximum = snapshot.points.maxOf { it.bytes }.toFloat().coerceAtLeast(1f)
                drawLine(grid, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset))
                val slot = width / snapshot.points.size
                snapshot.points.forEachIndexed { i, point ->
                    val x = inset + slot * i + slot * 0.14f
                    val y = inset + height * (1 - point.bytes / maximum)
                    drawRoundRect(if (i == snapshot.points.lastIndex) line else bar,
                        topLeft = Offset(x, y), size = Size(slot * 0.72f, size.height - inset - y),
                        cornerRadius = CornerRadius(5.dp.toPx()))
                }
            }
            Text("Latest: ${formatQuotaData(snapshot.points.last().bytes)}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(snapshot.points.first().label, style = MaterialTheme.typography.labelSmall)
                Text(snapshot.points.last().label, style = MaterialTheme.typography.labelSmall)
            }
            Text("Independent example; not readings from your current quota. Full chart in History.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
