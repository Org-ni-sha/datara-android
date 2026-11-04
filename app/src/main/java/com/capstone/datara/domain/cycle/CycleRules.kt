package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle

/**
 * Status transitions of a [TrackingCycle], pure so every rule is unit tested:
 *
 * ```
 * active  --usage reaches quota-->  reached   (reached_at = end of the crossing reading)
 * active  --user ends----------->   ended     (ended_at = now)
 * reached --user ends----------->   reached   (status kept, ended_at = now)
 * ```
 * `ended` is final, and `reached` never goes back to `active`.
 */
object CycleRules {

    fun create(
        cycleId: String,
        userId: String,
        deviceId: String,
        name: String?,
        quotaBytes: Long,
        now: Long
    ): TrackingCycle = validate(
        TrackingCycle(
            cycleId = cycleId,
            userId = userId,
            deviceId = deviceId,
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            quotaBytes = quotaBytes,
            startAt = now,
            reachedAt = null,
            endedAt = null,
            status = CycleStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
    )

    /**
     * Applies measured usage: an active cycle whose usage reached the quota becomes reached.
     * Returns the same instance when nothing changes, so callers can skip the write.
     */
    fun applyUsage(cycle: TrackingCycle, usage: CycleUsage, now: Long): TrackingCycle {
        val reachedAt = usage.reachedAt ?: return cycle
        if (cycle.status != CycleStatus.ACTIVE) return cycle
        return validate(
            cycle.copy(
                status = CycleStatus.REACHED,
                reachedAt = reachedAt.coerceAtLeast(cycle.startAt),
                updatedAt = maxOf(now, cycle.updatedAt)
            )
        )
    }

    /**
     * The user ends the cycle (or replaces it with a new one) at [now].
     * @throws IllegalStateException when it was already ended
     */
    fun end(cycle: TrackingCycle, now: Long): TrackingCycle {
        check(cycle.endedAt == null) { "Cycle ${cycle.cycleId} has already ended" }
        val endedAt = now.coerceAtLeast(cycle.startAt)
        return validate(
            cycle.copy(
                status = if (cycle.status == CycleStatus.REACHED) CycleStatus.REACHED else CycleStatus.ENDED,
                endedAt = endedAt,
                updatedAt = maxOf(now, cycle.updatedAt)
            )
        )
    }

    /** The `tracking_cycles` check constraints; throws [IllegalArgumentException] on a violation. */
    fun validate(cycle: TrackingCycle): TrackingCycle = cycle.apply {
        require(quotaBytes > 0) { "quota_bytes must be > 0" }
        require(status != CycleStatus.REACHED || reachedAt != null) { "A reached cycle needs reached_at" }
        require(reachedAt == null || reachedAt >= startAt) { "reached_at is before start_at" }
        require(endedAt == null || endedAt >= startAt) { "ended_at is before start_at" }
        require(status != CycleStatus.ENDED || endedAt != null) { "An ended cycle needs ended_at" }
    }
}
