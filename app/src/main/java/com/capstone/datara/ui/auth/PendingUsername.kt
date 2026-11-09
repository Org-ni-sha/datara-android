package com.capstone.datara.ui.auth

/**
 * The username typed on Create account when saving it right after sign-up failed (taken, or
 * offline). The username screen picks it up once, so the user sees their choice and why.
 */
internal object PendingUsername {
    @Volatile var value: String? = null

    fun take(): String? = value.also { value = null }
}
