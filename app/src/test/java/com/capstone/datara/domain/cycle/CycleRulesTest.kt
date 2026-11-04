package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CycleRulesTest {

    private val start = 1_793_000_000_000L

    private fun active(quota: Long = 1_000) = CycleRules.create("c1", "u1", "d1", "  Everyday  ", quota, start)

    private fun reachedUsage(at: Long) = CycleUsage(1_000, 0, at, at, 0, 0)

    @Test fun createStartsAnActiveCycleNow() {
        val cycle = active()
        assertEquals(CycleStatus.ACTIVE, cycle.status)
        assertEquals(start, cycle.startAt)
        assertEquals(start, cycle.createdAt)
        assertEquals(start, cycle.updatedAt)
        assertEquals("Everyday", cycle.name)
        assertNull(cycle.reachedAt)
        assertNull(cycle.endedAt)
    }

    @Test fun blankNameIsStoredAsNull() {
        assertNull(CycleRules.create("c", "u", "d", "   ", 1, start).name)
        assertNull(CycleRules.create("c", "u", "d", null, 1, start).name)
    }

    @Test fun quotaMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { active(quota = 0) }
        assertThrows(IllegalArgumentException::class.java) { active(quota = -5) }
    }

    @Test fun activeBecomesReachedWhenUsageReachesTheQuota() {
        val reached = CycleRules.applyUsage(active(), reachedUsage(start + 600_000), start + 700_000)
        assertEquals(CycleStatus.REACHED, reached.status)
        assertEquals(start + 600_000, reached.reachedAt)
        assertEquals(start + 700_000, reached.updatedAt)
        assertNull(reached.endedAt)
    }

    @Test fun usageBelowTheQuotaChangesNothing() {
        val cycle = active()
        assertSame(cycle, CycleRules.applyUsage(cycle, CycleUsage(999, 0, null, start + 1, 0, 0), start + 2))
    }

    @Test fun reachedIsRecordedOnlyOnce() {
        val reached = CycleRules.applyUsage(active(), reachedUsage(start + 600_000), start + 700_000)
        assertSame(reached, CycleRules.applyUsage(reached, reachedUsage(start + 1_200_000), start + 1_300_000))
    }

    @Test fun anEndedCycleNeverBecomesReached() {
        val ended = CycleRules.end(active(), start + 100)
        assertSame(ended, CycleRules.applyUsage(ended, reachedUsage(start + 50), start + 200))
    }

    @Test fun userEndingAnActiveCycleMarksItEnded() {
        val ended = CycleRules.end(active(), start + 5_000)
        assertEquals(CycleStatus.ENDED, ended.status)
        assertEquals(start + 5_000, ended.endedAt)
        assertNull(ended.reachedAt)
    }

    @Test fun userEndingAReachedCycleKeepsReachedAndSetsEndedAt() {
        val reached = CycleRules.applyUsage(active(), reachedUsage(start + 600_000), start + 600_000)
        val ended = CycleRules.end(reached, start + 900_000)
        assertEquals(CycleStatus.REACHED, ended.status)
        assertEquals(start + 600_000, ended.reachedAt)
        assertEquals(start + 900_000, ended.endedAt)
    }

    @Test fun endingTwiceIsRejected() {
        val ended = CycleRules.end(active(), start + 1)
        assertThrows(IllegalStateException::class.java) { CycleRules.end(ended, start + 2) }
    }

    @Test fun endedAtIsNeverBeforeStartAtEvenIfTheClockWentBack() {
        val ended = CycleRules.end(active(), start - 60_000)
        assertEquals(start, ended.endedAt)
        assertEquals(start, ended.updatedAt)
    }

    @Test fun checkConstraintsAreEnforced() {
        val base = active()
        assertThrows(IllegalArgumentException::class.java) { CycleRules.validate(base.copy(status = CycleStatus.REACHED)) }
        assertThrows(IllegalArgumentException::class.java) { CycleRules.validate(base.copy(reachedAt = start - 1)) }
        assertThrows(IllegalArgumentException::class.java) { CycleRules.validate(base.copy(endedAt = start - 1)) }
        assertThrows(IllegalArgumentException::class.java) { CycleRules.validate(base.copy(status = CycleStatus.ENDED)) }
        CycleRules.validate(base.copy(status = CycleStatus.REACHED, reachedAt = start, endedAt = start))
    }

    @Test fun statusValuesMatchTheSchema() {
        assertEquals(listOf("active", "reached", "ended"), CycleStatus.entries.map { it.value })
        assertEquals(CycleStatus.REACHED, CycleStatus.fromValue("reached"))
        assertThrows(IllegalArgumentException::class.java) { CycleStatus.fromValue("expired") }
    }

    @Test fun currentMeansNotEndedByTheUser() {
        val reached: TrackingCycle = CycleRules.applyUsage(active(), reachedUsage(start + 10), start + 10)
        assertEquals(true, reached.isCurrent)
        assertEquals(false, CycleRules.end(reached, start + 20).isCurrent)
    }
}
