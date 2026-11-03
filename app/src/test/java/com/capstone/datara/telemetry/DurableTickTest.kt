package com.capstone.datara.telemetry

import com.capstone.datara.telemetry.DurableTick.DAY_MS
import com.capstone.datara.telemetry.PrimaryTick.Counters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cumulative-since-midnight arithmetic of the 10-minute NetworkStatsManager read. */
class DurableTickTest {

    private val midnight = 20_000L * DAY_MS // a UTC midnight
    private val tenMin = MeasurementRules.APP_COLLECTION_INTERVAL_MS

    /** A NetworkStatsManager stand-in: cumulative totals keyed by query window. */
    private class FakeStats(val answers: Map<Pair<Long, Long>, Counters?>) {
        val queries = mutableListOf<Pair<Long, Long>>()
        fun query(anchor: Long, until: Long): Counters? {
            queries += anchor to until
            return answers[anchor to until]
        }
    }

    @Test
    fun `utc midnight is the bucket boundary at or before a time`() {
        assertEquals(midnight, DurableTick.utcMidnight(midnight))
        assertEquals(midnight, DurableTick.utcMidnight(midnight + DAY_MS - 1))
    }

    @Test
    fun `first read stores the baseline only`() {
        val now = midnight + 3_600_000
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(5_000, 500)))
        val result = DurableTick.compute(null, now, stats::query)!!
        assertTrue(result.spans.isEmpty())
        assertEquals(DurableCheckpoint(midnight, 5_000, 500, now), result.checkpoint)
        // The end lies a day ahead so the bucket in progress counts in full
        assertEquals(listOf(midnight to now + DAY_MS), stats.queries)
    }

    @Test
    fun `next read stores the difference over ten minutes`() {
        val before = DurableCheckpoint(midnight, 5_000, 500, midnight + 3_600_000)
        val now = before.takenAt + tenMin
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(8_000, 700)))
        val span = DurableTick.compute(before, now, stats::query)!!.spans.single()
        assertEquals(3_000L, span.bytesRx)
        assertEquals(200L, span.bytesTx)
        assertEquals(before.takenAt, span.since)
        assertEquals(now, span.until)
        assertEquals("OK", span.status)
    }

    @Test
    fun `zero usage is kept as a measured span`() {
        val before = DurableCheckpoint(midnight, 5_000, 500, midnight + 3_600_000)
        val now = before.takenAt + tenMin
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(5_000, 500)))
        val span = DurableTick.compute(before, now, stats::query)!!.spans.single()
        assertEquals(0L, span.bytesTotal)
        assertEquals("OK", span.status)
    }

    @Test
    fun `new utc day closes yesterday exactly and restarts today from zero`() {
        val yesterday = midnight - DAY_MS
        val before = DurableCheckpoint(yesterday, 10_000, 1_000, midnight - 300_000)
        val now = midnight + 300_000
        val stats = FakeStats(mapOf(
            (yesterday to midnight) to Counters(12_000, 1_100),
            (midnight to now + DAY_MS) to Counters(400, 40)
        ))
        val result = DurableTick.compute(before, now, stats::query)!!
        val (closing, today) = result.spans
        assertEquals(2_100L, closing.bytesTotal)
        assertEquals(midnight, closing.until)
        assertEquals(440L, today.bytesTotal)
        assertEquals(midnight, today.since)
        assertEquals(DurableCheckpoint(midnight, 400, 40, now), result.checkpoint)
    }

    @Test
    fun `failed query keeps the previous checkpoint for a retry`() {
        val before = DurableCheckpoint(midnight, 5_000, 500, midnight + 3_600_000)
        assertNull(DurableTick.compute(before, before.takenAt + tenMin, FakeStats(emptyMap())::query))
    }

    @Test
    fun `failed day-close query is retried as a whole`() {
        val yesterday = midnight - DAY_MS
        val before = DurableCheckpoint(yesterday, 10_000, 1_000, midnight - 300_000)
        val now = midnight + 300_000
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(400, 40)))
        assertNull(DurableTick.compute(before, now, stats::query))
    }

    @Test
    fun `totals below the baseline are a counter reset with no bytes`() {
        val before = DurableCheckpoint(midnight, 5_000, 500, midnight + 3_600_000)
        val now = before.takenAt + tenMin
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(100, 10)))
        val span = DurableTick.compute(before, now, stats::query)!!.spans.single()
        assertEquals("COUNTER_RESET", span.status)
        assertEquals(0L, span.bytesTotal)
    }

    @Test
    fun `read after a long stop keeps its bytes but is a gap`() {
        val before = DurableCheckpoint(midnight, 5_000, 500, midnight + 600_000)
        val now = before.takenAt + 3 * 3_600_000
        val stats = FakeStats(mapOf((midnight to now + DAY_MS) to Counters(9_000, 900)))
        val span = DurableTick.compute(before, now, stats::query)!!.spans.single()
        assertEquals("GAP", span.status)
        assertEquals(4_400L, span.bytesTotal)
    }
}
