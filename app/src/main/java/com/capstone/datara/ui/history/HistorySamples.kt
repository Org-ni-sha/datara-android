package com.capstone.datara.ui.history

import com.capstone.datara.ui.quota.HomeSampleState
import com.capstone.datara.ui.quota.HomeUiState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HistoryWindow(val label: String) { DAY("24 hours"), WEEK("7 days"), CYCLE("Quota cycle") }
data class UsagePoint(val label: String, val bytes: Long)
data class HistorySnapshot(val points: List<UsagePoint>, val days: Int) {
    val totalBytes get() = points.sumOf { it.bytes }
    val averageDailyBytes get() = totalBytes / days.coerceAtLeast(1)
    val peak get() = points.maxByOrNull { it.bytes }
}
data class SampleCycle(val id: String, val state: HomeUiState)

/** Fixed proportions and an injected date keep fixtures consistent across filters and tests. */
fun historyCycles(today: LocalDate = LocalDate.now()): List<SampleCycle> {
    val zone = ZoneId.systemDefault()
    fun time(daysAgo: Long) = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
    return listOf(
        SampleCycle("everyday", HomeUiState(HomeSampleState.FORECAST_AVAILABLE, 10_000_000_000, "Everyday data", time(7), time(0))),
        SampleCycle("commute", HomeUiState(HomeSampleState.QUOTA_REACHED, 5_000_000_000, "Commute quota", time(14), time(8))),
        SampleCycle("weekend", HomeUiState(HomeSampleState.FORECAST_AVAILABLE, 2_000_000_000, "Weekend data", time(18), time(15)))
    )
}

fun historySnapshot(window: HistoryWindow, cycle: SampleCycle, today: LocalDate): HistorySnapshot {
    val weights = listOf(10, 12, 18, 9, 16, 20, 15)
    val cycleTotal = cycle.state.usedBytes
    fun distribute(total: Long, parts: List<Int>): List<Long> {
        val sum = parts.sum()
        val values = parts.map { total / sum * it }.toMutableList()
        values[values.lastIndex] += total - values.sum()
        return values
    }
    if (window == HistoryWindow.DAY) {
        val hourly = distribute(600_000_000, listOf(1,1,1,1,1,2,3,5,8,6,4,5,6,4,5,6,7,10,14,12,9,6,3,2))
        return HistorySnapshot(hourly.mapIndexed { i, value -> UsagePoint("%02d:00".format(i), value) }, 1)
    }
    val total = if (window == HistoryWindow.WEEK) 4_000_000_000L else cycleTotal
    val days = if (window == HistoryWindow.WEEK) 7 else java.time.temporal.ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(cycle.state.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
        java.time.Instant.ofEpochMilli(cycle.state.endedAtMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()).toInt().coerceIn(1, 7)
    val values = distribute(total, weights.takeLast(days))
    val end = if (window == HistoryWindow.WEEK) today else java.time.Instant.ofEpochMilli(cycle.state.endedAtMillis!!)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    return HistorySnapshot(values.mapIndexed { i, value ->
        UsagePoint(end.minusDays((days-1-i).toLong()).format(DateTimeFormatter.ofPattern("MMM d")), value)
    }, days)
}
