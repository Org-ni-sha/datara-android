package com.capstone.datara.ui.quota

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.domain.cycle.CycleState

/** How complete a real cycle's measurement is; null for sample cycles. */
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

/**
 * What Quota Details shows, from either a real cycle ([fromCycleState]) or a labelled sample
 * ([HomeUiState.toDetailsModel], used by History and Notifications previews).
 */
data class QuotaDetailsModel(
    val hasQuota: Boolean,
    val name: String = "",
    val quotaBytes: Long = 0,
    /** Null when usage is unavailable (sample "missing permission"). */
    val usedBytes: Long? = 0,
    /** Clamped at 0 for display. */
    val remainingBytes: Long? = quotaBytes,
    /** Used / quota, unclamped. */
    val progress: Float = 0f,
    val startedAtMillis: Long = 0,
    val endedAtMillis: Long? = null,
    val reachedAtMillis: Long? = null,
    val isReached: Boolean = false,
    /** Real measurement details; null for samples. */
    val measurement: CycleMeasurement? = null,
    /** The preview scenario, for sample cycles only. */
    val sampleScenario: HomeSampleState? = null,
    /** True while the session or the latest cycle is still loading. */
    val isLoading: Boolean = false,
    /** The live D6 forecast of a real active cycle; null for samples, reached or ended cycles. */
    val forecast: PetForecastUi? = null
) {
    val isEnded: Boolean get() = endedAtMillis != null
    val isSample: Boolean get() = sampleScenario != null
    val canEnd: Boolean get() = hasQuota && !isEnded && when (sampleScenario) {
        null -> true
        HomeSampleState.NO_QUOTA, HomeSampleState.QUOTA_REACHED -> false
        else -> true
    }
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

/** Sample fixtures keep their illustrative numbers and are labelled as samples on screen. */
fun HomeUiState.toDetailsModel(): QuotaDetailsModel {
    val missing = scenario == HomeSampleState.MISSING_PERMISSION
    return QuotaDetailsModel(
        hasQuota = scenario != HomeSampleState.NO_QUOTA,
        name = name,
        quotaBytes = amountBytes,
        usedBytes = if (missing) null else usedBytes,
        remainingBytes = if (missing) null else remainingBytes,
        progress = if (missing) 0f else progress,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        isReached = scenario == HomeSampleState.QUOTA_REACHED,
        sampleScenario = scenario
    )
}
