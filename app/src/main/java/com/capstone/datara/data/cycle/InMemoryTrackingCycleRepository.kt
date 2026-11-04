package com.capstone.datara.data.cycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [TrackingCycleRepository] for JVM tests (the app binds [RoomTrackingCycleRepository]).
 * Rejects a second active cycle per device, like Room's trigger and Postgres' partial index.
 */
@Singleton
class InMemoryTrackingCycleRepository @Inject constructor() : TrackingCycleRepository {

    private val cycles = MutableStateFlow<List<TrackingCycle>>(emptyList())

    override fun observeLatest(userId: String, deviceId: String): Flow<TrackingCycle?> =
        cycles.map { all -> all.latestFor(userId, deviceId) }.distinctUntilChanged()

    override suspend fun latest(userId: String, deviceId: String): TrackingCycle? =
        cycles.value.latestFor(userId, deviceId)

    override fun observeAll(userId: String): Flow<List<TrackingCycle>> =
        cycles.map { all -> all.filter { it.userId == userId }.sortedByDescending { it.startAt } }
            .distinctUntilChanged()

    override suspend fun insert(cycle: TrackingCycle, endingPrevious: TrackingCycle?) = cycles.update { before ->
        val all = endingPrevious?.let { previous -> before.map { if (it.cycleId == previous.cycleId) previous else it } } ?: before
        require(all.none { it.cycleId == cycle.cycleId }) { "Duplicate cycle_id ${cycle.cycleId}" }
        check(cycle.status != CycleStatus.ACTIVE || all.none { it.deviceId == cycle.deviceId && it.status == CycleStatus.ACTIVE }) {
            "tracking_cycles_one_active_per_device"
        }
        all + cycle
    }

    override suspend fun update(cycle: TrackingCycle) = cycles.update { all ->
        require(all.any { it.cycleId == cycle.cycleId }) { "Unknown cycle_id ${cycle.cycleId}" }
        all.map { if (it.cycleId == cycle.cycleId) cycle else it }
    }

    private fun List<TrackingCycle>.latestFor(userId: String, deviceId: String) =
        filter { it.userId == userId && it.deviceId == deviceId }.maxByOrNull { it.startAt }
}
