package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capstone.datara.data.local.entity.Notification
import kotlinx.coroutines.flow.Flow

/** `notifications`: the alert inbox (D8). */
@Dao
interface NotificationDao {

    /**
     * Records an alert; returns -1 when this type was already sent for the cycle, in which case
     * D8 must not post it again.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: Notification): Long

    /** The user's inbox, newest first. */
    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY sent_at DESC")
    fun observeInbox(userId: String): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Query(
        "UPDATE notifications SET read_at = :readAt, synced_at = NULL " +
            "WHERE notification_id = :notificationId AND read_at IS NULL"
    )
    suspend fun markRead(notificationId: String, readAt: Long)

    @Query("SELECT * FROM notifications WHERE synced_at IS NULL ORDER BY sent_at")
    suspend fun unsynced(): List<Notification>

    /** [readAt] is the uploaded row's value, so a read during the upload stays queued. */
    @Query(
        "UPDATE notifications SET synced_at = :syncedAt " +
            "WHERE notification_id = :notificationId AND read_at IS :readAt"
    )
    suspend fun markSynced(notificationId: String, readAt: Long?, syncedAt: Long)
}
