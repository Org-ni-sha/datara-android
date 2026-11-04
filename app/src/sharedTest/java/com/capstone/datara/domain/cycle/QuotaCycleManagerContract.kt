package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.CycleOwner
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycleRepository
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.TelemetryRepository
import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.MeasurementStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The D3 use cases against a storage implementation: the contract every one must satisfy. Run on
 * the JVM against the in-memory repositories (QuotaCycleManagerTest) and on a device against Room
 * (RoomQuotaCycleManagerTest). Waits are real-time, since Room emits from its own query thread.
 */
abstract class QuotaCycleManagerContract {

    /** Cycles, telemetry and the readings view over that same telemetry. */
    class Storage(val cycles: TrackingCycleRepository, val telemetry: TelemetryRepository, val readings: UsageReadingRepository)

    /** New, empty storage for each test. */
    protected abstract fun createStorage(): Storage

    /** Called after the managers' scopes are cancelled, so nothing reads a closed database. */
    protected open fun closeStorage() {}

    private val start = 1_793_000_000_000L
    private val min = 60_000L
    private val owner = CycleOwner("user-1", "device-1")

    private val owners = object : CycleOwnerProvider {
        override val owner = MutableStateFlow<CycleOwner?>(this@QuotaCycleManagerContract.owner)
    }
    private var now = start
    private lateinit var storage: Storage
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUpStorage() {
        storage = createStorage()
    }

    @After
    fun tearDownStorage() {
        scopes.forEach { it.cancel() }
        closeStorage()
    }

