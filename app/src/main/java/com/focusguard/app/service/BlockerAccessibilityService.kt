package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.focusguard.app.AppContainer
import com.focusguard.app.appContainer
import com.focusguard.app.data.db.AppLimitEntity
import com.focusguard.app.data.db.BlockEventEntity
import com.focusguard.app.data.db.BlockReason
import com.focusguard.app.data.db.FocusSessionEntity
import com.focusguard.app.domain.BlockDecision
import com.focusguard.app.domain.BlockPolicy
import com.focusguard.app.ui.block.BlockActivity
import com.focusguard.app.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The enforcement engine.
 *
 * 1. Android tells us whenever a different app comes to the front
 *    (TYPE_WINDOW_STATE_CHANGED). We only read the package name — the config
 *    sets canRetrieveWindowContent=false, so screen content is never visible to us.
 * 2. If that app has a rule, BlockPolicy decides whether to block it.
 * 3. A light "tick" every few seconds catches the case where a limit runs out
 *    while the user is still inside the app, and finishes expired focus sessions.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer

    @Volatile private var limitsByPackage: Map<String, AppLimitEntity> = emptyMap()
    @Volatile private var activeFocus: FocusSessionEntity? = null
    @Volatile private var currentPackage: String? = null
    @Volatile private var launcherPackages: Set<String> = emptySet()
    @Volatile private var keyboardPackages: Set<String> = emptySet()
    @Volatile private var lastBlockedPackage: String? = null
    @Volatile private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = applicationContext.appContainer
        launcherPackages = container.apps.launcherPackages()
        keyboardPackages = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()

        scope.launch {
            container.limits.observeActive().collect { list ->
                limitsByPackage = list.filter { it.enabled }.associateBy { it.packageName }
            }
        }
        scope.launch { container.focus.observeActive().collect { activeFocus = it } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                runCatching { tick() }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // System UI (notification shade, volume panel) and keyboards pop up over apps;
        // they don't mean the user switched apps.
        if (pkg in IGNORED_PACKAGES || pkg in keyboardPackages) return
        currentPackage = pkg
        if (pkg == packageName || pkg in launcherPackages) return
        if (pkg !in limitsByPackage) return
        scope.launch { runCatching { evaluate(pkg, maxUsageAgeMs = OPEN_USAGE_MAX_AGE_MS) } }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()

        activeFocus?.let { session ->
            if (now >= session.plannedEndAt && container.focus.completeIfActive(session.id)) {
                Notifications.showFocusComplete(this, session.plannedDurationMinutes)
            }
        }

        if (!getSystemService(PowerManager::class.java).isInteractive) return
        val pkg = currentPackage ?: return
        if (pkg == packageName || pkg in launcherPackages || pkg !in limitsByPackage) return
        evaluate(pkg, maxUsageAgeMs = TICK_USAGE_MAX_AGE_MS)
    }

    private suspend fun evaluate(pkg: String, maxUsageAgeMs: Long) {
        val limit = limitsByPackage[pkg] ?: return
        val now = System.currentTimeMillis()
        val focus = activeFocus?.takeIf { now < it.plannedEndAt }
        val usedToday = if (limit.dailyLimitMinutes != null) {
            container.usage.todayUsage(maxUsageAgeMs)[pkg] ?: 0L
        } else {
            0L
        }
        val decision = BlockPolicy.decide(limit, usedToday, focusActive = focus != null)
        if (decision is BlockDecision.Block) block(limit, decision.reason, usedToday, focus?.plannedEndAt)
    }

    private suspend fun block(limit: AppLimitEntity, reason: BlockReason, usedMs: Long, focusEndAt: Long?) {
        val now = System.currentTimeMillis()
        // Opening an app fires several window events; show one block screen, not five.
        if (limit.packageName == lastBlockedPackage && now - lastBlockAt < DEBOUNCE_MS) return
        lastBlockedPackage = limit.packageName
        lastBlockAt = now

        container.blockEvents.insert(
            BlockEventEntity(packageName = limit.packageName, reason = reason, occurredAt = now)
        )
        withContext(Dispatchers.Main) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(
                BlockActivity.intent(
                    context = this@BlockerAccessibilityService,
                    packageName = limit.packageName,
                    appLabel = limit.appLabel,
                    reason = reason,
                    dailyLimitMinutes = limit.dailyLimitMinutes,
                    usedTodayMs = usedMs,
                    focusEndAt = focusEndAt,
                )
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TICK_MS = 5_000L
        const val TICK_USAGE_MAX_AGE_MS = 20_000L
        const val OPEN_USAGE_MAX_AGE_MS = 2_000L
        const val DEBOUNCE_MS = 2_000L
        val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
    }
}
