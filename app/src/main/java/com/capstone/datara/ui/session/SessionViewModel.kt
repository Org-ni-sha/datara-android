package com.capstone.datara.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.AuthRepository
import com.capstone.datara.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Exposes the launch-time session decision for [com.capstone.datara.MainActivity].
 *
 * supabase-kt persists the session itself (multiplatform-settings is on the classpath and
 * `Auth` loads from storage on start), so there is nothing to read manually here — the app only
 * has to wait for [SessionState.Initializing] to resolve before choosing a start destination.
 * For a signed-in user it also checks the profile for a username, keeping the splash up while
 * it does (bounded by [PROFILE_TIMEOUT_MS]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    repository: AuthRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionStatus
        .map { it.toSessionState() }
        // Token refreshes re-emit Authenticated; only a real change should trigger a lookup.
        .distinctUntilChanged()
        .mapLatest { state ->
            if (state != SessionState.Authenticated) return@mapLatest state
            val hasUsername = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
                profileRepository.fetchUsername().map { it != null }.getOrNull()
            }
            state.withProfile(hasUsername)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Initializing
        )

    private companion object {
        const val PROFILE_TIMEOUT_MS = 5_000L
    }
}
