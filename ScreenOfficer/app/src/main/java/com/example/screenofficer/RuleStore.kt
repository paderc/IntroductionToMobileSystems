package com.example.screenofficer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Calendar

/** Only selected apps and their settings are persisted; Android owns the usage history. */
class RuleStore(context: Context) {
    val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("screenofficer_rules", Context.MODE_PRIVATE)

    fun allRules(): List<AppRule> = preferences.all.mapNotNull { (key, value) ->
        if (!key.startsWith(RULE_PREFIX) || value !is String) return@mapNotNull null
        runCatching {
            val json = JSONObject(value)
            AppRule(
                packageName = key.removePrefix(RULE_PREFIX),
                label = json.getString("label"),
                limitMinutes = json.getInt("limitMinutes"),
                message = json.getString("message"),
                mode = BlockMode.valueOf(json.getString("mode")),
                enabled = json.optBoolean("enabled", true),
            ).also(::validate)
        }.getOrNull()
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    fun save(rule: AppRule) {
        validate(rule)
        val json = JSONObject()
            .put("label", rule.label)
            .put("limitMinutes", rule.limitMinutes)
            .put("message", rule.message.trim())
            .put("mode", rule.mode.name)
            .put("enabled", rule.enabled)
        preferences.edit()
            .putString(RULE_PREFIX + rule.packageName, json.toString())
            .remove(SNOOZE_DAY_PREFIX + rule.packageName)
            .remove(SNOOZE_USAGE_PREFIX + rule.packageName)
            .apply()
    }

    fun remove(packageName: String) {
        preferences.edit()
            .remove(RULE_PREFIX + packageName)
            .remove(SNOOZE_DAY_PREFIX + packageName)
            .remove(SNOOZE_USAGE_PREFIX + packageName)
            .apply()
    }

    /** Five more minutes of measured app use, rather than five minutes on the clock. */
    fun snooze(packageName: String, usedMs: Long, now: Long = System.currentTimeMillis()) {
        val rule = allRules().firstOrNull { it.packageName == packageName } ?: return
        if (rule.mode != BlockMode.SOFT || !rule.enabled) return
        val thresholdMs = usedMs.coerceIn(0L, Long.MAX_VALUE - FIVE_MINUTES_MS) + FIVE_MINUTES_MS
        preferences.edit()
            .putLong(SNOOZE_DAY_PREFIX + packageName, localDayStart(now))
            .putLong(SNOOZE_USAGE_PREFIX + packageName, thresholdMs)
            .apply()
    }

    fun snoozeThreshold(packageName: String, now: Long = System.currentTimeMillis()): Long? {
        if (preferences.getLong(SNOOZE_DAY_PREFIX + packageName, Long.MIN_VALUE) != localDayStart(now)) {
            return null
        }
        return preferences.getLong(SNOOZE_USAGE_PREFIX + packageName, 0L).takeIf { it > 0L }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun validate(rule: AppRule) {
        require(rule.packageName.isNotBlank()) { "Choose an app." }
        require(rule.label.isNotBlank()) { "The app name cannot be blank." }
        require(rule.limitMinutes in 1..1440) { "Choose a limit from 1 to 1440 minutes." }
        require(rule.message.isNotBlank() && rule.message.length <= 120) {
            "Enter a reminder of 1 to 120 characters."
        }
    }

    private companion object {
        const val RULE_PREFIX = "rule."
        const val SNOOZE_DAY_PREFIX = "snooze.day."
        const val SNOOZE_USAGE_PREFIX = "snooze.usage."
        const val FIVE_MINUTES_MS = 300_000L
    }
}

internal fun localDayStart(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
