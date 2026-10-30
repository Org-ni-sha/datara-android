package com.capstone.datara.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Thrown by [ProfileRepository.setUsername] when another account already holds the name. */
class UsernameTakenException : Exception("username_taken")

/**
 * The signed-in user's `public.users` row. Reads and writes are limited to the user's own row by
 * the `users_*_own` RLS policies; [isUsernameAvailable] goes through a definer function that only
 * answers yes or no (see supabase/migrations/20261031000001_usernames.sql).
 */
class ProfileRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest
) {

    @Serializable
    private data class UsernameRow(val username: String? = null)

    /** The user's username, or null when they have not chosen one yet. */
    suspend fun fetchUsername(): Result<String?> = runCatching {
        val userId = requireUserId()
        postgrest.from(USERS_TABLE)
            .select(Columns.list("username")) {
                filter { eq("id", userId) }
            }
            .decodeSingleOrNull<UsernameRow>()
            ?.username
    }

    suspend fun isUsernameAvailable(username: String): Result<Boolean> = runCatching {
        postgrest.rpc(
            function = "is_username_available",
            parameters = buildJsonObject { put("candidate", username) }
        ).decodeAs<Boolean>()
    }

    /** Saves [username] (already canonical). Fails with [UsernameTakenException] on a clash. */
    suspend fun setUsername(username: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        try {
            postgrest.from(USERS_TABLE).update({ set("username", username) }) {
                filter { eq("id", userId) }
            }
        } catch (e: Exception) {
            val details = e.message.orEmpty()
            if ("23505" in details || "duplicate key" in details) throw UsernameTakenException()
            throw e
        }
    }

    private fun requireUserId(): String =
        checkNotNull(auth.currentUserOrNull()?.id) { "No signed-in user" }

    private companion object {
        const val USERS_TABLE = "users"
    }
}
