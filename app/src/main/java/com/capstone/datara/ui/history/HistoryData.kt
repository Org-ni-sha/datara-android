package com.capstone.datara.ui.history

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.domain.cycle.CycleMath
import com.capstone.datara.domain.cycle.CycleUsage
import com.capstone.datara.forecast.FeatureContract
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import com.capstone.datara.forecast.HourlyUsage
import com.capstone.datara.ui.quota.CycleMeasurement
import com.capstone.datara.ui.quota.QuotaDetailsModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

enum class HistoryWindow(val label: String) { DAY("24 hours"), WEEK("7 days"), CYCLE("Quota cycle") }

/** One chart bar: [bytes] is null when the period was not measured (a gap, never a zero). */
data class UsagePoint(val label: String, val bytes: Long?, val measuredHours: Int = if (bytes == null) 0 else 1, val totalHours: Int = 1) {
    val isMeasured: Boolean get() = bytes != null
    /** Some hours in the period were not measured, so [bytes] is a lower bound. */
    val isPartial: Boolean get() = bytes != null && measuredHours < totalHours
}

/** One clock hour of mobile usage; [bytes] null = not measured. */
data class HourBucket(val hourStart: Long, val bytes: Long?)

/** One local day; [bytes] sums the measured hours, null when none were measured. */
data class DayBucket(val date: LocalDate, val bytes: Long?, val measuredHours: Int, val totalHours: Int)

/** A chart window's totals, counting measured periods only. */
data class HistorySnapshot(val points: List<UsagePoint>, val dayCount: Int) {
    val measuredPoints get() = points.filter { it.isMeasured }
    val totalBytes: Long get() = measuredPoints.sumOf { it.bytes!! }
    val measuredHours: Int get() = points.sumOf { it.measuredHours }
    val totalHours: Int get() = points.sumOf { it.totalHours }
    val hasData: Boolean get() = measuredPoints.isNotEmpty()
    /** Average per day over the measured time, so unmeasured days don't drag it down. */
    val averageDailyBytes: Long get() = if (measuredHours == 0) 0 else (totalBytes * 24.0 / measuredHours).roundToLong()
    val peak: UsagePoint? get() = measuredPoints.maxByOrNull { it.bytes!! }
    val isPartial: Boolean get() = measuredHours < totalHours
}

/** The forecast made some time before a cycle reached its quota, against the actual time. */
data class PetComparison(val predictedAt: Long, val predictedReachAt: Long, val reachedAt: Long, val model: String) {
    /** Positive when the quota was reached later than predicted. */
    val errorMillis: Long get() = reachedAt - predictedReachAt
}

/** A cycle in History with its measured usage and, when reached, how the forecast did. */
data class CycleHistory(
    val cycle: TrackingCycle,
    val usage: CycleUsage,
    val days: List<DayBucket>,
    val pet: PetComparison?
) {
    val name: String get() = cycle.name ?: QuotaDetailsModel.DEFAULT_NAME
    val statusLabel: String get() = when {
        cycle.status == CycleStatus.REACHED -> "Quota reached"
        cycle.endedAt != null -> "Ended early"
        else -> "Active"
    }

    fun toDetails(): QuotaDetailsModel = QuotaDetailsModel(
        hasQuota = true,
        cycleId = cycle.cycleId,
        name = name,
        quotaBytes = cycle.quotaBytes,
        usedBytes = usage.usedBytes,
        remainingBytes = CycleMath.displayRemainingBytes(cycle.quotaBytes, usage.usedBytes),
        progress = (CycleMath.percentUsed(cycle.quotaBytes, usage.usedBytes) / 100).toFloat(),
        startedAtMillis = cycle.startAt,
        endedAtMillis = cycle.endedAt,
        reachedAtMillis = cycle.reachedAt,
        isReached = cycle.status == CycleStatus.REACHED,
        measurement = CycleMeasurement(usage.measuredThrough, usage.unmeasuredMillis, usage.lostReadings)
    )
}

/** Everything History shows, built from Room by [HistoryViewModel]. */
data class HistoryData(
    val day: List<HourBucket>,
    val week: List<DayBucket>,
    val cycles: List<CycleHistory>,
    /** Measured hours over the whole reading range; drives the "collecting" note. */
    val measuredHours: Int
) {
    val isEmpty: Boolean get() = measuredHours == 0 && cycles.isEmpty()
}

/**
 * Pure aggregation for History: no Android, no storage. Hourly bytes use the forecast's
 * [HourlyUsage.hourlyFromWindows] over the NetworkStatsManager readings, so History and the PET
 * agree hour for hour; an hour that rule leaves unobserved stays null.
 */
object HistoryAggregation {

    /** How long before the reach the PET comparison looks for a forecast. */
    const val PET_LEAD_MS = 24 * HOUR_MS

