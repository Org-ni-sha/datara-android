package com.capstone.datara.alerts

import com.capstone.datara.data.local.dao.NotificationDao
import com.capstone.datara.data.local.dao.UserDao
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationPreferences
import com.capstone.datara.forecast.ForecastEngine
import com.capstone.datara.forecast.ForecastStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The alert inbox (`notifications`). Room in the app, in memory in JVM tests. */
interface AlertStore {
    /** Types already recorded for the cycle. */
    suspend fun sentTypes(cycleId: String): Set<String>

    /** False when this type was already recorded for the cycle: then it must not be posted again. */
    suspend fun insert(notification: Notification): Boolean

    /** The user's alerts, newest first. */
    fun observeInbox(userId: String): Flow<List<Notification>>

    suspend fun markRead(notificationId: String, readAt: Long)
    suspend fun markAllRead(userId: String, readAt: Long)
}

/** The two Settings switches (`notification_preferences`). */
interface NotificationPreferencesStore {
    /** Defaults (both on) until the user changes a switch. */
    fun observe(userId: String): Flow<AlertPreferences>
    suspend fun update(userId: String, now: Long, change: (AlertPreferences) -> AlertPreferences)
}

/** The latest D6 forecast. */
interface ForecastStatusSource {
    val status: StateFlow<ForecastStatus>
}

@Singleton
class RoomAlertStore @Inject constructor(private val dao: NotificationDao) : AlertStore {
    override suspend fun sentTypes(cycleId: String): Set<String> = dao.typesForCycle(cycleId).toSet()
    override suspend fun insert(notification: Notification): Boolean = dao.insert(notification) != -1L
    override fun observeInbox(userId: String): Flow<List<Notification>> = dao.observeInbox(userId)
    override suspend fun markRead(notificationId: String, readAt: Long) = dao.markRead(notificationId, readAt)
    override suspend fun markAllRead(userId: String, readAt: Long) = dao.markAllRead(userId, readAt)
}

@Singleton
class RoomNotificationPreferencesStore @Inject constructor(private val dao: UserDao) : NotificationPreferencesStore {
    override fun observe(userId: String): Flow<AlertPreferences> = dao.observePreferences(userId)
        .map { row -> row?.let { AlertPreferences(it.milestonesEnabled, it.forecastAlertsEnabled) } ?: AlertPreferences() }
        .distinctUntilChanged()

    override suspend fun update(userId: String, now: Long, change: (AlertPreferences) -> AlertPreferences) {
        val row = dao.preferences(userId)
        val current = row?.let { AlertPreferences(it.milestonesEnabled, it.forecastAlertsEnabled) } ?: AlertPreferences()
        val next = change(current)
        if (next == current && row != null) return
        // synced_at null queues the row for upload (C3)
        dao.upsertPreferences(NotificationPreferences(userId, next.milestones, next.forecasts, updatedAt = now, syncedAt = null))
    }
}

class EngineForecastStatusSource @Inject constructor(engine: ForecastEngine) : ForecastStatusSource {
    override val status: StateFlow<ForecastStatus> = engine.status
}
