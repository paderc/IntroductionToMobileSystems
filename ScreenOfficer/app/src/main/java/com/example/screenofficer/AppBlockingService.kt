package com.example.screenofficer

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.PowerManager
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Observes package changes only; it never reads app text, views, or screenshots. */
class AppBlockingService : AccessibilityService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private lateinit var store: RuleStore
    private lateinit var usage: UsageRepository
    private lateinit var windowManager: WindowManager
    private var registered = false
    private var foregroundHint: String? = null
    private var foregroundHintAt = 0L
    private var foregroundVersion = 0L
    private var inputMethodPackages: Set<String> = emptySet()
    private var overlay: View? = null
    private var overlayKey: OverlayKey? = null

    private data class OverlayKey(val rule: AppRule, val dayStart: Long)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        changes.trySend(Unit)
    }

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_INPUT_METHOD_CHANGED) refreshInputMethods()
            foregroundVersion++
            foregroundHint = null
            hideOverlay()
            changes.trySend(Unit)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (registered) return
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        connected = true
        store = RuleStore(this)
        usage = UsageRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshInputMethods()
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        ContextCompat.registerReceiver(
            this,
            deviceReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        scope.launch { monitor() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!registered || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        // IME windows belong to their keyboard package, but do not represent an app switch.
        // In particular, opening a keyboard must not suspend enforcement of the target app.
        if (packageName in inputMethodPackages) return
        // Our accessibility overlay emits window events under our package too. Treating those
        // as a foreground switch would remove and recreate the same block indefinitely.
        if (overlay != null && packageName == this.packageName &&
            event.className?.toString() != MainActivity::class.java.name
        ) return
        foregroundHint = packageName
        foregroundHintAt = SystemClock.elapsedRealtime()
        foregroundVersion++
        if (overlayKey?.rule?.packageName != packageName) hideOverlay()
        changes.trySend(Unit)
    }

    private suspend fun monitor() {
        while (scope.isActive) {
            val rules = withContext(Dispatchers.IO) {
                store.allRules().filter { it.enabled && !SafetyApps.isProtected(this@AppBlockingService, it.packageName) }
            }
            if (rules.isEmpty() || !canDisplay()) {
                hideOverlay()
                // Package events, rule edits and unlock broadcasts wake an idle service.
                changes.receive()
                continue
            }
            val hint = foregroundHint
            val version = foregroundVersion
            val snapshot = withContext(Dispatchers.IO) {
                usage.snapshot(rules.map { it.packageName }.toSet())
            }
            if (version != foregroundVersion) continue
            // Window events can be transient dialogs and may never be followed by another
            // package event when that dialog closes. Reconcile them with measured resumed
            // activity state instead of trusting an untracked hint indefinitely. A short
            // grace interval prevents lagging UsageEvents from redrawing over Settings/Home;
            // SystemUI gets extra time for notification-shade and system-panel transitions.
            val graceMs = if (hint == "com.android.systemui") 5_000L else 1_500L
            val freshHint = hint != null && SystemClock.elapsedRealtime() - foregroundHintAt < graceMs
            val current = if (freshHint) hint else snapshot.foregroundPackage
            if (!freshHint && snapshot.available) foregroundHint = snapshot.foregroundPackage
            val rule = rules.firstOrNull { it.packageName == current }
            val measured = current?.let { snapshot.usageMs[it] } ?: 0L
            if (snapshot.available && canDisplay() && rule != null &&
                // A window event can precede the corresponding UsageEvents event slightly.
                snapshot.foregroundPackage == current &&
                BlockPolicy.shouldBlock(rule, measured, store.snoozeThreshold(rule.packageName))
            ) {
                showOverlay(rule, measured, snapshot.dayStartMs)
            } else {
                hideOverlay()
            }
            withTimeoutOrNull(if (rule != null && snapshot.available) 1_000L else 5_000L) { changes.receive() }
        }
    }

    private fun refreshInputMethods() {
        inputMethodPackages = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    }

    private fun canDisplay(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isInteractive &&
            !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private fun showOverlay(rule: AppRule, measuredMs: Long, dayStart: Long) {
        val key = OverlayKey(rule, dayStart)
        if (overlayKey == key && overlay != null) return
        hideOverlay()
        val viewContext = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_NoActionBar)
        val content = LinearLayout(viewContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        fun text(value: String, size: Float, bold: Boolean = false) {
            content.addView(TextView(viewContext).apply {
                text = value
                textSize = size
                setTextColor(Color.rgb(22, 61, 54))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp(18))
            })
        }
        fun button(value: String, action: (Button) -> Unit) {
            content.addView(Button(viewContext).apply {
                text = value
                isAllCaps = false
                minHeight = dp(52)
                setOnClickListener { action(this) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
        text("ScreenOfficer", 18f, true)
        text(if (rule.mode == BlockMode.HARD) "Daily limit reached." else "Time for a check-in?", 30f, true)
        text(rule.label, 23f, true)
        text("${measuredMs / 60_000} min used today · ${rule.limitMinutes} min daily limit", 17f)
        text(rule.message, 21f)
        text(
            if (rule.mode == BlockMode.HARD)
                "Hard block: this app is blocked until midnight. You can manage your limit in ScreenOfficer."
            else "Soft block: take a break or choose five more minutes of app use.",
            16f,
        )
        button("Take a break · Go home") { goHome() }
        if (rule.mode == BlockMode.SOFT) {
            button("Continue for 5 more minutes") { clicked ->
                clicked.isEnabled = false
                scope.launch {
                    // Use current measured usage, rather than the number displayed when
                    // this overlay opened. The allowance survives a process restart.
                    val fresh = withContext(Dispatchers.IO) { usage.snapshot(setOf(rule.packageName)) }
                    val savedRule = withContext(Dispatchers.IO) {
                        store.allRules().firstOrNull { it.packageName == rule.packageName }
                    }
                    if (fresh.available && savedRule?.mode == BlockMode.SOFT && savedRule.enabled) {
                        withContext(Dispatchers.IO) {
                            store.snooze(rule.packageName, fresh.usageMs[rule.packageName] ?: measuredMs)
                        }
                    }
                    hideOverlay()
                    changes.trySend(Unit)
                }
            }
        }
        button("Manage limits in ScreenOfficer") {
            foregroundHint = packageName
            foregroundHintAt = SystemClock.elapsedRealtime()
            foregroundVersion++
            hideOverlay()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            changes.trySend(Unit)
        }
        val scroller = ScrollView(viewContext).apply {
            setBackgroundColor(Color.rgb(255, 252, 245))
            isFillViewport = true
            fitsSystemWindows = true
            isFocusableInTouchMode = true
            addView(content)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) goHome()
                    true
                } else false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.CENTER
            title = "ScreenOfficer daily limit"
        }
        try {
            windowManager.addView(scroller, params)
            overlay = scroller
            overlayKey = key
            scroller.requestFocus()
            scroller.announceForAccessibility("${rule.label}. Daily limit reached. ${rule.message}")
        } catch (_: WindowManager.BadTokenException) {
            // Android may detach the service while a check is completing.
            hideOverlay()
        } catch (_: SecurityException) {
            hideOverlay()
        }
    }

    private fun goHome() {
        // Suppress checks until the HOME transition supplies its new foreground package.
        foregroundHint = packageName
        foregroundHintAt = SystemClock.elapsedRealtime()
        foregroundVersion++
        hideOverlay()
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        changes.trySend(Unit)
    }

    private fun hideOverlay() {
        val old = overlay
        overlay = null
        overlayKey = null
        if (old != null) {
            try {
                windowManager.removeViewImmediate(old)
            } catch (_: IllegalArgumentException) {
                // Window already removed as the service was detached.
            }
        }
    }

    override fun onInterrupt() {
        hideOverlay()
        changes.trySend(Unit)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        connected = false
        hideOverlay()
        if (registered) {
            store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            unregisterReceiver(deviceReceiver)
            registered = false
        }
        scope.cancel()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        @Volatile
        var connected: Boolean = false
            private set
    }
}
