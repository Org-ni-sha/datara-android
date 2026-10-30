package com.capstone.datara.ui.session

import io.github.jan.supabase.auth.status.SessionStatus

/**
 * What the app needs to know about the session at launch, reduced from Supabase's
 * [SessionStatus] and the user's profile to the cases that decide which screen to show first.
 */
enum class SessionState {
    /** Supabase is still restoring any persisted session — show the splash, decide nothing yet. */
    Initializing,

    /** A valid session and a finished profile; the user skips the login screen. */
    Authenticated,

    /**
     * Signed in, but no username chosen yet — e.g. the app was closed on the username screen, or
     * the account came from Google sign-in. The user must pick one before reaching the dashboard.
     */
    NeedsUsername,

    /** No usable session. Covers both a genuinely signed-out user and a failed token refresh. */
    Unauthenticated
}

/**
 * Maps a Supabase [SessionStatus] to the [SessionState] the navigation gate acts on, before the
 * profile is known. A signed-in status maps to [SessionState.Authenticated]; [withProfile] then
 * refines it.
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

/**
 * Applies the profile lookup to a signed-in state. [hasUsername] is null when the lookup failed
 * or timed out (offline launch): the user is let through rather than trapped on a screen that
 * needs the network, and the check runs again on the next launch.
 */
fun SessionState.withProfile(hasUsername: Boolean?): SessionState =
    if (this == SessionState.Authenticated && hasUsername == false) {
        SessionState.NeedsUsername
    } else {
        this
    }
