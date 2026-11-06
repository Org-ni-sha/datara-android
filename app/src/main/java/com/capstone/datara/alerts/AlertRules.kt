package com.capstone.datara.alerts

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.local.entity.NotificationType
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.forecast.ForecastStatus

/** The two Settings switches (`notification_preferences`); both on until the user changes them. */
data class AlertPreferences(val milestones: Boolean = true, val forecasts: Boolean = true)

/**
 * D8 trigger rules (decided 2026-11-04, docs/erd-draft.md): usage milestones at 75%, 90% and 100%
 * of the active cycle's quota, and forecast alerts when the PET is within 24 hours and within
 * 3 hours. Each type goes out at most once per cycle; Room's unique `(cycle_id, notification_type)`
 * index enforces that, and these rules never ask for a type already in `sent`.
 *
 * Pure functions of the cycle, the latest forecast, the preferences and the time, so the rules
 * are unit-tested without Android.
 */
object AlertRules {
    const val HOUR_MS = 3_600_000L
    const val FORECAST_24H_MS = 24 * HOUR_MS
    const val FORECAST_3H_MS = 3 * HOUR_MS

    /**
     * A forecast older than this is not acted on. Forecasts are made each hour, so this allows one
     * missed run (for example a late durable read) but not a stale PET from hours ago.
     */
    const val MAX_FORECAST_AGE_MS = 2 * HOUR_MS

    val MILESTONES = listOf(NotificationType.MILESTONE_75, NotificationType.MILESTONE_90, NotificationType.QUOTA_REACHED)
    val FORECASTS = listOf(NotificationType.FORECAST_24H, NotificationType.FORECAST_3H)

    /**
     * The alerts due now for the device's current cycle, at most one milestone and one forecast
     * alert. Only the highest crossed level of each kind is returned, so a jump from 70% to 100%
     * sends "quota reached" alone, and a lower level is skipped once a higher one was [sent].
     */
    fun due(
        state: CycleState,
        forecast: ForecastStatus,
        preferences: AlertPreferences,
        sent: Set<String>,
        now: Long
    ): List<String> {
        val progress = (state as? CycleState.Tracked)?.progress ?: return emptyList()
        // An ended cycle (by the user, or replaced) gets nothing more
        if (progress.cycle.endedAt != null) return emptyList()
        return listOfNotNull(
            milestone(progress)?.takeIf { preferences.milestones }?.takeUnless { alreadyCovered(it, MILESTONES, sent) },
            forecast(progress, forecast, now)?.takeIf { preferences.forecasts }?.takeUnless { alreadyCovered(it, FORECASTS, sent) }
        )
    }

    /** The highest milestone measured usage has crossed, or null below 75%. */
    fun milestone(progress: CycleProgress): String? {
        val used = progress.usedBytes
        val quota = progress.cycle.quotaBytes
        return when {
            progress.cycle.status == CycleStatus.REACHED || progress.usage.reachedAt != null || used >= quota ->
                NotificationType.QUOTA_REACHED
            used * 10 >= quota * 9 -> NotificationType.MILESTONE_90
            used * 4 >= quota * 3 -> NotificationType.MILESTONE_75
            else -> null
        }
    }

    /**
     * The forecast alert the latest forecast calls for, or null. Only a fresh [ForecastStatus.Ready]
     * for this active cycle counts: collecting, unavailable, beyond 30 days (`reachAt == null`),
     * a forecast for another cycle, or a reached quota never trigger one.
     */
    fun forecast(progress: CycleProgress, forecast: ForecastStatus, now: Long): String? {
        val cycle = progress.cycle
        if (cycle.status != CycleStatus.ACTIVE || progress.usedBytes >= cycle.quotaBytes) return null
        val ready = forecast as? ForecastStatus.Ready ?: return null
        if (ready.cycleId != cycle.cycleId) return null
        val reachAt = ready.reachAt ?: return null
        if (now - ready.predictedAt !in 0..MAX_FORECAST_AGE_MS) return null
        val left = reachAt - now
        return when {
            left <= FORECAST_3H_MS -> NotificationType.FORECAST_3H
            left <= FORECAST_24H_MS -> NotificationType.FORECAST_24H
            else -> null
        }
    }

    /** True when [type] or a higher level of the same kind ([levels], lowest first) was sent. */
    private fun alreadyCovered(type: String, levels: List<String>, sent: Set<String>): Boolean =
        levels.drop(levels.indexOf(type)).any { it in sent }
}
