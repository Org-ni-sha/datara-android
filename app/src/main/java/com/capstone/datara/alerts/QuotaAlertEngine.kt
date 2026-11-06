package com.capstone.datara.alerts

import android.util.Log
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.di.ApplicationScope
import com.capstone.datara.domain.cycle.CycleClock
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.forecast.CycleStateSource
import com.capstone.datara.forecast.ForecastStatus
import com.capstone.datara.forecast.ForecastZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Shows an alert as an Android notification (a no-op when notifications are not allowed). */
fun interface AlertPoster {
    fun post(notification: Notification)
}

/**
 * D8: turns the active cycle's measured usage (D3) and the latest PET (D6) into alerts.
 *
 * Cadence: it follows [CycleStateSource] (recomputed after each 10-minute durable read), the
 * forecast status (once per hour) and the preferences, so it wakes the phone no more often
 * than telemetry already does. Started with the process by [com.capstone.datara.DataraApplication],
 * so alerts go out while only the tracking service is running.
 *
 * An alert is recorded in Room first; the unique `(cycle_id, notification_type)` index turns a
 * second insert into a no-op, and only a new row is posted. The inbox keeps the alert even when
 * Android notifications are off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class QuotaAlertEngine(
    private val cycles: CycleStateSource,
    private val forecasts: ForecastStatusSource,
    private val preferences: NotificationPreferencesStore,
    private val store: AlertStore,
    private val poster: AlertPoster,
    private val clock: CycleClock,
    private val zone: ForecastZone,
    private val scope: CoroutineScope,
    private val locale: () -> Locale
) {
    @Inject
    constructor(
        cycles: CycleStateSource,
        forecasts: ForecastStatusSource,
        preferences: NotificationPreferencesStore,
        store: AlertStore,
        poster: AlertPoster,
        clock: CycleClock,
        zone: ForecastZone,
        @ApplicationScope scope: CoroutineScope
    ) : this(cycles, forecasts, preferences, store, poster, clock, zone, scope, { Locale.getDefault() })

    private val lock = Mutex()

    fun start() {
        scope.launch {
            cycles.state.flatMapLatest { state ->
                val userId = (state as? CycleState.Tracked)?.progress?.cycle?.userId
                if (userId == null) emptyFlow()
                else combine(preferences.observe(userId), forecasts.status) { prefs, forecast -> Triple(state, prefs, forecast) }
            }.collect { (state, prefs, forecast) ->
                runCatching { evaluate(state, forecast, prefs) }.onFailure { Log.e(TAG, "Alert check failed", it) }
            }
        }
    }

    /** Records and posts the alerts due now; returns the ones newly sent. */
    suspend fun evaluate(state: CycleState, forecast: ForecastStatus, prefs: AlertPreferences): List<Notification> = lock.withLock {
        val progress = (state as? CycleState.Tracked)?.progress ?: return emptyList()
        val cycle = progress.cycle
        val now = clock.now()
        val due = AlertRules.due(state, forecast, prefs, store.sentTypes(cycle.cycleId), now)
        due.mapNotNull { type ->
            val text = AlertCopy.text(type, progress, (forecast as? ForecastStatus.Ready)?.reachAt, now, zone.zone(), locale())
            val notification = Notification(
                notificationId = notificationId(cycle.cycleId, type),
                userId = cycle.userId,
                cycleId = cycle.cycleId,
                notificationType = type,
                title = text.title,
                message = text.message,
                sentAt = now,
                readAt = null
            )
            notification.takeIf { store.insert(it) }?.also { poster.post(it) }
        }
    }

    companion object {
        private const val TAG = "QuotaAlertEngine"

        /** The same id for the same cycle and type, so sync retries upsert. */
        fun notificationId(cycleId: String, type: String): String =
            UUID.nameUUIDFromBytes("notification:$cycleId:$type".toByteArray()).toString()
    }
}
