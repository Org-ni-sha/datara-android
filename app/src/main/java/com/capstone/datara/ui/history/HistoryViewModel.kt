package com.capstone.datara.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.TrackingCycleRepository
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.data.local.dao.ForecastDao
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Ready(val data: HistoryData) : HistoryUiState
}

/**
 * History over Room: this device's cycles ([TrackingCycleRepository]), the 10-minute
 * NetworkStatsManager readings ([UsageReadingRepository], the same source as the quota and the
 * forecast) and the logged `forecasts` of reached cycles. Re-aggregates whenever a reading or a
 * cycle is stored; the work runs on [Dispatchers.Default].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    owners: CycleOwnerProvider,
    cycles: TrackingCycleRepository,
    readings: UsageReadingRepository,
    private val forecasts: ForecastDao
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    /** Pull to refresh: re-reads Room now (the flows already update on every stored reading). */
    fun refresh() { refreshTick.value++ }

    val state: StateFlow<HistoryUiState> = combine(owners.owner, refreshTick) { owner, _ -> owner }.flatMapLatest { owner ->
        if (owner == null) return@flatMapLatest flowOf(HistoryUiState.Ready(HistoryData(emptyList(), emptyList(), emptyList(), 0)))
        cycles.observeAll(owner.userId)
            .map { all -> all.filter { it.deviceId == owner.deviceId } }
            .flatMapLatest { deviceCycles ->
                val now = System.currentTimeMillis()
                val from = minOf(deviceCycles.minOfOrNull { it.startAt } ?: Long.MAX_VALUE,
                    now - (HistoryAggregation.WEEK_DAYS + 1) * 24 * HOUR_MS)
                readings.observeReadingsFrom(from).map { rows ->
                    val forecastsByCycle = deviceCycles.filter { it.reachedAt != null }
                        .associate { it.cycleId to forecasts.between(it.cycleId, it.startAt, it.reachedAt!!) }
                    HistoryUiState.Ready(HistoryAggregation.build(deviceCycles, rows, forecastsByCycle,
                        System.currentTimeMillis(), ZoneId.systemDefault())) as HistoryUiState
                }
            }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)
}
