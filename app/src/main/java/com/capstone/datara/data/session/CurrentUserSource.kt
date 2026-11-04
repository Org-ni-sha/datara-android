package com.capstone.datara.data.session

import io.github.jan.supabase.auth.Auth
import javax.inject.Inject

/** The signed-in user's id (`public.users.id`), or null when signed out. */
fun interface CurrentUserSource {
    fun userId(): String?
}

class SupabaseCurrentUserSource @Inject constructor(
    private val auth: Auth
) : CurrentUserSource {
    override fun userId(): String? = auth.currentUserOrNull()?.id
}
