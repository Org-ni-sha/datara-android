package com.capstone.datara.data.session

import io.github.jan.supabase.auth.Auth
import javax.inject.Inject

/**
 * The signed-in user's id (`public.users.id`), or null when signed out. Offline after the access
 * token expired the user is still signed in (see [signedInUserId]).
 */
fun interface CurrentUserSource {
    suspend fun userId(): String?
}

class SupabaseCurrentUserSource @Inject constructor(
    private val auth: Auth
) : CurrentUserSource {
    private val saved = SupabaseSavedSessionSource(auth)

    override suspend fun userId(): String? = auth.sessionStatus.value.signedInUserId(saved)
}
