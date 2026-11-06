package com.example.screenofficer

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import java.util.Calendar

data class UsageSnapshot(
    val dayStartMs: Long,
    val checkedAtMs: Long,
    val usageMs: Map<String, Long>,
    val foregroundPackage: String?,
    val available: Boolean,
    val error: String? = null,
)

class UsageRepository(context: Context) {
    private val context = context.applicationContext

    @Suppress("DEPRECATION")
    fun hasAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Call from a background dispatcher; no unselected-app totals are returned or retained. */
    fun snapshot(packages: Set<String>, now: Long = System.currentTimeMillis()): UsageSnapshot {
        val dayStart = localDayStart(now)
        fun unavailable(message: String) = UsageSnapshot(dayStart, now, emptyMap(), null, false, message)
        if (!hasAccess()) return unavailable("Usage Access is off. Enable it to measure time.")
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager?.isUserUnlocked == false) return unavailable("Unlock the phone to read usage.")
        return try {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return unavailable("Usage information is unavailable on this device.")
            val lookbackStart = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DATE, -1)
            }.timeInMillis
            val stream = manager.queryEvents(lookbackStart, now)
                ?: return unavailable("Android has not made usage information available yet.")
            val events = mutableListOf<UsageTimeEvent>()
            val event = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                val kind = eventKind(event.eventType) ?: continue
                events += UsageTimeEvent(event.timeStamp, kind, event.packageName, event.className)
            }
            val result = UsageTimeCalculator.calculate(events, dayStart, now, packages)
            // Access can be revoked while the system query is in flight.
            if (!hasAccess()) return unavailable("Usage Access was turned off.")
            UsageSnapshot(dayStart, now, result.usageMs, result.foregroundPackage, true)
        } catch (_: SecurityException) {
            unavailable("Usage Access is off. Enable it to measure time.")
        } catch (_: RuntimeException) {
            unavailable("Android could not read usage. Try refreshing in a moment.")
        }
    }

    @Suppress("DEPRECATION")
    private fun eventKind(type: Int): UsageEventKind? = when (type) {
        // These are aliases of ACTIVITY_RESUMED / ACTIVITY_PAUSED and support API 24–28 too.
        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventKind.RESUMED
        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventKind.PAUSED
        UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.STOPPED
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventKind.SCREEN_OFF
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventKind.SCREEN_ON
        UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventKind.KEYGUARD_SHOWN
        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventKind.KEYGUARD_HIDDEN
        UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.SHUTDOWN
        UsageEvents.Event.DEVICE_STARTUP -> UsageEventKind.STARTUP
        else -> null
    }
}
