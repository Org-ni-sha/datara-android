package com.capstone.datara.ui.history

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HistorySamplesTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test fun dailyAndWeeklyFixturesAgreeOnTheirSharedDay() {
        val cycle = historyCycles(today).first()
        val day = historySnapshot(HistoryWindow.DAY, cycle, today)
        val week = historySnapshot(HistoryWindow.WEEK, cycle, today)
        assertEquals(24, day.points.size)
        assertEquals(7, week.points.size)
        assertEquals(day.totalBytes, week.points.last().bytes)
        assertEquals(4_000_000_000L, week.totalBytes)
        assertEquals(week.totalBytes / 7, week.averageDailyBytes)
        assertTrue(day.points.all { it.bytes >= 0 })
    }

    @Test fun eachCycleChartReconcilesExactlyToItsDetailsAndDuration() {
        historyCycles(today).forEach { cycle ->
            val snapshot = historySnapshot(HistoryWindow.CYCLE, cycle, today)
            assertEquals(cycle.state.usedBytes, snapshot.totalBytes)
            assertEquals(snapshot.days, snapshot.points.size)
            assertEquals(snapshot.totalBytes / snapshot.days, snapshot.averageDailyBytes)
        }
        assertEquals(3, historySnapshot(HistoryWindow.CYCLE, historyCycles(today).last(), today).days)
    }
}
