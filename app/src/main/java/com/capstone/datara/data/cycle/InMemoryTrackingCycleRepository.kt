package com.capstone.datara.data.cycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime stand-in for D2's Room table. Cycles survive screen changes and activity
 * recreation, but not the process dying: after that the device has no cycle until the user
 * creates one again.
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

    override suspend fun insert(cycle: TrackingCycle) = cycles.update { all ->
        require(all.none { it.cycleId == cycle.cycleId }) { "Duplicate cycle_id ${cycle.cycleId}" }
        all + cycle
    }

    override suspend fun update(cycle: TrackingCycle) = cycles.update { all ->
        require(all.any { it.cycleId == cycle.cycleId }) { "Unknown cycle_id ${cycle.cycleId}" }
        all.map { if (it.cycleId == cycle.cycleId) cycle else it }
    }

    private fun List<TrackingCycle>.latestFor(userId: String, deviceId: String) =
        filter { it.userId == userId && it.deviceId == deviceId }.maxByOrNull { it.startAt }
}
