package com.capstone.datara.alerts

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.NotificationType.FORECAST_24H
import com.capstone.datara.data.local.entity.NotificationType.FORECAST_3H
import com.capstone.datara.data.local.entity.NotificationType.MILESTONE_75
import com.capstone.datara.data.local.entity.NotificationType.MILESTONE_90
import com.capstone.datara.data.local.entity.NotificationType.QUOTA_REACHED
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.CycleUsage
import com.capstone.datara.forecast.ForecastStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertRulesTest {

    private val hour = AlertRules.HOUR_MS
    private val now = 1_794_200_000_000L
    private val quota = 1_000_000_000L // 1 GB
    private val on = AlertPreferences()

    private fun state(used: Long, status: CycleStatus = CycleStatus.ACTIVE, endedAt: Long? = null, id: String = "c1"): CycleState {
        val reachedAt = if (status == CycleStatus.REACHED) now - hour else null
        val cycle = TrackingCycle(id, "u1", "d1", "Everyday", quota, now - 48 * hour, reachedAt, endedAt, status,
            now - 48 * hour, now - 48 * hour)
        return CycleState.Tracked(CycleProgress(cycle, CycleUsage(used, used, reachedAt, now, 0, 0)))
    }

    private fun ready(reachIn: Long?, cycleId: String = "c1", madeAgo: Long = 10 * 60_000L) = ForecastStatus.Ready(
        cycleId = cycleId, predictedAt = now - madeAgo, reachAt = reachIn?.let { now + it }, nextHourBytes = 1,
        remainingBytes = 1, activeModel = ActiveModel.GLOBAL, modelVersion = "v0"
    )

    private fun due(state: CycleState, forecast: ForecastStatus = ForecastStatus.Pending,
        prefs: AlertPreferences = on, sent: Set<String> = emptySet()) = AlertRules.due(state, forecast, prefs, sent, now)

    @Test
    fun `milestones fire at 75, 90 and 100 percent of measured usage`() {
        assertEquals(emptyList<String>(), due(state(749_999_999)))
        assertEquals(listOf(MILESTONE_75), due(state(750_000_000)))
        assertEquals(listOf(MILESTONE_75), due(state(899_999_999)))
        assertEquals(listOf(MILESTONE_90), due(state(900_000_000)))
        assertEquals(listOf(QUOTA_REACHED), due(state(quota)))
    }

    @Test
    fun `a jump past several levels sends only the highest`() {
        assertEquals(listOf(QUOTA_REACHED), due(state(1_200_000_000)))
        assertEquals(listOf(MILESTONE_90), due(state(950_000_000), sent = emptySet()))
    }

    @Test
    fun `a type already sent, or a higher level of its kind, is not asked for again`() {
        assertEquals(emptyList<String>(), due(state(800_000_000), sent = setOf(MILESTONE_75)))
        assertEquals(emptyList<String>(), due(state(800_000_000), sent = setOf(MILESTONE_90)))
        assertEquals(listOf(MILESTONE_90), due(state(910_000_000), sent = setOf(MILESTONE_75)))
        // the PET moved away after the 3-hour alert: no 24-hour alert afterwards
        assertEquals(emptyList<String>(), due(state(0), ready(10 * hour), sent = setOf(FORECAST_3H)))
        assertEquals(emptyList<String>(), due(state(0), ready(2 * hour), sent = setOf(FORECAST_3H)))
    }

    @Test
    fun `forecast alerts fire within 24 hours and within 3 hours of the PET`() {
        assertEquals(emptyList<String>(), due(state(0), ready(25 * hour)))
        assertEquals(listOf(FORECAST_24H), due(state(0), ready(24 * hour)))
        assertEquals(listOf(FORECAST_24H), due(state(0), ready(4 * hour)))
        assertEquals(listOf(FORECAST_3H), due(state(0), ready(3 * hour)))
        assertEquals(listOf(FORECAST_3H), due(state(0), ready(-5 * 60_000L))) // PET already passed, quota not yet
    }

    @Test
    fun `a milestone and a forecast alert can be due together`() {
        assertEquals(listOf(MILESTONE_90, FORECAST_3H), due(state(950_000_000), ready(hour)))
    }

    @Test
    fun `no forecast alert while collecting, unavailable, pending or beyond 30 days`() {
        assertEquals(emptyList<String>(), due(state(0), ForecastStatus.Collecting(3, 6)))
        assertEquals(emptyList<String>(), due(state(0), ForecastStatus.Unavailable("no model")))
        assertEquals(emptyList<String>(), due(state(0), ForecastStatus.Pending))
        assertEquals(emptyList<String>(), due(state(0), ForecastStatus.NoActiveCycle))
        assertEquals(emptyList<String>(), due(state(0), ready(reachIn = null)))
    }

    @Test
    fun `a forecast for another cycle or a stale forecast is ignored`() {
        assertEquals(emptyList<String>(), due(state(0), ready(hour, cycleId = "old")))
        assertEquals(emptyList<String>(), due(state(0), ready(hour, madeAgo = 3 * hour)))
        assertEquals(listOf(FORECAST_3H), due(state(0), ready(hour, madeAgo = 2 * hour)))
        assertEquals(emptyList<String>(), due(state(0), ready(hour, madeAgo = -hour))) // from the future (clock change)
    }

    @Test
    fun `preferences turn each kind off on its own`() {
        val usage = state(950_000_000)
        val forecast = ready(hour)
        assertEquals(listOf(FORECAST_3H), due(usage, forecast, AlertPreferences(milestones = false)))
        assertEquals(listOf(MILESTONE_90), due(usage, forecast, AlertPreferences(forecasts = false)))
        assertEquals(emptyList<String>(), due(usage, forecast, AlertPreferences(milestones = false, forecasts = false)))
    }

    @Test
    fun `an ended cycle gets nothing, a reached one only its quota-reached alert`() {
        assertEquals(emptyList<String>(), due(state(quota, CycleStatus.ENDED, endedAt = now), ready(hour)))
        assertEquals(emptyList<String>(), due(state(quota, CycleStatus.REACHED, endedAt = now), ready(hour)))
        assertEquals(emptyList<String>(), due(state(800_000_000, CycleStatus.ENDED, endedAt = now)))
        assertEquals(listOf(QUOTA_REACHED), due(state(quota, CycleStatus.REACHED), ready(hour)))
    }

    @Test
    fun `no cycle or signed out sends nothing`() {
        assertEquals(emptyList<String>(), due(CycleState.NoCycle, ready(hour)))
        assertEquals(emptyList<String>(), due(CycleState.SignedOut))
        assertEquals(emptyList<String>(), due(CycleState.Loading))
    }
}
