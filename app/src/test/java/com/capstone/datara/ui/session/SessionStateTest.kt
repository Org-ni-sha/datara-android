package com.capstone.datara.ui.session

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SessionStateTest {

    @Test
    fun `a refresh that cannot reach the server stays signed in`() {
        val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))
        assertEquals(SessionState.Authenticated, offline.toSessionState())
    }

    @Test
    fun `offline with an unknown profile still opens the dashboard`() {
        val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))
        assertEquals(SessionState.Authenticated, offline.toSessionState().withProfile(hasUsername = null))
    }

    @Test
    fun `signed out, revoked and fresh installs go to the welcome screen`() {
        // A revoked refresh token clears the session: supabase-kt reports NotAuthenticated
        assertEquals(SessionState.Unauthenticated, SessionStatus.NotAuthenticated(isSignOut = true).toSessionState())
        assertEquals(SessionState.Unauthenticated, SessionStatus.NotAuthenticated(isSignOut = false).toSessionState())
    }

    @Test
    fun `restoring the session decides nothing yet`() {
        assertEquals(SessionState.Initializing, SessionStatus.Initializing.toSessionState())
    }

    @Test
    fun `signed-in user without a username needs one`() {
        assertEquals(SessionState.NeedsUsername, SessionState.Authenticated.withProfile(hasUsername = false))
    }

    @Test
    fun `signed-in user with a username goes to the dashboard`() {
        assertEquals(SessionState.Authenticated, SessionState.Authenticated.withProfile(hasUsername = true))
    }

    @Test
    fun `unknown profile lets the user through rather than trapping them offline`() {
        assertEquals(SessionState.Authenticated, SessionState.Authenticated.withProfile(hasUsername = null))
    }

    @Test
    fun `profile never changes a signed-out or initializing state`() {
        listOf(SessionState.Unauthenticated, SessionState.Initializing).forEach { state ->
            listOf(true, false, null).forEach { hasUsername ->
                assertEquals(state, state.withProfile(hasUsername))
            }
        }
    }
}
