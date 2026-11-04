package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.CycleOwner
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.cycle.TrackingCycleRepository
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock for cycle boundaries, injectable so tests control time. */
fun interface CycleClock {
    fun now(): Long
}

/** A cycle with the usage measured in it. */
data class CycleProgress(val cycle: TrackingCycle, val usage: CycleUsage) {
    val usedBytes: Long get() = usage.usedBytes
    val remainingBytes: Long get() = CycleMath.remainingBytes(cycle.quotaBytes, usage.usedBytes)
    val displayRemainingBytes: Long get() = CycleMath.displayRemainingBytes(cycle.quotaBytes, usage.usedBytes)
    val percentUsed: Double get() = CycleMath.percentUsed(cycle.quotaBytes, usage.usedBytes)
}

sealed interface CycleState {
    /** The session or the latest cycle is still being read. */
    data object Loading : CycleState

    /** No signed-in user, so there is no cycle owner. */
    data object SignedOut : CycleState

    /** Signed in, and this device has no cycle yet. */
    data object NoCycle : CycleState

    /** The latest cycle (active, reached or ended) and its measured usage. */
    data class Tracked(val progress: CycleProgress) : CycleState
}

/**
 * The D3 cycle use cases: observe progress, create, end, and restore the latest cycle when the
 * process starts (it reads it back from [TrackingCycleRepository], which is in memory until D2).
 *
 * Usage is recomputed whenever a 10-minute reading is stored or the cycle changes. When it
 * reaches the quota the active cycle is marked reached. That happens here, in the app process,
 * not in a screen, so it is recorded while only the telemetry service is running (the singleton
 * is created at app start by [com.capstone.datara.DataraApplication]).
 *
 * All writes go through one [Mutex], so ending a cycle and marking it reached cannot overwrite
 * each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class QuotaCycleManager @Inject constructor(
    private val cycles: TrackingCycleRepository,
    private val readings: UsageReadingRepository,
    private val owners: CycleOwnerProvider,
    private val clock: CycleClock,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val writeLock = Mutex()

    val state: StateFlow<CycleState> = owners.owner
        .flatMapLatest { owner ->
            if (owner == null) flowOf(CycleState.SignedOut)
            else cycles.observeLatest(owner.userId, owner.deviceId).flatMapLatest { cycle ->
                if (cycle == null) flowOf(CycleState.NoCycle)
                else progressOf(cycle).map { CycleState.Tracked(it) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, CycleState.Loading)

    init {
        scope.launch {
            state.collect { current ->
                if (current is CycleState.Tracked) recordReached(current.progress)
            }
        }
    }

    /**
     * Starts a new active cycle now. A current cycle (active, or reached and not ended) is ended
     * first at the same instant, so the device never has two active cycles.
     */
    suspend fun create(quotaBytes: Long, name: String?): Result<TrackingCycle> = runCatching {
        writeLock.withLock {
            val owner = requireOwner()
            val now = clock.now()
            cycles.latest(owner.userId, owner.deviceId)?.takeIf { it.isCurrent }?.let { previous ->
                cycles.update(settleAndEnd(previous, now))
            }
            CycleRules.create(UUID.randomUUID().toString(), owner.userId, owner.deviceId, name, quotaBytes, now)
                .also { cycles.insert(it) }
        }
    }

    /** Ends the current cycle now. Fails when there is none. */
    suspend fun end(): Result<TrackingCycle> = runCatching {
        writeLock.withLock {
            val owner = requireOwner()
            val current = cycles.latest(owner.userId, owner.deviceId)?.takeIf { it.isCurrent }
                ?: error("There is no cycle to end.")
            settleAndEnd(current, clock.now()).also { cycles.update(it) }
        }
    }

    private fun progressOf(cycle: TrackingCycle): Flow<CycleProgress> =
        readings.observeReadingsFrom(cycle.startAt).map { list ->
            CycleProgress(cycle, CycleMath.usage(list, cycle.startAt, cycle.endedAt, cycle.quotaBytes))
        }

    /** Records a reached quota on the stored row, if it is still the same active cycle. */
    private suspend fun recordReached(progress: CycleProgress) {
        if (progress.usage.reachedAt == null) return
        writeLock.withLock {
            val stored = cycles.latest(progress.cycle.userId, progress.cycle.deviceId)
                ?.takeIf { it.cycleId == progress.cycle.cycleId } ?: return
            val updated = CycleRules.applyUsage(stored, progress.usage, clock.now())
            if (updated !== stored) cycles.update(updated)
        }
    }

    /** A reached quota not yet recorded is recorded before ending, so the outcome is kept. */
    private suspend fun settleAndEnd(cycle: TrackingCycle, now: Long): TrackingCycle {
        val usage = CycleMath.usage(readings.observeReadingsFrom(cycle.startAt).first(), cycle.startAt, now, cycle.quotaBytes)
        return CycleRules.end(CycleRules.applyUsage(cycle, usage, now), now)
    }

    private suspend fun requireOwner(): CycleOwner =
        owners.owner.first() ?: error("Sign in to track a quota.")
}
