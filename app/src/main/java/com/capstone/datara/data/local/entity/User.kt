package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.users`. The row is created server-side by the `on_auth_user_created` trigger, so the
 * phone only ever stores a copy downloaded from Supabase (or the edits the user makes to it).
 *
 * Shared rules for every synced entity in this package (see `supabase/README.md`):
 * - columns match the Postgres table column for column, in `snake_case`;
 * - UUIDs are `String`, `timestamptz` is epoch millis (`Long`), `bigint` is `Long`, `jsonb` is a
 *   JSON `String`;
 * - [syncedAt] is local-only: null means the row is waiting to be uploaded (C3). Every local
 *   change must set it back to null.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey val id: String,
    val email: String,
    /** Lowercase, 3–20 of `[a-z0-9_]`; null until the username screen is done. */
    val username: String?,
    val name: String?,
    /** Optional, user-entered, E.164 (`+639XXXXXXXXX`). Never read from the SIM. */
    @ColumnInfo(name = "phone_number") val phoneNumber: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)
