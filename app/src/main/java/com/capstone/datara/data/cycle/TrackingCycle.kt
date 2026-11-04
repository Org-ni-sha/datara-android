package com.capstone.datara.data.cycle

/**
 * Lifecycle of a [TrackingCycle], stored as the `tracking_cycles.status` text values.
 *
 * - [ACTIVE]: tracking against the quota.
 * - [REACHED]: measured usage reached `quota_bytes`; `reached_at` is set. Ending a reached
 *   cycle keeps this status and only sets `ended_at`, so it still counts as a known outcome.
 * - [ENDED]: the user ended the cycle before it reached the quota.
 */
enum class CycleStatus(val value: String) {
    ACTIVE("active"),
    REACHED("reached"),
    ENDED("ended");

    companion object {
        fun fromValue(value: String): CycleStatus =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown cycle status: $value")
    }
}

/**
 * One quota the user set, and how it ended. Field for field with `tracking_cycles` in
 * docs/erd-draft.md and migration 20261104000001: times are epoch millis (timestamptz in
 * Postgres), amounts are bytes, ids are client-generated UUIDs so sync retries upsert.
 *
 * Constraints (mirrored by [CycleRules.validate]):
 * - `quota_bytes > 0`
 * - `status <> 'reached' or reached_at is not null`
 * - `reached_at` and `ended_at`, when set, are `>= start_at`
 * - at most one [CycleStatus.ACTIVE] cycle per device
 */
data class TrackingCycle(
    val cycleId: String,
    val userId: String,
    val deviceId: String,
    /** Optional name the user typed; null when left blank. */
    val name: String?,
    val quotaBytes: Long,
    val startAt: Long,
    /** End of the reading in which measured usage reached [quotaBytes]. */
    val reachedAt: Long?,
    /** When the user ended (or replaced) the cycle. */
    val endedAt: Long?,
    val status: CycleStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** The cycle the device is on: not ended by the user, whether still active or already reached. */
    val isCurrent: Boolean get() = endedAt == null
}
