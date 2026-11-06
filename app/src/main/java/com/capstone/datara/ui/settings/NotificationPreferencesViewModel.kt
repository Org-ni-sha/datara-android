package com.capstone.datara.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.alerts.AlertPreferences
import com.capstone.datara.alerts.NotificationPreferencesStore
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.domain.cycle.CycleClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The two Settings switches, stored in Room `notification_preferences` and read by the alert rules. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val owners: CycleOwnerProvider,
    private val store: NotificationPreferencesStore,
    private val clock: CycleClock
) : ViewModel() {

    val preferences: StateFlow<AlertPreferences> = owners.owner
        .flatMapLatest { owner -> owner?.let { store.observe(it.userId) } ?: flowOf(AlertPreferences()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertPreferences())

    fun setMilestones(enabled: Boolean) = update { it.copy(milestones = enabled) }
    fun setForecasts(enabled: Boolean) = update { it.copy(forecasts = enabled) }

    private fun update(change: (AlertPreferences) -> AlertPreferences) {
        viewModelScope.launch {
            val owner = owners.owner.first() ?: return@launch
            store.update(owner.userId, clock.now(), change)
        }
    }
}
