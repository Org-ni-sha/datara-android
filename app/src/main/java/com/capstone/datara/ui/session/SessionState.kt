package com.capstone.datara.ui.session

import io.github.jan.supabase.auth.status.SessionStatus

/**
 * What the app needs to know about the session at launch, reduced from Supabase's
 * [SessionStatus] to the three cases that decide which screen to show first.
 */
enum class SessionState {
    /** Supabase is still restoring any persisted session — show the splash, decide nothing yet. */
    Initializing,

    /** A valid session was restored or created; the user skips the login screen. */
    Authenticated,

    /** No usable session. Covers both a genuinely signed-out user and a failed token refresh. */
    Unauthenticated
}

/**
 * Maps a Supabase [SessionStatus] to the [SessionState] the navigation gate acts on.
 *
 * [SessionStatus.RefreshFailure] is deliberately treated as unauthenticated: the refresh token
 * is stale or revoked, so the only recovery is signing in again. Treating it as authenticated
 * would land the user on a dashboard whose every request then fails with a 401.
 */
fun SessionStatus.toSessionState(): SessionState = when (this) {
    is SessionStatus.Initializing -> SessionState.Initializing
    is SessionStatus.Authenticated -> SessionState.Authenticated
    is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
    is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
}
