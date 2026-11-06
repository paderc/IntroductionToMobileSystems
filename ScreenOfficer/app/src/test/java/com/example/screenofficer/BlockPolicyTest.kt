package com.example.screenofficer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {
    private val rule = AppRule("app.one", "One", limitMinutes = 30)
    private val limitMs = 30 * 60_000L

    @Test fun blocksAtExactlyTheLimit() {
        assertFalse(BlockPolicy.shouldBlock(rule, limitMs - 1, null))
        assertTrue(BlockPolicy.shouldBlock(rule, limitMs, null))
        assertTrue(BlockPolicy.shouldBlock(rule, limitMs + 1, null))
    }

    @Test fun pausedRuleNeverBlocks() {
        assertFalse(BlockPolicy.shouldBlock(rule.copy(enabled = false), limitMs * 10, null))
    }

    @Test fun softBlockRespectsFiveMoreMinutesOfUsage() {
        val extension = limitMs + 300_000L
        assertFalse(BlockPolicy.shouldBlock(rule, limitMs, extension))
        assertFalse(BlockPolicy.shouldBlock(rule, extension - 1, extension))
        assertTrue(BlockPolicy.shouldBlock(rule, extension, extension))
    }

    @Test fun expiredSnoozeRestoresDailyLimit() {
        assertTrue(BlockPolicy.shouldBlock(rule, limitMs, null))
    }

    @Test fun snoozeCannotReduceTheSavedDailyGoal() {
        assertFalse(BlockPolicy.shouldBlock(rule, limitMs - 1, limitMs - 100))
    }

    @Test fun hardBlockIgnoresAnySoftSnoozeState() {
        assertTrue(BlockPolicy.shouldBlock(rule.copy(mode = BlockMode.HARD), limitMs, limitMs * 10))
    }
}
