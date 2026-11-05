package com.capstone.datara.ui.quota

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.CycleUsage
import com.capstone.datara.forecast.ForecastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetForecastUiTest {

    private val hour = 3_600_000L
    private val now = 1_794_153_600_000L
    private val date: (Long) -> String = { "T$it" }

    private fun ready(reach: Long?, cycle: String = "c1", version: String = "v0-2026-11-07") =
        ForecastStatus.Ready(cycle, now, reach, 1_000_000, 50_000_000, "global", version)

    @Test
    fun `ready shows a relative time and the date`() {
        val ui = PetForecastUi.from(ready(now + 12 * hour), "c1", now, date)
        assertEquals("About 12 hours", ui.title)
        assertTrue(ui.body.startsWith("Estimated around T${now + 12 * hour}"))
        assertFalse(ui.body.contains("Test model"))
        assertTrue(ui.isAvailable)
    }

    @Test
    fun `placeholder model is labelled`() {
        assertTrue(PetForecastUi.from(ready(now + hour, version = "dev-synthetic-v1"), "c1", now, date).body.contains("Test model"))
    }

    @Test
    fun `beyond 30 days, collecting, unavailable and another cycle's forecast`() {
        assertEquals("Beyond 30 days", PetForecastUi.from(ready(null), "c1", now, date).title)
        val collecting = PetForecastUi.from(ForecastStatus.Collecting(2, 6), "c1", now, date)
        assertEquals("Collecting usage", collecting.title)
        assertTrue(collecting.body.contains("2 so far"))
        assertNull(collecting.reachAtMillis)
        assertEquals("Forecast unavailable", PetForecastUi.from(ForecastStatus.Unavailable("x"), "c1", now, date).title)
        assertEquals("Preparing your forecast", PetForecastUi.from(ready(now + hour, cycle = "old"), "c1", now, date).title)
    }

    @Test
    fun `relative wording`() {
        assertEquals("Any moment now", PetForecastUi.relative(0))
        assertEquals("Less than an hour", PetForecastUi.relative(hour - 1))
        assertEquals("About 1 hour", PetForecastUi.relative(hour))
        assertEquals("About 47 hours", PetForecastUi.relative(47 * hour))
        assertEquals("About 3 days", PetForecastUi.relative(72 * hour))
    }

    @Test
    fun `details carry the forecast only while the cycle is active`() {
        fun tracked(status: CycleStatus, ended: Long? = null) = CycleState.Tracked(CycleProgress(
            TrackingCycle("c1", "u", "d", null, 100, 0, if (status == CycleStatus.REACHED) 10 else null, ended, status, 0, 0),
            CycleUsage(10, 10, null, null, 0, 0)))
        val active = QuotaCycleViewModel.detailsWithForecast(tracked(CycleStatus.ACTIVE), ready(now + 2 * hour), now)
        assertEquals("About 2 hours", active.forecast!!.title)
        assertNull(QuotaCycleViewModel.detailsWithForecast(tracked(CycleStatus.REACHED), ready(now), now).forecast)
        assertNull(QuotaCycleViewModel.detailsWithForecast(tracked(CycleStatus.ENDED, 5), ready(now), now).forecast)
        assertNull(QuotaCycleViewModel.detailsWithForecast(CycleState.NoCycle, ready(now), now).forecast)
    }
}