    private fun manager() = QuotaCycleManager(storage.cycles, storage.readings, owners, { now },
        CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it })

    /** A manager with nothing collecting in the background. */
    private fun idleManager() = QuotaCycleManager(storage.cycles, storage.readings, owners, { now },
        CoroutineScope(Job().apply { cancel() }))

    private suspend fun record(fromMin: Long, toMin: Long, bytes: Long, status: String = MeasurementStatus.OK) {
        val until = start + toMin * min
        storage.telemetry.commitDurable(
            listOf(DurableUsageReading(since = start + fromMin * min, until = until, bytesRx = bytes, bytesTx = 0, status = status, schemaVersion = 3)),
            DurableCheckpoint(anchor = 0, rx = 0, tx = 0, takenAt = until)
        )
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = withTimeout(5_000) { first(predicate) }

    /** The tracked progress once [predicate] holds. */
    private suspend fun QuotaCycleManager.progress(predicate: (CycleProgress) -> Boolean = { true }): CycleProgress =
        (state.await { it is CycleState.Tracked && predicate(it.progress) } as CycleState.Tracked).progress

    private suspend fun stored() = storage.cycles.latest(owner.userId, owner.deviceId)!!

    @Test
    fun signedOutHasNoCycleOwner() = runBlocking {
        owners.owner.value = null
        val manager = manager()
        manager.state.await { it == CycleState.SignedOut }
        assertTrue(manager.create(1_000, null).isFailure)
        owners.owner.value = owner
        manager.state.await { it == CycleState.NoCycle }
        Unit
    }

    @Test
    fun createdCycleRecomputesUsageAsReadingsArrive() = runBlocking {
        val manager = manager()
        record(-10, 0, 9_999) // before the cycle
        manager.create(10_000, "Everyday").getOrThrow()
        val empty = manager.progress()
        assertEquals(0L, empty.usedBytes)
        assertEquals(null, empty.usage.measuredThrough)

        record(0, 10, 2_500)
        val progress = manager.progress { it.usedBytes > 0 }
        assertEquals(2_500L, progress.usedBytes)
        assertEquals(7_500L, progress.remainingBytes)
        assertEquals(25.0, progress.percentUsed, 0.0)
        assertEquals(start + 10 * min, progress.usage.measuredThrough)
        assertEquals(CycleStatus.ACTIVE, progress.cycle.status)
    }

    @Test
    fun readingThatStraddlesTheStartIsSplit() = runBlocking {
        val manager = manager()
        now = start + 5 * min
        manager.create(10_000, null).getOrThrow()
        record(0, 10, 1_000)
        assertEquals(500L, manager.progress { it.usedBytes > 0 }.usedBytes)
    }

    @Test
    fun reachingTheQuotaIsRecordedOnTheStoredCycle() = runBlocking {
        val manager = manager()
        manager.create(10_000, null).getOrThrow()
        record(0, 10, 6_000)
        now = start + 25 * min
        record(10, 20, 6_000)

        storage.cycles.observeLatest(owner.userId, owner.deviceId).await { it?.status == CycleStatus.REACHED }
        val stored = stored()
        assertEquals(start + 20 * min, stored.reachedAt)
        assertEquals(start + 25 * min, stored.updatedAt)
        val progress = manager.progress { it.cycle.status == CycleStatus.REACHED }
        assertEquals(0L, progress.displayRemainingBytes)
        assertEquals(-2_000L, progress.remainingBytes)
    }

    @Test
    fun endingStopsCountingAtEndedAt() = runBlocking {
        val manager = manager()
        manager.create(10_000, null).getOrThrow()
        record(0, 10, 1_000)
        now = start + 15 * min
        val ended = manager.end().getOrThrow()
        assertEquals(CycleStatus.ENDED, ended.status)
        assertEquals(start + 15 * min, ended.endedAt)

        // The reading that covers the end arrives afterwards and is split at ended_at
        record(10, 20, 1_000)
        record(20, 30, 50_000)
        val progress = manager.progress { it.cycle.status == CycleStatus.ENDED && it.usage.measuredThrough == start + 15 * min }
        assertEquals(1_500L, progress.usedBytes)
        assertTrue(manager.end().isFailure)
    }

    @Test
    fun endingAReachedCycleKeepsItReached() = runBlocking {
        val manager = idleManager()
        manager.create(1_000, null).getOrThrow()
        record(0, 10, 1_000)
        now = start + 30 * min
        val ended = manager.end().getOrThrow()
        assertEquals(CycleStatus.REACHED, ended.status)
        assertEquals(start + 10 * min, ended.reachedAt)
        assertEquals(start + 30 * min, ended.endedAt)
        assertEquals(ended, stored())
    }

    @Test
    fun creatingANewCycleEndsTheCurrentOneSoOnlyOneIsActive() = runBlocking {
        val manager = idleManager()
        val first = manager.create(10_000, "First").getOrThrow()
        now = start + 60 * min
        val second = manager.create(5_000, "Second").getOrThrow()

        val all = storage.cycles.observeAll(owner.userId).first()
        assertEquals(listOf(second.cycleId, first.cycleId), all.map { it.cycleId })
        assertEquals(1, all.count { it.status == CycleStatus.ACTIVE })
        val previous = all.last()
        assertEquals(CycleStatus.ENDED, previous.status)
        assertEquals(second.startAt, previous.endedAt)
        assertEquals("Second", stored().name)
    }

    @Test
    fun replacingAReachedCycleKeepsItsOutcome() = runBlocking {
        val manager = idleManager()
        val first = manager.create(1_000, null).getOrThrow()
        record(0, 10, 2_000)
        now = start + 40 * min
        manager.create(5_000, null).getOrThrow()
        val previous = storage.cycles.observeAll(owner.userId).first().single { it.cycleId == first.cycleId }
        assertEquals(CycleStatus.REACHED, previous.status)
        assertEquals(start + 10 * min, previous.reachedAt)
        assertEquals(start + 40 * min, previous.endedAt)
    }

    @Test
    fun latestCycleIsRestoredByANewManager() = runBlocking {
        idleManager().create(10_000, "Kept").getOrThrow()
        record(0, 10, 400)
        // A new manager over the same storage, as after a process restart
        val progress = manager().progress { it.usedBytes > 0 }
        assertEquals("Kept", progress.cycle.name)
        assertEquals(400L, progress.usedBytes)
    }

    @Test
    fun anotherUserOnTheDeviceDoesNotSeeTheCycle() = runBlocking {
        val manager = manager()
        manager.create(10_000, null).getOrThrow()
        manager.progress()
        owners.owner.value = CycleOwner("user-2", "device-2")
        manager.state.await { it == CycleState.NoCycle }
        assertTrue(manager.end().isFailure)
    }
}
