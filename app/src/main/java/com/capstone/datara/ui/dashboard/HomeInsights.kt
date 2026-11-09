package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.history.HistoryAggregation
import com.capstone.datara.ui.history.HistoryData
import com.capstone.datara.ui.history.HistoryWindow
import com.capstone.datara.ui.quota.formatQuotaData
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/** One Home insight card. [value] null = not enough measured data yet. */
data class HomeInsight(val kind: Kind, val value: String?, val label: String) {
    enum class Kind(val title: String) {
        TODAY("Today"), DAILY_AVERAGE("Daily average"), PEAK_HOUR("Peak hour"), FORECAST_VS_ACTUAL("Forecast vs. actual")
    }

    companion object {
        const val NOT_ENOUGH = "Not enough data yet"
        private const val HOUR = 3_600_000L

        /**
         * Insights from the same aggregation History shows: today's day bucket, the 7-day
         * window's average per measured day, the busiest hour of the last 24 hours, and the PET
         * comparison of the latest reached cycle (left out when no cycle has reached its quota).
         */
        fun from(data: HistoryData, zone: ZoneId): List<HomeInsight> {
            val today = data.week.lastOrNull()?.bytes
            val week = HistoryAggregation.snapshot(HistoryWindow.WEEK, data, null, zone)
            val peak = data.day.filter { it.bytes != null }.maxByOrNull { it.bytes!! }
            val pet = data.cycles.firstOrNull { it.pet != null }?.pet
            return listOfNotNull(
                HomeInsight(Kind.TODAY, today?.let(::formatQuotaData), if (today == null) NOT_ENOUGH else "Mobile data so far"),
                HomeInsight(Kind.DAILY_AVERAGE, if (week.hasData) formatQuotaData(week.averageDailyBytes) else null,
                    if (week.hasData) "Per day, last 7 days" else NOT_ENOUGH),
                HomeInsight(Kind.PEAK_HOUR, peak?.let { "%02d:00".format(Instant.ofEpochMilli(it.hourStart).atZone(zone).hour) },
                    peak?.let { "${formatQuotaData(it.bytes!!)} · last 24 hours" } ?: NOT_ENOUGH),
                pet?.let {
                    HomeInsight(Kind.FORECAST_VS_ACTUAL, offset(it.errorMillis), when {
                        abs(it.errorMillis) < HOUR -> "Last quota reached on time"
                        it.errorMillis > 0 -> "Later than forecast, last quota"
                        else -> "Sooner than forecast, last quota"
                    })
                }
            )
        }

        /** The forecast's distance from the actual reach: "Within 1 h", "5 h" or "2 days". */
        fun offset(errorMillis: Long): String {
            val hours = (abs(errorMillis).toDouble() / HOUR).roundToLong()
            return when {
                hours < 1 -> "Within 1 h"
                hours < 48 -> "$hours h"
                else -> "${(hours / 24.0).roundToLong()} days"
            }
        }
    }
}

private fun HomeInsight.Kind.icon(): ImageVector = when (this) {
    HomeInsight.Kind.TODAY -> DataraIcons.Data
    HomeInsight.Kind.DAILY_AVERAGE -> DataraIcons.History
    HomeInsight.Kind.PEAK_HOUR -> DataraIcons.Clock
    HomeInsight.Kind.FORECAST_VS_ACTUAL -> DataraIcons.CheckCircle
}

/**
 * Home's insights: Today as one wide card, then the other insights as a row of small cards, so
 * every insight is visible without swiping. At large font sizes the small cards stack instead.
 * Nothing is drawn until some usage has been measured.
 */
@Composable
fun HomeInsightsPanel(insights: List<HomeInsight>, modifier: Modifier = Modifier) {
    if (insights.none { it.value != null }) return
    val large = LocalDensity.current.fontScale > 1.3f
    val featured = insights.firstOrNull { it.kind == HomeInsight.Kind.TODAY }
    val rest = insights.filter { it !== featured }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        featured?.let { WideInsightCard(it) }
        if (rest.isNotEmpty()) {
            if (large) {
                rest.forEach { WideInsightCard(it) }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rest.forEach { SmallInsightCard(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun InsightIcon(kind: HomeInsight.Kind) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Icon(kind.icon(), null, Modifier.padding(8.dp).size(18.dp))
    }
}

@Composable
private fun WideInsightCard(insight: HomeInsight, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InsightIcon(insight.kind)
            Column(Modifier.weight(1f)) {
                Text(insight.kind.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(insight.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(insight.value ?: "—", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

@Composable
private fun SmallInsightCard(insight: HomeInsight, modifier: Modifier = Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InsightIcon(insight.kind)
            Text(insight.kind.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(insight.value ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(insight.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
