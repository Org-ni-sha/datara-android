package com.capstone.datara.data.session

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** The session supabase-kt saved on the phone, or null when there is none. */
fun interface SavedSessionSource {
    suspend fun load(): UserSession?
}

/** Reads the saved session through supabase-kt's own session manager; works offline. */
class SupabaseSavedSessionSource(private val auth: Auth) : SavedSessionSource {
    override suspend fun load(): UserSession? = auth.sessionManager.loadSessionOrNull()
}

/**
 * The signed-in user's id for a Supabase [status], the one rule every part of the app uses:
 *
 * - [SessionStatus.Authenticated]: the session's user.
 * - [SessionStatus.RefreshFailure]: the user of the session saved on the phone. In supabase-kt
 *   3.8 this status only means the access token expired and the refresh could not reach the
 *   server (offline, or a server error); supabase-kt keeps retrying and returns to Authenticated
 *   when the network is back. A revoked refresh token clears the session instead
 *   ([SessionStatus.NotAuthenticated]), so the user here is still signed in.
 * - [SessionStatus.NotAuthenticated] and [SessionStatus.Initializing]: null.
 */
suspend fun SessionStatus.signedInUserId(saved: SavedSessionSource): String? = when (this) {
    is SessionStatus.Authenticated -> session.userId()
    is SessionStatus.RefreshFailure -> saved.load()?.userId()
    is SessionStatus.NotAuthenticated, SessionStatus.Initializing -> null
}

/**
 * The signed-in user id over time. Repeats are dropped, so a token refresh or a switch between
 * Authenticated and RefreshFailure for the same user emits nothing new.
 */
fun Flow<SessionStatus>.signedInUserIds(saved: SavedSessionSource): Flow<String?> =
    map { it.signedInUserId(saved) }.distinctUntilChanged()

/** The session's user id, from its user, else the `sub` claim of its access token. */
internal fun UserSession.userId(): String? = user?.id ?: jwtSubject(accessToken)

/** The `sub` claim of a JWT, or null when [token] is not a readable JWT. Not verified. */
internal fun jwtSubject(token: String): String? = runCatching {
    val payload = token.split('.')[1]
    val json = String(Base64.getUrlDecoder().decode(payload.trimEnd('=')), Charsets.UTF_8)
    Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }
