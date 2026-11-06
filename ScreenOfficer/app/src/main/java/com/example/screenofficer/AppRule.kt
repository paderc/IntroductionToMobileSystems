package com.example.screenofficer

const val DEFAULT_MESSAGE = "Is this still how you want to spend your time?"

data class TrackedApp(val packageName: String, val label: String)

enum class BlockMode { SOFT, HARD }

data class AppRule(
    val packageName: String,
    val label: String,
    val limitMinutes: Int = 30,
    val message: String = DEFAULT_MESSAGE,
    val mode: BlockMode = BlockMode.SOFT,
    val enabled: Boolean = true,
)

object BlockPolicy {
    fun shouldBlock(rule: AppRule, usedMs: Long, snoozeThresholdMs: Long?): Boolean {
        if (!rule.enabled) return false
        val dailyLimitMs = rule.limitMinutes.toLong() * 60_000L
        val thresholdMs = if (rule.mode == BlockMode.SOFT) {
            maxOf(dailyLimitMs, snoozeThresholdMs ?: 0L)
        } else {
            dailyLimitMs
        }
        return usedMs >= thresholdMs
    }
}
