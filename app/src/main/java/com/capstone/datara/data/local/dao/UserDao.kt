package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.datara.data.local.entity.NotificationPreferences
import com.capstone.datara.data.local.entity.User
import com.capstone.datara.data.local.entity.UserModelState
import kotlinx.coroutines.flow.Flow

// Upload queue convention for every synced DAO (C3): `unsynced` lists rows whose synced_at is
// null, and `markSynced` stamps them after a successful upsert. For mutable rows that carry a
// version column (updated_at, read_at), markSynced only matches the version that was uploaded,
// so an edit made during the upload stays queued.

/** Per-user, one-row tables: `users`, `notification_preferences` and `user_model_state`. */
@Dao
interface UserDao {

    @Upsert
    suspend fun upsert(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun get(userId: String): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun observe(userId: String): Flow<User?>

    @Query("SELECT * FROM users WHERE synced_at IS NULL")
    suspend fun unsynced(): List<User>

    @Query("UPDATE users SET synced_at = :syncedAt WHERE id = :userId")
    suspend fun markSynced(userId: String, syncedAt: Long)

    // notification_preferences

    @Upsert
    suspend fun upsertPreferences(preferences: NotificationPreferences)

    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId")
    suspend fun preferences(userId: String): NotificationPreferences?

    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId")
    fun observePreferences(userId: String): Flow<NotificationPreferences?>

    @Query("SELECT * FROM notification_preferences WHERE synced_at IS NULL")
    suspend fun unsyncedPreferences(): List<NotificationPreferences>

    @Query(
        "UPDATE notification_preferences SET synced_at = :syncedAt " +
            "WHERE user_id = :userId AND updated_at = :updatedAt"
    )
    suspend fun markPreferencesSynced(userId: String, updatedAt: Long, syncedAt: Long)

    // user_model_state

    @Upsert
    suspend fun upsertModelState(state: UserModelState)

    @Query("SELECT * FROM user_model_state WHERE user_id = :userId")
    suspend fun modelState(userId: String): UserModelState?

    @Query("SELECT * FROM user_model_state WHERE user_id = :userId")
    fun observeModelState(userId: String): Flow<UserModelState?>

    @Query("SELECT * FROM user_model_state WHERE synced_at IS NULL")
    suspend fun unsyncedModelState(): List<UserModelState>

    @Query(
        "UPDATE user_model_state SET synced_at = :syncedAt " +
            "WHERE user_id = :userId AND updated_at = :updatedAt"
    )
    suspend fun markModelStateSynced(userId: String, updatedAt: Long, syncedAt: Long)
}
