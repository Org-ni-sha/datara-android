package com.capstone.datara.data.cycle

import com.capstone.datara.data.local.dao.TrackingCycleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.capstone.datara.data.local.entity.TrackingCycle as TrackingCycleEntity

/**
 * `tracking_cycles` in Room (D2), so cycles survive process death and reboots. Every write
 * clears `synced_at`, queueing the row for upload (C3).
 */
@Singleton
class RoomTrackingCycleRepository @Inject constructor(
    private val dao: TrackingCycleDao
) : TrackingCycleRepository {

    override fun observeLatest(userId: String, deviceId: String): Flow<TrackingCycle?> =
        dao.observeLatest(userId, deviceId).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun latest(userId: String, deviceId: String): TrackingCycle? =
        dao.latest(userId, deviceId)?.toDomain()

    override fun observeAll(userId: String): Flow<List<TrackingCycle>> =
        dao.observeAll(userId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun insert(cycle: TrackingCycle, endingPrevious: TrackingCycle?) =
        dao.replace(endingPrevious?.toEntity(), cycle.toEntity())

    override suspend fun update(cycle: TrackingCycle) = dao.update(cycle.toEntity())
}

internal fun TrackingCycleEntity.toDomain() = TrackingCycle(
    cycleId = cycleId,
    userId = userId,
    deviceId = deviceId,
    name = name,
    quotaBytes = quotaBytes,
    startAt = startAt,
    reachedAt = reachedAt,
    endedAt = endedAt,
    status = CycleStatus.fromValue(status),
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** `synced_at` is null: a written row waits for upload. */
internal fun TrackingCycle.toEntity() = TrackingCycleEntity(
    cycleId = cycleId,
    userId = userId,
    deviceId = deviceId,
    name = name,
    quotaBytes = quotaBytes,
    startAt = startAt,
    reachedAt = reachedAt,
    endedAt = endedAt,
    status = status.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncedAt = null
)
