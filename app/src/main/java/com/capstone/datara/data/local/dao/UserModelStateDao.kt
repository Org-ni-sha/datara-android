package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.datara.data.local.entity.UserModelState

/** `user_model_state`: the per-user local layer and the GLOBAL/LOCAL switch (D6). */
@Dao
interface UserModelStateDao {

    @Query("SELECT * FROM user_model_state WHERE user_id = :userId")
    suspend fun get(userId: String): UserModelState?

    /** The caller sets updatedAt = now and syncedAt = null. */
    @Upsert
    suspend fun upsert(state: UserModelState)

    @Query("SELECT * FROM user_model_state WHERE synced_at IS NULL")
    suspend fun unsynced(): List<UserModelState>

    @Query("UPDATE user_model_state SET synced_at = :syncedAt WHERE user_id = :userId AND updated_at = :updatedAt")
    suspend fun markSynced(userId: String, updatedAt: Long, syncedAt: Long)
}
