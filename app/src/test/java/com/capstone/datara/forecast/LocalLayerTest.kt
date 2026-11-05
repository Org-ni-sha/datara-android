package com.capstone.datara.forecast

import com.capstone.datara.data.local.entity.ActiveModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln1p

class LocalLayerTest {

    private val noScores = LayerScores(0, null, null)

    private fun pending(hour: Long, globalLog: Double, localLog: Double? = null, localHour: Int = 20) =
        LocalLayerParameters(pendingHour = hour, pendingLocalHour = localHour, pendingGlobalLog = globalLog, pendingLocalLog = localLog)

    @Test
    fun `learns the log residual and the global error`() {
        val (p, s) = LocalLayer.learn(pending(100, ln1p(1_000_000.0)), noScores, 2_000_000.0)
        assertEquals(ln1p(2_000_000.0) - ln1p(1_000_000.0), p.overallBias, 1e-12)
        assertEquals(1, p.overallHours)
        assertEquals(1, p.blockHours[3]) // 20:00 is the evening block
        assertNull(p.pendingHour)
        assertEquals(100L, p.lastLearnedHour)
        assertEquals(1, s.hoursSeen)
        assertEquals(1_000_000.0, s.globalRecentMae!!, 1.0)
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
    fun `correction uses the block once it has enough hours`() {
        var p = LocalLayerParameters()
        var s = noScores
        repeat(30) { i ->
            val (np, ns) = LocalLayer.learn(p.copy(pendingHour = i.toLong(), pendingLocalHour = 20, pendingGlobalLog = ln1p(1e6)), s, 4e6)
            p = np; s = ns
        }
        assertTrue(p.isReady)
        assertEquals(ln1p(4e6) - ln1p(1e6), p.correction(20), 1e-9) // evening block
        assertEquals(p.overallBias, p.correction(3), 0.0)          // night block has no hours
    }

    @Test
    fun `parameters survive json and bad json resets`() {
        val p = pending(5, 1.5, 2.5).copy(overallBias = 0.3, overallHours = 40)
        assertEquals(p, LocalLayerParameters.fromJson(p.toJson()))
        assertEquals(LocalLayerParameters(), LocalLayerParameters.fromJson("{}"))
        assertEquals(LocalLayerParameters(), LocalLayerParameters.fromJson("not json"))
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
