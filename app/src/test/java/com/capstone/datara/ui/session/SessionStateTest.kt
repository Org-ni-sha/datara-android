package com.capstone.datara.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {

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
