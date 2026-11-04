package com.capstone.datara.data.cycle

import kotlinx.coroutines.flow.Flow

/**
 * Storage seam for `tracking_cycles`. Bound to [InMemoryTrackingCycleRepository] until D2 adds
 * Room; the shape is meant to map one-to-one onto a DAO:
 * - reads that the UI follows are `Flow`s (Room `@Query` returning `Flow`)
 * - one-shot reads and writes are `suspend` (`@Query`, `@Insert`, `@Update`)
 *
 * Callers ([com.capstone.datara.domain.cycle.QuotaCycleManager]) serialize writes and enforce the
 * transition rules; the repository only stores rows. The Room version should still add the
 * partial unique index `(device_id) where status = 'active'` from the migration.
 */
interface TrackingCycleRepository {

    /**
     * The user's latest cycle on this device (highest `start_at`), whatever its status, or null.
     * It is the device's current cycle while `ended_at is null`; once ended it stays the latest
     * (shown as "Cycle ended") until a new one starts. Emits again on every change.
     * DAO: `where user_id = :userId and device_id = :deviceId order by start_at desc limit 1`.
     */
    fun observeLatest(userId: String, deviceId: String): Flow<TrackingCycle?>

    /** One-shot version of [observeLatest]. */
    suspend fun latest(userId: String, deviceId: String): TrackingCycle?

    /** All of the user's cycles, newest `start_at` first (History, D7). */
    fun observeAll(userId: String): Flow<List<TrackingCycle>>

    suspend fun insert(cycle: TrackingCycle)

    /** Replaces the row with the same [TrackingCycle.cycleId]. */
    suspend fun update(cycle: TrackingCycle)
}
