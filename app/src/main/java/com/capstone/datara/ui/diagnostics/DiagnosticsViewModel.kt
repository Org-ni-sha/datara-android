package com.capstone.datara.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.local.dao.ForecastDao
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.dao.UserModelStateDao
import com.capstone.datara.domain.cycle.CycleClock
import com.capstone.datara.forecast.FeatureContract
import com.capstone.datara.forecast.FeatureExtractor
import com.capstone.datara.forecast.ForecastEngine
import com.capstone.datara.forecast.ForecastZone
import com.capstone.datara.forecast.HourlyUsage
import com.capstone.datara.forecast.NextHourModelProvider
import com.capstone.datara.forecast.OnnxNextHourModel
import com.capstone.datara.telemetry.TelemetryController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads the real pipeline state for the hidden diagnostics screen (D9). Read-only. */
class DiagnosticsRepository @Inject constructor(
    private val owners: CycleOwnerProvider,
    private val telemetry: TelemetryDao,
    private val forecasts: ForecastDao,
    private val modelStates: UserModelStateDao,
    private val models: NextHourModelProvider,
    private val engine: ForecastEngine,
    private val controller: TelemetryController,
    private val clock: CycleClock,
    private val zone: ForecastZone
) {
    suspend fun snapshot(): DiagnosticsSnapshot = withContext(Dispatchers.IO) {
        val now = clock.now()
        val owner = owners.owner.first()
        val hour = FeatureContract.hourStart(now)
        val from = hour - 25 * FeatureContract.HOUR_MS
        val history = HourlyUsage.aggregate(
            telemetry.readingsOverlapping(from, hour).map { it.toDomain() },
            telemetry.samplesEndingIn(from, hour).map { it.toDomain() }
        )
        val model = models.model()
        DiagnosticsSnapshot(
            now = now,
            zone = zone.zone(),
            signedIn = owner != null,
            modelState = owner?.let { modelStates.get(it.userId) },
            forecasts = owner?.let { forecasts.recent(it.userId, it.deviceId, 24) }.orEmpty(),
            model = model?.let {
                val onnx = it as? OnnxNextHourModel
                ModelInfo(it.version, onnx?.outputTransform?.key ?: "unknown", onnx?.outputScale ?: 1.0, FeatureContract.CONTRACT_VERSION)
            },
            timing = engine.lastTiming.value,
            forecastStatus = engine.status.value,
            lastSampleEnd = telemetry.latestSampleEnd(),
            lastReadingUntil = telemetry.latestReadingUntil(),
            samplesLast24h = telemetry.countSamplesEndingSince(now - 24 * FeatureContract.HOUR_MS),
            measuredHoursLast24h = FeatureExtractor.observedLast24h(history, hour),
            serviceRunning = controller.isRunning.value
        )
    }
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val repository: DiagnosticsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

    init {
        // Live enough for the demo: telemetry ticks every 30 s
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.snapshot() }.onSuccess { mutableState.value = DiagnosticsMapper.map(it) }
        }
    }

    private companion object {
        const val REFRESH_MS = 15_000L
    }
}
