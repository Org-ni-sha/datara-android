package com.capstone.datara.ui.dashboard

import com.capstone.datara.telemetry.TrackingAccess
import com.capstone.datara.ui.history.DayBucket
import java.time.format.DateTimeFormatter

/** Home's header greeting: time of day plus the profile's first name, else @username, else none. */
fun homeGreeting(hour: Int, name: String?, username: String?): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val who = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        ?: username?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }
    return if (who == null) part else "$part, $who"
}

/** Home's header subtitle. */
fun homeSubtitle(quotaName: String?): String = quotaName?.let { "Tracking “$it”" } ?: "Set a quota to start forecasting"

/** Why a live cycle is not being measured properly, most blocking first. */
enum class TrackingIssue(val title: String, val body: String) {
    USAGE_ACCESS("Tracking is paused", "Usage Access is off, so DATAra can’t read your mobile data."),
    NOTIFICATIONS("Tracking can’t run", "Notifications are off. Android needs them to keep tracking on."),
    SERVICE_STOPPED("Tracking is off", "Usage isn’t being measured, so this forecast may fall behind.");

    companion object {
        /**
         * The banner for an active cycle, from the same checks Settings shows (D9): the Android
         * grants and whether the tracking service runs. Ended or reached cycles need no tracking.
         */
        fun of(access: TrackingAccess, serviceRunning: Boolean, cycleActive: Boolean): TrackingIssue? = when {
            !cycleActive -> null
            !access.usageAccess -> USAGE_ACCESS
            !access.notifications -> NOTIFICATIONS
            !serviceRunning -> SERVICE_STOPPED
            else -> null
        }
    }
}

/** Home's "Last 7 days" card: the same daily totals as History's 7-day window. */
data class HomeWeek(val days: List<Day>, val averageDailyBytes: Long = 0) {
    data class Day(val label: String, val bytes: Long?)

    /** Measured bytes only; unmeasured days add nothing. */
    val totalBytes: Long get() = days.sumOf { it.bytes ?: 0 }
    val hasReadings: Boolean get() = days.any { it.bytes != null }
    val latest: Day? get() = days.lastOrNull { it.bytes != null }

    companion object {
        private val LABEL = DateTimeFormatter.ofPattern("MMM d")
        /** [averageDailyBytes] is History's 7-day average (per measured hour × 24), so Home shows one number. */
        fun from(week: List<DayBucket>, averageDailyBytes: Long = 0) =
            HomeWeek(week.map { Day(it.date.format(LABEL), it.bytes) }, averageDailyBytes)
    }
}
