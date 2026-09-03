package com.capstone.datara.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import com.capstone.datara.BuildConfig

import kotlin.time.Duration.Companion.seconds

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        requestTimeout = 30.seconds
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}