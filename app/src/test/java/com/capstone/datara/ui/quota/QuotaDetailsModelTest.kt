package com.capstone.datara.ui.quota

import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleRules
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.CycleUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaDetailsModelTest {

    private val start = 1_793_000_000_000L
    private val cycle = CycleRules.create("c", "u", "d", null, 2_000_000_000, start)

    private fun tracked(usage: CycleUsage, ended: Boolean = false): QuotaDetailsModel {
        var c = CycleRules.applyUsage(cycle, usage, start + 1)
        if (ended) c = CycleRules.end(c, start + 2)
        return QuotaDetailsModel.fromCycleState(CycleState.Tracked(CycleProgress(c, usage)))
    }

    @Test fun noCycleAndLoadingHaveNoQuota() {
        assertFalse(QuotaDetailsModel.fromCycleState(CycleState.NoCycle).hasQuota)
        assertFalse(QuotaDetailsModel.fromCycleState(CycleState.SignedOut).hasQuota)
        val loading = QuotaDetailsModel.fromCycleState(CycleState.Loading)
        assertTrue(loading.isLoading)
        assertEquals("Loading", detailsStatus(loading))
        assertEquals("No active quota", detailsStatus(QuotaDetailsModel.fromCycleState(CycleState.NoCycle)))
    }

    @Test fun activeCycleShowsMeasuredUsage() {
        val model = tracked(CycleUsage(500_000_000, 0, null, start + 600_000, 0, 0))
        assertTrue(model.hasQuota)
        assertEquals(QuotaDetailsModel.DEFAULT_NAME, model.name)
        assertEquals(500_000_000L, model.usedBytes)
        assertEquals(1_500_000_000L, model.remainingBytes)
        assertEquals(0.25f, model.progress, 0.0001f)
        assertTrue(model.canEnd)
        assertEquals("Tracking", detailsStatus(model))
    }

    @Test fun beforeTheFirstReadingTheStatusSaysSo() {
        val model = tracked(CycleUsage.NONE)
        assertNull(model.measurement!!.measuredThroughMillis)
        assertEquals("Waiting for usage readings", detailsStatus(model))
    }

    @Test fun overQuotaClampsRemainingButNotProgress() {
        val model = tracked(CycleUsage(3_000_000_000, 0, start + 600_000, start + 600_000, 0, 0))
        assertTrue(model.isReached)
        assertEquals(start + 600_000, model.reachedAtMillis)
        assertEquals(0L, model.remainingBytes)
        assertEquals(1.5f, model.progress, 0.0001f)
        assertEquals("Quota reached", detailsStatus(model))
    }

    @Test fun reachedThenEndedShowsBoth() {
        val model = tracked(CycleUsage(3_000_000_000, 0, start + 600_000, start + 600_000, 0, 0), ended = true)
        assertTrue(model.isEnded)
        assertEquals("Quota reached · Cycle ended", detailsStatus(model))
    }

    @Test fun incompleteMeasurementIsCarried() {
        val model = tracked(CycleUsage(1, 0, null, start + 1, 120_000, 0))
        assertTrue(model.measurement!!.isIncomplete)
    }

    @Test fun noCycleHasNoQuotaAndCannotEnd() {
        val none = QuotaDetailsModel.fromCycleState(CycleState.NoCycle)
        assertFalse(none.hasQuota)
        assertFalse(none.canEnd)
        assertEquals("No active quota", detailsStatus(none))
    }

    @Test fun durationsAreRoundedToMinutes() {
        assertEquals("less than a minute", formatDuration(20_000))
        assertEquals("20 min", formatDuration(20 * 60_000L))
        assertEquals("2 h", formatDuration(120 * 60_000L))
        assertEquals("1 h 20 min", formatDuration(80 * 60_000L + 10_000))
    }
}
