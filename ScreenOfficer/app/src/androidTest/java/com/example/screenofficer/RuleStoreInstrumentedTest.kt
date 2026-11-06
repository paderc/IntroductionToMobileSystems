package com.example.screenofficer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Run on a test device: these tests clear ScreenOfficer's saved rules before and after each case. */
@RunWith(AndroidJUnit4::class)
class RuleStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: RuleStore
    private val rule = AppRule("test.selected.app", "Selected app", limitMinutes = 30)
    private val usedMs = 31 * 60_000L
    private val today = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, 6, 12, 0)
    }.timeInMillis

    @Before fun prepare() {
        store = RuleStore(context)
        store.clear()
    }

    @After fun cleanUp() {
        store.clear()
    }

    @Test fun settingsSurviveANewStoreInstance() {
        val saved = rule.copy(mode = BlockMode.HARD, message = "Time to study.", enabled = false)
        store.save(saved)
        assertEquals(listOf(saved), RuleStore(context).allRules())
    }

    @Test fun snoozePersistsForTodayWithoutChangingDailyGoalAndResetsTomorrow() {
        store.save(rule)
        store.snooze(rule.packageName, usedMs, today)
        val reloaded = RuleStore(context)
        assertEquals(usedMs + 300_000L, reloaded.snoozeThreshold(rule.packageName, today))
        assertEquals(rule, reloaded.allRules().single())
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DATE, 1)
        }.timeInMillis
        assertNull(reloaded.snoozeThreshold(rule.packageName, tomorrow))
    }

    @Test fun hardBlockCannotReceiveASnooze() {
        store.save(rule.copy(mode = BlockMode.HARD))
        store.snooze(rule.packageName, usedMs, today)
        assertNull(store.snoozeThreshold(rule.packageName, today))
    }

    @Test fun savingAChangedGoalClearsTheExtension() {
        store.save(rule)
        store.snooze(rule.packageName, usedMs, today)
        store.save(rule.copy(limitMinutes = 45))
        assertNull(store.snoozeThreshold(rule.packageName, today))
        assertEquals(45, store.allRules().single().limitMinutes)
    }

    @Test fun invalidValuesAreRejectedWithoutOverwritingSavedSettings() {
        store.save(rule)
        listOf(
            rule.copy(limitMinutes = 0),
            rule.copy(limitMinutes = 1441),
            rule.copy(message = " "),
            rule.copy(message = "x".repeat(121)),
        ).forEach { invalid ->
            try {
                store.save(invalid)
                fail("Invalid settings must not be saved: $invalid")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
        assertEquals(listOf(rule), RuleStore(context).allRules())
    }

    @Test fun removingOrClearingRulesAlsoClearsSnoozes() {
        store.save(rule)
        store.snooze(rule.packageName, usedMs, today)
        store.remove(rule.packageName)
        assertTrue(store.allRules().isEmpty())
        assertNull(store.snoozeThreshold(rule.packageName, today))
        store.save(rule)
        store.snooze(rule.packageName, usedMs, today)
        store.clear()
        assertTrue(RuleStore(context).allRules().isEmpty())
        assertNull(RuleStore(context).snoozeThreshold(rule.packageName, today))
    }
}
