package com.capstone.datara.ui.session

import io.github.jan.supabase.auth.status.SessionStatus

/**
 * What the app needs to know about the session at launch, reduced from Supabase's
 * [SessionStatus] and the user's profile to the cases that decide which screen to show first.
 */
enum class SessionState {
    /** Supabase is still restoring any persisted session — show the splash, decide nothing yet. */
    Initializing,

    /**
     * A signed-in session and a finished profile; the user skips the login screen. Includes an
     * offline user whose access token expired and could not be refreshed yet.
     */
    Authenticated,

    /**
     * Signed in, but no username chosen yet — e.g. the app was closed on the username screen, or
     * the account came from Google sign-in. The user must pick one before reaching the dashboard.
     */
    NeedsUsername,

    /** No session: never signed in, signed out, or the session was revoked and cleared. */
    Unauthenticated
}

/**
 * Maps a Supabase [SessionStatus] to the [SessionState] the navigation gate acts on, before the
 * profile is known. A signed-in status maps to [SessionState.Authenticated]; [withProfile] then
 * refines it.
 *
 * [SessionStatus.RefreshFailure] is signed in. In supabase-kt 3.8 it only means the access token
 * expired and the refresh could not reach the server (no internet, or a server error); the
 * session stays saved and supabase-kt retries until the network is back, then returns to
 * Authenticated. A revoked or invalid refresh token clears the session instead, which arrives
 * as [SessionStatus.NotAuthenticated]. So an app opened offline after an hour still reaches
 * Home, which reads only the phone's Room data; [withProfile] lets the user through when the
 * profile lookup cannot reach the server.
 */
fun SessionStatus.toSessionState(): SessionState = when (this) {
    is SessionStatus.Initializing -> SessionState.Initializing
    is SessionStatus.Authenticated -> SessionState.Authenticated
    is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
    is SessionStatus.RefreshFailure -> SessionState.Authenticated
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
