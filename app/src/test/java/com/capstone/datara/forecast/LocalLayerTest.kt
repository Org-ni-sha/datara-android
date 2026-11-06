package com.capstone.datara.forecast

import com.capstone.datara.data.local.entity.ActiveModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLayerTest {

    private val noScores = LayerScores(0, null, null)

    private fun pending(hour: Long, global: Double, local: Double? = null, localHour: Int = 20) =
        LocalLayerParameters(pendingHour = hour, pendingLocalHour = localHour, pendingGlobalBytes = global, pendingLocalBytes = local)

    @Test
    fun `learns actual and predicted bytes and the global error`() {
        val (p, s) = LocalLayer.learn(pending(100, 1_000_000.0), noScores, 2_000_000.0)
        assertEquals(2_000_000.0, p.overallActual, 0.0)
        assertEquals(1_000_000.0, p.overallPredicted, 0.0)
        assertEquals(1, p.overallHours)
        assertEquals(1, p.blockHours[3]) // 20:00 is the evening block
        assertNull(p.pendingHour)
        assertEquals(100L, p.lastLearnedHour)
        assertEquals(1, s.hoursSeen)
        assertEquals(1_000_000.0, s.globalRecentMae!!, 0.0)
        assertNull(s.localRecentMae) // no local prediction was made yet
    }

    @Test
    fun `an unobserved hour is dropped unlearned`() {
        val (p, s) = LocalLayer.learn(pending(100, 10.0), noScores, null)
        assertNull(p.pendingHour)
        assertEquals(0, p.overallHours)
        assertEquals(noScores, s)
    }

    @Test
    fun `factor is the ratio of actual to predicted, per block once it has enough hours`() {
        var p = LocalLayerParameters()
        var s = noScores
        repeat(30) { i ->
            val (np, ns) = LocalLayer.learn(p.copy(pendingHour = i.toLong(), pendingLocalHour = 20, pendingGlobalBytes = 1e6), s, 3e6)
            p = np; s = ns
        }
        assertTrue(p.isReady)
        assertEquals(3.0, p.factor(20), 1e-9)                     // evening block: uses 3x more
        assertEquals(p.factor(20), p.factor(3), 0.0)              // night block has no hours: overall ratio
    }

    @Test
    fun `factor is clamped and neutral without predictions`() {
        assertEquals(LocalLayerParameters.MAX_FACTOR, LocalLayerParameters.ratio(1e9, 1.0), 0.0)
        assertEquals(LocalLayerParameters.MIN_FACTOR, LocalLayerParameters.ratio(0.0, 1e6), 0.0)
        assertEquals(1.0, LocalLayerParameters.ratio(5.0, 0.0), 0.0)
        assertEquals(1.0, LocalLayerParameters().factor(12), 0.0)
    }

    @Test
    fun `parameters survive json, and old or bad json resets`() {
        val p = pending(5, 1.5e6, 2.5e6).copy(overallActual = 3e6, overallPredicted = 2e6, overallHours = 40)
        assertEquals(p, LocalLayerParameters.fromJson(p.toJson()))
        assertEquals(LocalLayerParameters(), LocalLayerParameters.fromJson("{}"))
        assertEquals(LocalLayerParameters(), LocalLayerParameters.fromJson("not json"))
        // The log-space correction of contracts 1-3 (version 1) is discarded
        assertEquals(LocalLayerParameters(), LocalLayerParameters.fromJson("{\"version\":1,\"overall_bias\":0.4,\"overall_hours\":90}"))
    }

    @Test
    fun `switch needs a week and a clearly lower local error`() {
        val week = ModelSwitch.MIN_HOURS_FOR_LOCAL
        assertEquals(ActiveModel.GLOBAL, ModelSwitch.decide(ActiveModel.GLOBAL, LayerScores(week - 1, 100.0, 10.0), true))
        assertEquals(ActiveModel.GLOBAL, ModelSwitch.decide(ActiveModel.GLOBAL, LayerScores(week, 100.0, 10.0), false))
        assertEquals(ActiveModel.GLOBAL, ModelSwitch.decide(ActiveModel.GLOBAL, LayerScores(week, 100.0, 96.0), true))
        assertEquals(ActiveModel.LOCAL, ModelSwitch.decide(ActiveModel.GLOBAL, LayerScores(week, 100.0, 94.0), true))
    }

    @Test
    fun `switch keeps local inside the margin and goes back when local is worse`() {
        val week = ModelSwitch.MIN_HOURS_FOR_LOCAL
        assertEquals(ActiveModel.LOCAL, ModelSwitch.decide(ActiveModel.LOCAL, LayerScores(week, 100.0, 97.0), true))
        assertEquals(ActiveModel.GLOBAL, ModelSwitch.decide(ActiveModel.LOCAL, LayerScores(week, 100.0, 100.0), true))
        assertFalse(ModelSwitch.decide(ActiveModel.LOCAL, LayerScores(week, null, 1.0), true) == ActiveModel.LOCAL)
    }
}
