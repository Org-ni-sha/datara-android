package com.capstone.datara.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the launch-time session decision for [com.capstone.datara.MainActivity].
 *
 * supabase-kt persists the session itself (multiplatform-settings is on the classpath and
 * `Auth` loads from storage on start), so there is nothing to read manually here — the app only
 * has to wait for [SessionState.Initializing] to resolve before choosing a start destination.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    repository: AuthRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionStatus
        .map { it.toSessionState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Initializing
        )
}
