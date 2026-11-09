package com.capstone.datara.ui.dashboard

import com.capstone.datara.telemetry.TrackingAccess
import com.capstone.datara.ui.history.DayBucket
import com.capstone.datara.ui.quota.QuotaDetailsModel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeStatusTest {
    private val allowed = TrackingAccess(usageAccess = true, phoneState = true, notifications = true, batteryUnrestricted = true)

    @Test fun noBannerWhenTrackingRunsWithGrants() {
        assertNull(TrackingIssue.of(allowed, serviceRunning = true, cycleActive = true))
    }

    @Test fun missingUsageAccessComesFirst() {
        assertEquals(TrackingIssue.USAGE_ACCESS, TrackingIssue.of(TrackingAccess(), serviceRunning = false, cycleActive = true))
    }

    @Test fun notificationsThenStoppedService() {
        assertEquals(TrackingIssue.NOTIFICATIONS, TrackingIssue.of(allowed.copy(notifications = false), false, true))
        assertEquals(TrackingIssue.SERVICE_STOPPED, TrackingIssue.of(allowed, serviceRunning = false, cycleActive = true))
    }

    @Test fun noBannerWithoutActiveCycle() {
        assertNull(TrackingIssue.of(TrackingAccess(), serviceRunning = false, cycleActive = false))
    }

    @Test fun weekReusesHistoryDaysAndKeepsGaps() {
        val start = LocalDate.of(2026, 11, 2)
        val days = (0..6).map { DayBucket(start.plusDays(it.toLong()), if (it == 3) null else it * 1_000_000L, 24, 24) }
        val week = HomeWeek.from(days)
        assertEquals(7, week.days.size)
        assertEquals("Nov 2", week.days.first().label)
        assertNull(week.days[3].bytes)
        assertEquals((0..6).filter { it != 3 }.sumOf { it * 1_000_000L }, week.totalBytes)
        assertEquals("Nov 8", week.latest?.label)
        assertTrue(week.hasReadings)
    }

    @Test fun weekWithoutReadings() {
        val week = HomeWeek.from(listOf(DayBucket(LocalDate.of(2026, 11, 8), null, 0, 24)))
        assertFalse(week.hasReadings)
        assertNull(week.latest)
    }

    private val live = QuotaDetailsModel(hasQuota = true, name = "Defense", quotaBytes = 5_000_000_000, usedBytes = 1_000_000_000,
        remainingBytes = 4_000_000_000, progress = 0.2f, startedAtMillis = 0)

    @Test fun realCycleShowsWithItsTrackingIssue() {
        val hero = HeroContent.select(live, false, TrackingIssue.SERVICE_STOPPED)
        assertTrue(hero.isLive)
        assertEquals(TrackingIssue.SERVICE_STOPPED, hero.issue)
    }

    @Test fun loadingAndEmptyStates() {
        assertTrue(HeroContent.select(null, liveLoading = true, issue = null).loading)
        val empty = HeroContent.select(null, false, null)
        assertTrue(empty.empty)
        assertFalse(empty.isLive)
    }

    @Test fun reachedCycleHasNoBanner() {
        val hero = HeroContent.live(live.copy(isReached = true), TrackingIssue.SERVICE_STOPPED)
        assertNull(hero.issue)
    }

    @Test fun greetingUsesTimeOfDayAndFirstName() {
        assertEquals("Good morning, Cybe", homeGreeting(8, "Cybe Montejo", "cybe"))
        assertEquals("Good afternoon, @cybe", homeGreeting(13, " ", "cybe"))
        assertEquals("Good evening", homeGreeting(22, null, null))
        assertEquals("Tracking “Defense”", homeSubtitle("Defense"))
    }
}
