package com.capstone.datara.ui.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.domain.cycle.QuotaCycleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of a create or end request. */
data class QuotaActionState(val isSaving: Boolean = false, val errorMessage: String? = null)

/**
 * Create Quota and Quota Details against the real cycle use cases ([QuotaCycleManager]).
 * Home still runs on [QuotaDemoViewModel] fixtures until D5 connects it.
 */
@HiltViewModel
class QuotaCycleViewModel @Inject constructor(private val manager: QuotaCycleManager) : ViewModel() {

    val details: StateFlow<QuotaDetailsModel> = manager.state
        .map(QuotaDetailsModel::fromCycleState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuotaDetailsModel.fromCycleState(manager.state.value))

    private val mutableAction = MutableStateFlow(QuotaActionState())
    val action: StateFlow<QuotaActionState> = mutableAction.asStateFlow()

    /** Starts a cycle; [onCreated] runs only when it was stored. */
    fun createQuota(quotaBytes: Long, name: String, onCreated: () -> Unit = {}) = run(onCreated) {
        manager.create(quotaBytes, name)
    }

    fun endCycle(onEnded: () -> Unit = {}) = run(onEnded) { manager.end() }

    fun clearError() = mutableAction.update { it.copy(errorMessage = null) }

    private fun run(onSuccess: () -> Unit, block: suspend () -> Result<*>) {
        if (mutableAction.value.isSaving) return
        mutableAction.value = QuotaActionState(isSaving = true)
        viewModelScope.launch {
            val result = block()
            mutableAction.value = QuotaActionState(errorMessage = result.exceptionOrNull()?.let { it.message ?: "Something went wrong. Try again." })
            if (result.isSuccess) onSuccess()
        }
    }
}
