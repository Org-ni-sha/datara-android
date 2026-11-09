package com.capstone.datara.ui.quota

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.domain.cycle.CycleState

/** How complete a cycle's measurement is. */
data class CycleMeasurement(
    /** End of the latest counted 10-minute reading; null before the first one. */
    val measuredThroughMillis: Long?,
    /** Time in the cycle no reading covers (tracking off, or the process restarted). */
    val unmeasuredMillis: Long,
    /** Readings whose bytes are unknown (counter reset, clock error, ...). */
    val lostReadings: Int
) {
    val isIncomplete: Boolean get() = unmeasuredMillis > 0 || lostReadings > 0
}

/** What Quota Details shows for a real cycle ([fromCycleState]). */
data class QuotaDetailsModel(
    val hasQuota: Boolean,
    val name: String = "",
    val quotaBytes: Long = 0,
    /** Null when usage is unavailable. */
    val usedBytes: Long? = 0,
    /** Clamped at 0 for display. */
    val remainingBytes: Long? = quotaBytes,
    /** Used / quota, unclamped. */
    val progress: Float = 0f,
    val startedAtMillis: Long = 0,
    val endedAtMillis: Long? = null,
    val reachedAtMillis: Long? = null,
    val isReached: Boolean = false,
    /** Measurement details; null when there is no cycle. */
    val measurement: CycleMeasurement? = null,
    /** True while the session or the latest cycle is still loading. */
    val isLoading: Boolean = false,
    /** The live D6 forecast of an active cycle; null for reached or ended cycles. */
    val forecast: PetForecastUi? = null,
    /** The cycle's id (alerts open it); null when there is no cycle. */
    val cycleId: String? = null
) {
    val isEnded: Boolean get() = endedAtMillis != null
    val canEnd: Boolean get() = hasQuota && !isEnded
    val usageUnavailable: Boolean get() = usedBytes == null

    companion object {
        const val DEFAULT_NAME = "My data quota"

        fun fromCycleState(state: CycleState): QuotaDetailsModel = when (state) {
            CycleState.Loading -> QuotaDetailsModel(hasQuota = false, isLoading = true)
            CycleState.SignedOut, CycleState.NoCycle -> QuotaDetailsModel(hasQuota = false)
            is CycleState.Tracked -> {
                val progress = state.progress
                val cycle = progress.cycle
                QuotaDetailsModel(
                    hasQuota = true,
                    cycleId = cycle.cycleId,
                    name = cycle.name ?: DEFAULT_NAME,
                    quotaBytes = cycle.quotaBytes,
                    usedBytes = progress.usedBytes,
                    remainingBytes = progress.displayRemainingBytes,
                    progress = (progress.percentUsed / 100).toFloat(),
                    startedAtMillis = cycle.startAt,
                    endedAtMillis = cycle.endedAt,
                    reachedAtMillis = cycle.reachedAt,
                    isReached = cycle.status == CycleStatus.REACHED,
                    measurement = CycleMeasurement(
                        progress.usage.measuredThrough,
                        progress.usage.unmeasuredMillis,
                        progress.usage.lostReadings
                    )
                )
            }
        }
    }
}
