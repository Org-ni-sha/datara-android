package com.capstone.datara.domain.cycle

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.telemetry.MeasurementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleMathTest {

    private val start = 1_793_000_000_000L
    private val min = 60_000L
    private val quota = 10_000L

    private fun reading(fromMin: Long, toMin: Long, bytes: Long, status: String = MeasurementStatus.OK, rxShare: Long = bytes / 2) =
        DurableUsageReading(
            since = start + fromMin * min,
            until = start + toMin * min,
            bytesRx = rxShare,
            bytesTx = bytes - rxShare,
            status = status,
            schemaVersion = 3
        )

    private fun usage(vararg readings: DurableUsageReading, endAt: Long? = null, quotaBytes: Long = quota) =
        CycleMath.usage(readings.toList(), start, endAt, quotaBytes)

    @Test fun noReadingsMeansNothingMeasured() {
        val result = usage()
        assertEquals(CycleUsage.NONE, result)
        assertNull(result.measuredThrough)
        assertFalse(result.isIncomplete)
    }

    @Test fun sumsRxAndTxOfReadingsInsideTheWindow() {
        val result = usage(reading(0, 10, 1_000), reading(10, 20, 2_000, rxShare = 1_500))
        assertEquals(3_000L, result.usedBytes)
        assertEquals(0L, result.estimatedBytes)
        assertEquals(start + 20 * min, result.measuredThrough)
        assertEquals(0L, result.unmeasuredMillis)
    }

    @Test fun readingThatStraddlesStartIsSplitProportionallyByTime() {
        // 4 of its 10 minutes fall before start_at, so 60% of the bytes count
        val result = usage(reading(-4, 6, 1_000), reading(6, 16, 500))
        assertEquals(1_100L, result.usedBytes)
        assertEquals(600L, result.estimatedBytes)
        assertEquals(0L, result.unmeasuredMillis)
    }

    @Test fun readingsOutsideTheWindowDoNotCount() {
        val ended = start + 20 * min
        val result = usage(reading(-20, -10, 9_000), reading(-10, 0, 9_000), reading(0, 20, 100), reading(20, 30, 9_000), endAt = ended)
        assertEquals(100L, result.usedBytes)
        assertEquals(ended, result.measuredThrough)
    }

    @Test fun readingThatStraddlesEndedAtIsSplitToo() {
        val result = usage(reading(0, 10, 1_000), reading(10, 20, 1_000), endAt = start + 15 * min)
        assertEquals(1_500L, result.usedBytes)
        assertEquals(500L, result.estimatedBytes)
        assertEquals(start + 15 * min, result.measuredThrough)
    }

    @Test fun reachedAtIsTheEndOfTheReadingWhereTheSumCrossesTheQuota() {
        val result = usage(reading(0, 10, 4_000), reading(10, 20, 5_000), reading(20, 30, 3_000), reading(30, 40, 3_000))
        assertEquals(start + 30 * min, result.reachedAt)
        assertEquals(15_000L, result.usedBytes)
    }

    @Test fun usageExactlyAtTheQuotaCountsAsReached() {
        assertEquals(start + 10 * min, usage(reading(0, 10, quota)).reachedAt)
        assertNull(usage(reading(0, 10, quota - 1)).reachedAt)
    }

    @Test fun reachedAtIsClampedToTheWindowWhenTheCrossingReadingStraddlesEndedAt() {
        val ended = start + 5 * min
        val result = usage(reading(0, 10, 30_000), endAt = ended)
        assertEquals(15_000L, result.usedBytes)
        assertEquals(ended, result.reachedAt)
    }

    @Test fun reachedAtIsNeverBeforeStartWhenTheFirstReadingStraddlesIt() {
        val result = usage(reading(-5, 5, 40_000))
        assertEquals(start + 5 * min, result.reachedAt)
    }

    @Test fun gapReadingsKeepTheirRealBytes() {
        // A durable GAP is a long span with exact cumulative bytes; it straddles start here
        val result = usage(reading(-30, 30, 6_000, MeasurementStatus.GAP))
        assertEquals(3_000L, result.usedBytes)
        assertEquals(0, result.lostReadings)
        assertFalse(result.isIncomplete)
    }

    @Test fun zeroByteStatusesAreFlaggedAsLostUsage() {
        val result = usage(
            reading(0, 10, 1_000),
            reading(10, 20, 0, MeasurementStatus.COUNTER_RESET),
            reading(20, 30, 0, MeasurementStatus.COUNTER_JUMP),
            reading(30, 40, 0, MeasurementStatus.UNSUPPORTED),
            reading(40, 50, 100, MeasurementStatus.INTERRUPTED)
        )
        assertEquals(1_100L, result.usedBytes)
        assertEquals(3, result.lostReadings)
        assertTrue(result.isIncomplete)
    }

    @Test fun clockErrorReadingsCannotBePlacedAndAreSkipped() {
        val backwards = DurableUsageReading(since = start + 20 * min, until = start + 15 * min, bytesRx = 5_000, bytesTx = 0,
            status = MeasurementStatus.CLOCK_ERROR, schemaVersion = 3)
        val result = usage(reading(0, 10, 1_000), backwards)
        assertEquals(1_000L, result.usedBytes)
        assertEquals(1, result.lostReadings)
        assertEquals(start + 10 * min, result.measuredThrough)
    }

    @Test fun clockErrorBeforeTheCycleIsNotCountedAgainstIt() {
        val old = DurableUsageReading(since = start - 5 * min, until = start - 10 * min, bytesRx = 1, bytesTx = 0,
            status = MeasurementStatus.CLOCK_ERROR, schemaVersion = 3)
        assertEquals(0, usage(old).lostReadings)
    }

    @Test fun uncoveredTimeIsReportedAsUnmeasured() {
        // Tracking started 5 min after the cycle, then stopped for 20 min
        val result = usage(reading(5, 15, 100), reading(35, 45, 100))
        assertEquals(25 * min, result.unmeasuredMillis)
        assertEquals(start + 45 * min, result.measuredThrough)
        assertTrue(result.isIncomplete)
    }

    @Test fun overlappingReadingsDoNotCreateFalseGaps() {
        val result = usage(reading(0, 20, 100), reading(10, 15, 100), reading(20, 30, 100))
        assertEquals(0L, result.unmeasuredMillis)
        assertEquals(start + 30 * min, result.measuredThrough)
    }

    @Test fun readingsAreSummedInTimeOrderWhateverTheInputOrder() {
        val result = usage(reading(20, 30, 6_000), reading(0, 10, 6_000), reading(10, 20, 6_000))
        assertEquals(start + 20 * min, result.reachedAt)
    }

    @Test fun zeroLengthReadingAtStartCounts() {
        assertEquals(50L, usage(reading(0, 0, 50)).usedBytes)
        assertEquals(0L, usage(reading(-1, -1, 50)).usedBytes)
    }

    @Test fun proportionDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE / 2, CycleMath.proportion(Long.MAX_VALUE, 1, 2))
        assertEquals(333L, CycleMath.proportion(1_000, 1, 3))
    }

    @Test fun remainingIsClampedForDisplayOnly() {
        assertEquals(-500L, CycleMath.remainingBytes(1_000, 1_500))
        assertEquals(0L, CycleMath.displayRemainingBytes(1_000, 1_500))
        assertEquals(400L, CycleMath.displayRemainingBytes(1_000, 600))
    }

    @Test fun percentUsedIsUnclamped() {
        assertEquals(25.0, CycleMath.percentUsed(4_000, 1_000), 0.0)
        assertEquals(150.0, CycleMath.percentUsed(1_000, 1_500), 0.0)
        assertEquals(0.0, CycleMath.percentUsed(1_000_000, 0), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun windowCannotEndBeforeItStarts() {
        usage(endAt = start - 1)
    }
}
