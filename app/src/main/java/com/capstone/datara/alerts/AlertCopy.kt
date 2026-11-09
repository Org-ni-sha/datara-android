package com.capstone.datara.alerts

import com.capstone.datara.data.local.entity.NotificationType
import com.capstone.datara.domain.cycle.CycleProgress
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Title and message of an alert, stored in `notifications` and shown by Android. */
data class AlertText(val title: String, val message: String)

/**
 * Alert wording. Figures are those at the moment the alert is sent, so the inbox reads as a
 * history. Every alert says, or implies, that DATAra never blocks data: a quota here is a
 * tracking amount, not a carrier limit.
 */
object AlertCopy {
    private const val MB = 1_000_000L
    private const val GB = 1_000_000_000L

    fun text(
        type: String,
        progress: CycleProgress,
        reachAt: Long?,
        now: Long,
        zone: ZoneId,
        locale: Locale = Locale.getDefault()
    ): AlertText {
        val quota = data(progress.cycle.quotaBytes, locale)
        val used = data(progress.usedBytes, locale)
        val left = data((progress.cycle.quotaBytes - progress.usedBytes).coerceAtLeast(0), locale)
        val name = progress.cycle.name?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "your quota"
        return when (type) {
            NotificationType.MILESTONE_75 -> AlertText(
                "75% of your quota used",
                "$used of $quota used on $name. $left left. Nothing is blocked."
            )
            NotificationType.MILESTONE_90 -> AlertText(
                "90% of your quota used",
                "$used of $quota used on $name. Only $left left. Nothing is blocked."
            )
            NotificationType.QUOTA_REACHED -> AlertText(
                "Quota reached",
                "You’ve used the $quota you set for $name. Your internet stays on."
            )
            NotificationType.FORECAST_24H -> AlertText(
                "Quota likely reached within a day",
                "At your pace, $name may reach $quota ${around(reachAt, now, zone, locale)}. $left left."
            )
            NotificationType.FORECAST_3H -> AlertText(
                "Quota likely reached within 3 hours",
                "At your pace, $name may reach $quota ${around(reachAt, now, zone, locale)}. $left left."
            )
            else -> throw IllegalArgumentException("Unknown notification type: $type")
        }
    }

    /** Decimal MB/GB, as on the quota screens (1 MB = 1,000,000 bytes). */
    fun data(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val (unit, label) = if (bytes >= GB) GB to "GB" else MB to "MB"
        val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (bytes >= GB) 2 else 1 }
        return "${number.format(bytes.toDouble() / unit)} $label"
    }

    /** "around 3:40 PM" today, "around Tue 3:40 PM" on another day, "soon" when already due. */
    private fun around(reachAt: Long?, now: Long, zone: ZoneId, locale: Locale): String {
        if (reachAt == null || reachAt <= now) return "soon"
        val at = Instant.ofEpochMilli(reachAt).atZone(zone)
        val sameDay = at.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val pattern = if (sameDay) "h:mm a" else "EEE h:mm a"
        return "around ${DateTimeFormatter.ofPattern(pattern, locale).format(at)}"
    }
}
