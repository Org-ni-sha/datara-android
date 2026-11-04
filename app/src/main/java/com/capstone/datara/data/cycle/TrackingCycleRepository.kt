package com.capstone.datara.data.cycle

import kotlinx.coroutines.flow.Flow

/**
 * Storage seam for `tracking_cycles`. The app binds [RoomTrackingCycleRepository];
 * [InMemoryTrackingCycleRepository] is the JVM tests' reference implementation.
 * - reads that the UI follows are `Flow`s, one-shot reads and writes are `suspend`
 *
 * Callers ([com.capstone.datara.domain.cycle.QuotaCycleManager]) serialize writes and enforce the
 * transition rules; the repository only stores rows. Room additionally rejects a second active
 * cycle per device (`DataraDatabase.ONE_ACTIVE_CYCLE_TRIGGERS`).
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

    /**
     * Inserts [cycle]. When [endingPrevious] is given (the device's current cycle, already ended
     * or reached), it is updated in the same transaction, before the insert.
     */
    suspend fun insert(cycle: TrackingCycle, endingPrevious: TrackingCycle? = null)

    /** Replaces the row with the same [TrackingCycle.cycleId]. */
    suspend fun update(cycle: TrackingCycle)
}
