package com.example.screenofficer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class UsageTimeCalculatorTest {
    private val app = "app.one"
    private val other = "app.two"
    private fun event(at: Long, kind: UsageEventKind, pkg: String? = app, activity: String? = null) =
        UsageTimeEvent(at, kind, pkg, activity)

    @Test fun countsClosedSessionsAndAnOpenForegroundSession() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED), event(30, UsageEventKind.PAUSED),
                event(50, UsageEventKind.RESUMED),
            ), 0, 90, setOf(app),
        )
        assertEquals(60L, result.usageMs[app])
        assertEquals(app, result.foregroundPackage)
    }

    @Test fun clipsAMidnightSessionWithoutCountingYesterday() {
        val result = UsageTimeCalculator.calculate(
            listOf(event(50, UsageEventKind.RESUMED), event(150, UsageEventKind.PAUSED)),
            100, 200, setOf(app),
        )
        assertEquals(50L, result.usageMs[app])
        assertNull(result.foregroundPackage)
    }

    @Test fun switchingAppsDoesNotRequireAPauseEventAndDoesNotOverlapTotals() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED),
                event(30, UsageEventKind.RESUMED, other),
                event(40, UsageEventKind.PAUSED), // Late pause for the previous app.
                event(70, UsageEventKind.PAUSED, other),
            ), 0, 90, setOf(app, other),
        )
        assertEquals(mapOf(app to 20L, other to 40L), result.usageMs)
        assertNull(result.foregroundPackage)
    }

    @Test fun oldActivityPauseDoesNotEndItsReplacementActivity() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED, activity = "Feed"),
                event(30, UsageEventKind.RESUMED, activity = "Video"),
                event(31, UsageEventKind.PAUSED, activity = "Feed"),
                event(50, UsageEventKind.STOPPED, activity = "Feed"),
                event(70, UsageEventKind.PAUSED, activity = "Video"),
            ), 0, 90, setOf(app),
        )
        assertEquals(60L, result.usageMs[app])
    }

    @Test fun screenOffAndLockedIntervalsAreExcluded() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED),
                event(30, UsageEventKind.SCREEN_OFF, null),
                event(31, UsageEventKind.KEYGUARD_SHOWN, null),
                event(60, UsageEventKind.SCREEN_ON, null),
                event(70, UsageEventKind.KEYGUARD_HIDDEN, null),
            ), 0, 90, setOf(app),
        )
        assertEquals(40L, result.usageMs[app])
        assertEquals(app, result.foregroundPackage)
    }

    @Test fun lockedDeviceHasNoForegroundAppAndNoGrowingUsage() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED),
                event(30, UsageEventKind.KEYGUARD_SHOWN, null),
            ), 0, 900, setOf(app),
        )
        assertEquals(20L, result.usageMs[app])
        assertNull(result.foregroundPackage)
    }

    @Test fun shutdownClosesTheSessionAndDoesNotCountPoweredOffTime() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED),
                event(30, UsageEventKind.SHUTDOWN, null),
                event(70, UsageEventKind.STARTUP, null),
            ), 0, 900, setOf(app),
        )
        assertEquals(20L, result.usageMs[app])
        assertNull(result.foregroundPackage)
    }

    @Test fun orphanedResumeBetweenShutdownAndStartupIsIgnored() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED),
                event(30, UsageEventKind.SHUTDOWN, null),
                event(35, UsageEventKind.RESUMED),
                event(70, UsageEventKind.STARTUP, null),
                event(80, UsageEventKind.RESUMED),
            ), 0, 90, setOf(app),
        )
        assertEquals(30L, result.usageMs[app])
    }

    @Test fun repeatedResumeEventsDoNotDoubleCountAndFutureEventsAreIgnored() {
        val result = UsageTimeCalculator.calculate(
            listOf(
                event(10, UsageEventKind.RESUMED), event(20, UsageEventKind.RESUMED),
                event(30, UsageEventKind.RESUMED), event(100, UsageEventKind.PAUSED),
            ), 0, 90, setOf(app),
        )
        assertEquals(80L, result.usageMs[app])
        assertEquals(app, result.foregroundPackage)
    }

    @Test fun unselectedAppsDoNotAppearInReturnedTotals() {
        val result = UsageTimeCalculator.calculate(
            listOf(event(10, UsageEventKind.RESUMED, other)), 0, 90, setOf(app),
        )
        assertEquals(mapOf(app to 0L), result.usageMs)
        assertEquals(other, result.foregroundPackage)
    }

    @Test fun noEventsMeansZeroUsageRatherThanAnInventedSession() {
        val result = UsageTimeCalculator.calculate(emptyList(), 100, 200, setOf(app))
        assertEquals(mapOf(app to 0L), result.usageMs)
        assertNull(result.foregroundPackage)
    }

    @Test fun localMidnightHandlesDaylightSavingWithoutAssuming24Hours() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
            val previousDay = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.MARCH, 29, 12, 0)
            }.timeInMillis
            val nextDay = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.MARCH, 30, 12, 0)
            }.timeInMillis
            assertEquals(23 * 60 * 60 * 1000L, localDayStart(nextDay) - localDayStart(previousDay))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
