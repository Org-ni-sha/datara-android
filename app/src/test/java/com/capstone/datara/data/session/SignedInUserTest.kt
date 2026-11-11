package com.capstone.datara.data.session

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.Base64

class SignedInUserTest {

    private val session = session(userId = USER)
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))
    private val savedOnPhone = SavedSessionSource { session }
    private val nothingSaved = SavedSessionSource { null }

    @Test
    fun `authenticated is the session's user`() = runBlocking {
        assertEquals(USER, SessionStatus.Authenticated(session).signedInUserId(nothingSaved))
    }

    @Test
    fun `refresh failure offline is the user of the session saved on the phone`() = runBlocking {
        assertEquals(USER, offline.signedInUserId(savedOnPhone))
    }

    @Test
    fun `refresh failure with no saved session is signed out`() = runBlocking {
        assertNull(offline.signedInUserId(nothingSaved))
    }

    @Test
    fun `saved session without its user falls back to the token's subject`() = runBlocking {
        val tokenOnly = SavedSessionSource { session(userId = null) }
        assertEquals(USER, offline.signedInUserId(tokenOnly))
    }

    @Test
    fun `signed out and initializing have no user`() = runBlocking {
        listOf(
            SessionStatus.NotAuthenticated(isSignOut = true),
            SessionStatus.NotAuthenticated(isSignOut = false),
            SessionStatus.Initializing
        ).forEach { assertNull(it.signedInUserId(savedOnPhone)) }
    }

    @Test
    fun `going offline and back keeps one owner, signing out clears it`() = runBlocking {
        val statuses = flowOf(
            SessionStatus.Initializing,
            SessionStatus.Authenticated(session),
            offline,
            offline,
            SessionStatus.Authenticated(session(userId = USER, accessToken = jwt(USER, extra = "refreshed"))),
            SessionStatus.NotAuthenticated(isSignOut = true)
        )
        assertEquals(listOf(null, USER, null), statuses.signedInUserIds(savedOnPhone).toList())
    }

    @Test
    fun `an unreadable token has no subject`() {
        assertNull(jwtSubject("not-a-jwt"))
        assertNull(jwtSubject("a.%%%.c"))
        assertNull(jwtSubject(jwt(sub = "")))
    }

    private companion object {
        const val USER = "6f1c2b0e-8a43-4c1d-9a51-3e0f7d2b9c11"

        fun jwt(sub: String, extra: String = "x"): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            fun part(json: String) = encoder.encodeToString(json.toByteArray())
            return part("""{"alg":"HS256"}""") + "." + part("""{"sub":"$sub","n":"$extra"}""") + ".sig"
        }

        fun session(userId: String?, accessToken: String = jwt(USER)) = UserSession(
            accessToken = accessToken,
            refreshToken = "refresh",
            expiresIn = 3600,
            tokenType = "bearer",
            user = userId?.let { UserInfo(aud = "authenticated", id = it) }
        )
    }
}
