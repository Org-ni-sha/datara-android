package com.capstone.datara.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.domain.cycle.CycleMath
import com.capstone.datara.telemetry.MeasurementStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Home's live "Today": the 10-minute NetworkStatsManager readings since local midnight, counted
 * like "used this cycle" ([CycleMath.usage]), plus the 30-second TrafficStats samples taken after
 * the latest reading. Each new 10-minute reading replaces the samples it covers, so every 10
 * minutes the number is corrected to Android's own count.
 *
 * Display only: History's charts, the forecast and the alerts keep using the readings alone
 * (complete hours), so the live part never reaches the model.
 */
object LiveToday {

    /** Today's mobile bytes, or null when nothing has been measured since [dayStart]. */
    fun bytes(readings: List<DurableUsageReading>, samples: List<UsageSample>, dayStart: Long): Long? {
        val todays = readings.filter { it.until > dayStart }
        val usage = if (todays.isEmpty()) null else CycleMath.usage(todays, dayStart, null, Long.MAX_VALUE)
        val cutoff = maxOf(usage?.measuredThrough ?: dayStart, dayStart)

        var live = 0L
        var anySample = false
        for (s in samples) {
            if (s.intervalEnd <= cutoff || s.intervalEnd <= s.intervalStart || s.status == MeasurementStatus.CLOCK_ERROR) continue
            anySample = true
            val total = s.bytesRx + s.bytesTx
            // A sample that straddles the cutoff counts only for its part after it
            live += if (s.intervalStart >= cutoff) total
            else total * (s.intervalEnd - cutoff) / (s.intervalEnd - s.intervalStart)
        }
        if (usage == null && !anySample) return null
        return (usage?.usedBytes ?: 0L) + live
    }

    /** Start of today in [zone] (epoch millis). */
    fun dayStart(nowMillis: Long, zone: ZoneId): Long =
        java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}

/** [LiveToday] for Home, re-computed on every stored sample (30 s) and reading (10 min). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTodayViewModel @Inject constructor(
    owners: CycleOwnerProvider,
    private val readings: UsageReadingRepository,
    private val telemetry: TelemetryDao
) : ViewModel() {

    val todayBytes: StateFlow<Long?> = combine(owners.owner.map { it != null }.distinctUntilChanged(), days()) { signedIn, day ->
        day.takeIf { signedIn }
    }.flatMapLatest { dayStart ->
        if (dayStart == null) flowOf(null) else live(dayStart)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Only the samples after the latest reading are read, so each 30-second update stays small
    private fun live(dayStart: Long): Flow<Long?> = readings.observeReadingsFrom(dayStart).flatMapLatest { rows ->
        val cutoff = maxOf(rows.maxOfOrNull { it.until } ?: dayStart, dayStart)
        telemetry.observeSamplesEndingAfter(cutoff).map { samples ->
            LiveToday.bytes(rows, samples.map { it.toDomain() }, dayStart)
        }
    }

    /** Start of today, emitted again at each local midnight. */
    private fun days(): Flow<Long> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            emit(LiveToday.dayStart(System.currentTimeMillis(), zone))
            val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((next - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()
}
