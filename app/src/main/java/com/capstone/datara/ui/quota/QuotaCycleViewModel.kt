package com.capstone.datara.ui.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.QuotaCycleManager
import com.capstone.datara.forecast.ForecastEngine
import com.capstone.datara.forecast.ForecastStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of a create or end request. */
data class QuotaActionState(val isSaving: Boolean = false, val errorMessage: String? = null)

/**
 * Create Quota and Quota Details against the real cycle use cases ([QuotaCycleManager]), with the
 * live D6 forecast ([ForecastEngine]) of the active cycle. Home's hero shows the same real cycle
 * and forecast.
 */
@HiltViewModel
class QuotaCycleViewModel @Inject constructor(
    private val manager: QuotaCycleManager,
    private val forecasts: ForecastEngine
) : ViewModel() {

    val details: StateFlow<QuotaDetailsModel> = combine(manager.state, forecasts.status, ::detailsWithForecast)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), detailsWithForecast(manager.state.value, forecasts.status.value))

    private val mutableAction = MutableStateFlow(QuotaActionState())
    val action: StateFlow<QuotaActionState> = mutableAction.asStateFlow()

    /** Starts a cycle; [onCreated] runs only when it was stored. */
    fun createQuota(quotaBytes: Long, name: String, onCreated: () -> Unit = {}) = run(QuotaAction.CREATE, onCreated) {
        manager.create(quotaBytes, name)
    }

    fun endCycle(onEnded: () -> Unit = {}) = run(QuotaAction.END, onEnded) { manager.end() }

    fun clearError() = mutableAction.update { it.copy(errorMessage = null) }

    companion object {
        /** The cycle's details plus the live forecast text while it is active. */
        fun detailsWithForecast(state: CycleState, status: ForecastStatus, now: Long = System.currentTimeMillis()): QuotaDetailsModel {
            val details = QuotaDetailsModel.fromCycleState(state)
            val cycle = (state as? CycleState.Tracked)?.progress?.cycle ?: return details
            if (cycle.status != CycleStatus.ACTIVE || cycle.endedAt != null) return details
            return details.copy(forecast = PetForecastUi.from(status, cycle.cycleId, now, ::formatCycleDate))
        }
    }

    private fun run(kind: QuotaAction, onSuccess: () -> Unit, block: suspend () -> Result<*>) {
        if (mutableAction.value.isSaving) return
        mutableAction.value = QuotaActionState(isSaving = true)
        viewModelScope.launch {
            val result = block()
            mutableAction.value = QuotaActionState(errorMessage = result.exceptionOrNull()?.let { quotaErrorMessage(kind, it) })
            if (result.isSuccess) onSuccess()
        }
    }
}

enum class QuotaAction { CREATE, END }

/** Sentences written for people; anything else (database or system text) becomes a plain retry message. */
private val PLAIN_MESSAGES = setOf("Sign in to track a quota.", "There is no cycle to end.")

/** A plain sentence for a failed create or end; raw exception text is never shown. */
fun quotaErrorMessage(action: QuotaAction, error: Throwable): String =
    error.message?.takeIf { it in PLAIN_MESSAGES } ?: when (action) {
        QuotaAction.CREATE -> "Couldn’t start this quota. Try again."
        QuotaAction.END -> "Couldn’t end this cycle. Try again."
    }
