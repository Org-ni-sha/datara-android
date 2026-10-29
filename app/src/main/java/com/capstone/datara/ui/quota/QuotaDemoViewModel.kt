package com.capstone.datara.ui.quota

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class HomeSampleState(val label: String) {
    NO_QUOTA("No active quota"), COLLECTING("Collecting usage"),
    FORECAST_AVAILABLE("Forecast available"), MISSING_PERMISSION("Missing permission"),
    QUOTA_REACHED("Quota reached")
}

/** Presentation fixtures only. No telemetry, permissions, storage or inference is connected. */
data class HomeUiState(
    val scenario: HomeSampleState = HomeSampleState.NO_QUOTA,
    val amountBytes: Long = 10_000_000_000L,
    val name: String = "Everyday data",
    val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long? = null
) {
    val isEnded: Boolean get() = endedAtMillis != null
    val canEnd: Boolean get() = !isEnded && scenario != HomeSampleState.NO_QUOTA && scenario != HomeSampleState.QUOTA_REACHED
    val usedBytes: Long get() = when (scenario) {
        HomeSampleState.FORECAST_AVAILABLE -> amountBytes / 5 * 2
        HomeSampleState.QUOTA_REACHED -> amountBytes
        else -> 0L
    }
    val remainingBytes: Long get() = (amountBytes - usedBytes).coerceAtLeast(0)
    val progress: Float get() = if (amountBytes > 0) usedBytes.toFloat() / amountBytes else 0f
}

@HiltViewModel
class QuotaDemoViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(
        scenario = HomeSampleState.entries.firstOrNull { it.name == saved.get<String>("scenario") }
            ?: HomeSampleState.NO_QUOTA,
        amountBytes = saved.get<Long>("amount") ?: 10_000_000_000L,
        name = saved.get<String>("quotaName") ?: "Everyday data",
        startedAtMillis = saved.get<Long>("startedAt") ?: System.currentTimeMillis(),
        endedAtMillis = saved.get<Long>("endedAt")
    ))
    val uiState = mutableState.asStateFlow()

    init { update(mutableState.value) }

    fun selectScenario(scenario: HomeSampleState) = update(mutableState.value.copy(scenario = scenario, endedAtMillis = null))

    fun createQuota(amountBytes: Long, name: String, nowMillis: Long = System.currentTimeMillis()) {
        require(amountBytes >= QuotaUnit.MB.bytes)
        update(HomeUiState(HomeSampleState.COLLECTING, amountBytes, name.trim().ifEmpty { "My data quota" }, nowMillis))
    }

    fun endCycle(nowMillis: Long = System.currentTimeMillis()) {
        val current = mutableState.value
        if (current.canEnd) update(current.copy(endedAtMillis = nowMillis.coerceAtLeast(current.startedAtMillis)))
    }

    private fun update(state: HomeUiState) {
        saved["scenario"] = state.scenario.name
        saved["amount"] = state.amountBytes
        saved["quotaName"] = state.name
        saved["startedAt"] = state.startedAtMillis
        saved["endedAt"] = state.endedAtMillis
        mutableState.value = state
    }
}
