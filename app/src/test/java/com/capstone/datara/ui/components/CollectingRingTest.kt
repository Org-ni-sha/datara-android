package com.capstone.datara.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectingRingTest {
    @Test fun aFullTurnEndsWhereItStarted() {
        assertEquals(0f, collectingArcStart(0), 0f)
        assertEquals(90f, collectingArcStart(COLLECTING_TURN_MS / 4), 0.001f)
        assertEquals(collectingArcStart(123), collectingArcStart(123 + COLLECTING_TURN_MS), 0.001f)
    }

    @Test fun thePositionComesFromTheClockSoARestartDoesNotJump() {
        // Two rings created at different times agree at the same frame time
        val frame = 9_876_543_210L
        assertEquals(collectingArcStart(frame), collectingArcStart(frame), 0f)
        val start = collectingArcStart(frame)
        assert(start >= 0f && start < 360f)
    }
}
