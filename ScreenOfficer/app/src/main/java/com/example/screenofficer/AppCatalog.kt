package com.example.screenofficer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.util.Locale

/** Only launchable apps are offered; essential phone controls remain reachable. */
class AppCatalog(private val context: Context) {
    fun loadApps(): List<TrackedApp> {
        val protectedPackages = SafetyApps.protectedPackages(context)
        return context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).asSequence()
            .filterNot { it.activityInfo.packageName in protectedPackages }
            .map { TrackedApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.label.lowercase(Locale.getDefault()) }, { it.packageName }))
            .toList()
    }
}

object SafetyApps {
    fun isProtected(context: Context, packageName: String): Boolean =
        packageName in protectedPackages(context)

    // Resolve on demand so newly installed launchers and dialers are protected too.
    internal fun protectedPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)
        addAll(listOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.android.phone", "com.android.server.telecom",
            "com.android.emergency", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.samsung.android.packageinstaller",
        ))
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Intent.ACTION_DIAL),
            Intent(Settings.ACTION_SETTINGS),
            Intent("android.intent.action.EMERGENCY_DIAL"),
        )
        intents.forEach { intent ->
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .forEach { add(it.activityInfo.packageName) }
        }
    }
}

object PermissionAccess {
    fun openUsageSettings(context: Context) {
        open(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAccessibilitySettings(context: Context) {
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    private fun open(context: Context, intent: Intent, fallback: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}