    /** Days shown in the 7-day window and the most days a cycle chart shows (its latest). */
    const val WEEK_DAYS = 7
    const val MAX_CYCLE_DAYS = 31

    /** Hour start to bytes, measured hours only. */
    fun measuredHours(readings: List<DurableUsageReading>): Map<Long, Long> =
        HourlyUsage.hourlyFromWindows(readings.map(HourlyUsage::window))
            .filterValues { !it.isNaN() }
            .mapValues { it.value.roundToLong() }

    /** Every complete hour in `[from, until)`, null where unmeasured. */
    fun hours(measured: Map<Long, Long>, from: Long, until: Long): List<HourBucket> {
        val out = ArrayList<HourBucket>()
        var h = FeatureContract.hourStart(from)
        while (h + HOUR_MS <= until) {
            out += HourBucket(h, measured[h])
            h += HOUR_MS
        }
        return out
    }

    /** Groups hours by local date, oldest first, one bucket per date from [first] to [last]. */
    fun days(hours: List<HourBucket>, first: LocalDate, last: LocalDate, zone: ZoneId): List<DayBucket> {
        val byDate = hours.groupBy { Instant.ofEpochMilli(it.hourStart).atZone(zone).toLocalDate() }
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { date ->
            val day = byDate[date].orEmpty()
            val measured = day.filter { it.bytes != null }
            DayBucket(date, if (measured.isEmpty()) null else measured.sumOf { it.bytes!! }, measured.size, day.size)
        }.toList()
    }

    /**
     * The forecast to compare with the actual reach: the latest one made at least [PET_LEAD_MS]
     * before it, else the earliest one in the cycle. Uses the model that was active then.
     */
    fun petComparison(cycle: TrackingCycle, forecasts: List<Forecast>): PetComparison? {
        val reachedAt = cycle.reachedAt ?: return null
        fun reachOf(f: Forecast) = if (f.activeModel == ActiveModel.LOCAL) f.localReachAt ?: f.globalReachAt else f.globalReachAt
        val candidates = forecasts.filter { it.predictedAt < reachedAt && reachOf(it) != null }.sortedBy { it.predictedAt }
        val chosen = candidates.lastOrNull { it.predictedAt <= reachedAt - PET_LEAD_MS } ?: candidates.firstOrNull() ?: return null
        return PetComparison(chosen.predictedAt, reachOf(chosen)!!, reachedAt, chosen.activeModel)
    }

    fun build(
        cycles: List<TrackingCycle>,
        readings: List<DurableUsageReading>,
        forecastsByCycle: Map<String, List<Forecast>>,
        now: Long,
        zone: ZoneId
    ): HistoryData {
        val measured = measuredHours(readings)
        val currentHour = FeatureContract.hourStart(now)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val weekStart = today.minusDays(WEEK_DAYS - 1L)
        val weekHours = hours(measured, weekStart.atStartOfDay(zone).toInstant().toEpochMilli(), currentHour)
        val cycleItems = cycles.sortedByDescending { it.startAt }.map { cycle ->
            val end = cycle.endedAt ?: now
            val usage = CycleMath.usage(readings, cycle.startAt, cycle.endedAt, cycle.quotaBytes)
            val lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
            val firstDay = maxOf(Instant.ofEpochMilli(cycle.startAt).atZone(zone).toLocalDate(), lastDay.minusDays(MAX_CYCLE_DAYS - 1L))
            val cycleHours = hours(measured, maxOf(cycle.startAt, firstDay.atStartOfDay(zone).toInstant().toEpochMilli()),
                minOf(FeatureContract.hourStart(end) + if (cycle.endedAt != null) HOUR_MS else 0, currentHour))
            CycleHistory(cycle, usage, days(cycleHours, firstDay, lastDay, zone),
                petComparison(cycle, forecastsByCycle[cycle.cycleId].orEmpty()))
        }
        return HistoryData(
            day = hours(measured, currentHour - 24 * HOUR_MS, currentHour),
            week = days(weekHours, weekStart, today, zone),
            cycles = cycleItems,
            measuredHours = measured.count { it.key < currentHour }
        )
    }

    fun snapshot(window: HistoryWindow, data: HistoryData, cycle: CycleHistory?, zone: ZoneId): HistorySnapshot = when (window) {
        HistoryWindow.DAY -> HistorySnapshot(data.day.map { hour ->
            UsagePoint("%02d:00".format(Instant.ofEpochMilli(hour.hourStart).atZone(zone).hour), hour.bytes)
        }, 1)
        HistoryWindow.WEEK -> daySnapshot(data.week)
        HistoryWindow.CYCLE -> daySnapshot(cycle?.days.orEmpty())
    }

    private fun daySnapshot(days: List<DayBucket>) = HistorySnapshot(days.map {
        UsagePoint(it.date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")), it.bytes, it.measuredHours, it.totalHours)
    }, days.size)
}
