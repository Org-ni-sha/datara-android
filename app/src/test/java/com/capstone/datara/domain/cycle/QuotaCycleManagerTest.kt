package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.CycleOwner
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.InMemoryTrackingCycleRepository
import com.capstone.datara.data.cycle.TelemetryUsageReadingRepository
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.InMemoryTelemetryRepository
import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.MeasurementStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The use cases against the in-memory repositories: what D2's Room versions must also satisfy. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaCycleManagerTest {

    private val start = 1_793_000_000_000L
    private val min = 60_000L
    private val owner = CycleOwner("user-1", "device-1")

    private val owners = object : CycleOwnerProvider {
        override val owner = MutableStateFlow<CycleOwner?>(this@QuotaCycleManagerTest.owner)
    }
    private val cycles = InMemoryTrackingCycleRepository()
    private val telemetry = InMemoryTelemetryRepository()
    private val readings = TelemetryUsageReadingRepository(telemetry)
    private var now = start

    private fun manager(scope: CoroutineScope) = QuotaCycleManager(cycles, readings, owners, { now }, scope)

    private suspend fun record(fromMin: Long, toMin: Long, bytes: Long, status: String = MeasurementStatus.OK) {
        val until = start + toMin * min
        telemetry.commitDurable(
            listOf(DurableUsageReading(since = start + fromMin * min, until = until, bytesRx = bytes, bytesTx = 0, status = status, schemaVersion = 3)),
            DurableCheckpoint(anchor = 0, rx = 0, tx = 0, takenAt = until)
        )
    }

    private fun TestScope.tracked(manager: QuotaCycleManager): CycleProgress {
        runCurrent()
        val state = manager.state.value
        assertTrue("Expected a tracked cycle, got $state", state is CycleState.Tracked)
        return (state as CycleState.Tracked).progress
    }

    @Test fun signedOutHasNoCycleOwner() = runTest(UnconfinedTestDispatcher()) {
        owners.owner.value = null
        val manager = manager(backgroundScope)
        runCurrent()
        assertEquals(CycleState.SignedOut, manager.state.value)
        assertTrue(manager.create(1_000, null).isFailure)
        owners.owner.value = owner
        runCurrent()
        assertEquals(CycleState.NoCycle, manager.state.value)
    }

    @Test fun createdCycleRecomputesUsageAsReadingsArrive() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        record(-10, 0, 9_999) // before the cycle
        manager.create(10_000, "Everyday").getOrThrow()
        assertEquals(0L, tracked(manager).usedBytes)
        assertNull(tracked(manager).usage.measuredThrough)

        record(0, 10, 2_500)
        val progress = tracked(manager)
        assertEquals(2_500L, progress.usedBytes)
        assertEquals(7_500L, progress.remainingBytes)
        assertEquals(25.0, progress.percentUsed, 0.0)
        assertEquals(start + 10 * min, progress.usage.measuredThrough)
        assertEquals(CycleStatus.ACTIVE, progress.cycle.status)
    }

    @Test fun reachingTheQuotaIsRecordedOnTheStoredCycle() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        manager.create(10_000, null).getOrThrow()
        record(0, 10, 6_000)
        now = start + 25 * min
        record(10, 20, 6_000)
        runCurrent()

        val stored = cycles.latest(owner.userId, owner.deviceId)!!
        assertEquals(CycleStatus.REACHED, stored.status)
        assertEquals(start + 20 * min, stored.reachedAt)
        assertEquals(start + 25 * min, stored.updatedAt)
        val progress = tracked(manager)
        assertEquals(CycleStatus.REACHED, progress.cycle.status)
        assertEquals(0L, progress.displayRemainingBytes)
        assertEquals(-2_000L, progress.remainingBytes)
    }

    @Test fun endingStopsCountingAtEndedAt() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        manager.create(10_000, null).getOrThrow()
        record(0, 10, 1_000)
        now = start + 15 * min
        val ended = manager.end().getOrThrow()
        assertEquals(CycleStatus.ENDED, ended.status)
        assertEquals(start + 15 * min, ended.endedAt)

        // The reading that covers the end arrives afterwards and is split at ended_at
        record(10, 20, 1_000)
        record(20, 30, 50_000)
        val progress = tracked(manager)
        assertEquals(1_500L, progress.usedBytes)
        assertEquals(CycleStatus.ENDED, progress.cycle.status)
        assertTrue(manager.end().isFailure)
    }

    @Test fun endingAReachedCycleKeepsItReached() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        manager.create(1_000, null).getOrThrow()
        record(0, 10, 1_000)
        now = start + 30 * min
        val ended = manager.end().getOrThrow()
        assertEquals(CycleStatus.REACHED, ended.status)
        assertEquals(start + 10 * min, ended.reachedAt)
        assertEquals(start + 30 * min, ended.endedAt)
    }

    @Test fun endingRecordsAReachedQuotaThatWasNotStoredYet() = runTest(UnconfinedTestDispatcher()) {
        // A cancelled scope, so nothing collects in the background: end() must settle "reached"
        val idle = QuotaCycleManager(cycles, readings, owners, { now }, CoroutineScope(Job().apply { cancel() }))
        idle.create(1_000, null).getOrThrow()
        record(0, 10, 1_500)
        now = start + 20 * min
        val ended = idle.end().getOrThrow()
        assertEquals(CycleStatus.REACHED, ended.status)
        assertEquals(start + 10 * min, ended.reachedAt)
    }

    @Test fun creatingANewCycleEndsTheCurrentOneSoOnlyOneIsActive() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        val first = manager.create(10_000, "First").getOrThrow()
        now = start + 60 * min
        val second = manager.create(5_000, "Second").getOrThrow()

        val all = cycles.observeAll(owner.userId).first()
        assertEquals(listOf(second.cycleId, first.cycleId), all.map { it.cycleId })
        assertEquals(1, all.count { it.status == CycleStatus.ACTIVE })
        val previous = all.last()
        assertEquals(CycleStatus.ENDED, previous.status)
        assertEquals(second.startAt, previous.endedAt)
        assertEquals("Second", tracked(manager).cycle.name)
    }

    @Test fun replacingAReachedCycleKeepsItsOutcome() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        val first = manager.create(1_000, null).getOrThrow()
        record(0, 10, 2_000)
        now = start + 40 * min
        manager.create(5_000, null).getOrThrow()
        val previous = cycles.observeAll(owner.userId).first().single { it.cycleId == first.cycleId }
        assertEquals(CycleStatus.REACHED, previous.status)
        assertEquals(start + 10 * min, previous.reachedAt)
        assertEquals(start + 40 * min, previous.endedAt)
    }

    @Test fun activeCycleIsRestoredWhenTheAppStartsAgain() = runTest(UnconfinedTestDispatcher()) {
        manager(backgroundScope).create(10_000, "Kept").getOrThrow()
        record(0, 10, 400)
        // A new manager over the same storage, as after a process restart once D2 persists it
        val restarted = manager(backgroundScope)
        val progress = tracked(restarted)
        assertEquals("Kept", progress.cycle.name)
        assertEquals(400L, progress.usedBytes)
    }

    @Test fun anotherUserOnTheDeviceDoesNotSeeTheCycle() = runTest(UnconfinedTestDispatcher()) {
        val manager = manager(backgroundScope)
        manager.create(10_000, null).getOrThrow()
        owners.owner.value = CycleOwner("user-2", owner.deviceId)
        runCurrent()
        assertEquals(CycleState.NoCycle, manager.state.value)
        assertTrue(manager.end().isFailure)
    }
}
