package com.focusguard.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusguard.app.data.db.AppLimitEntity
import com.focusguard.app.data.db.BlockReason
import com.focusguard.app.data.db.FocusSessionEntity
import com.focusguard.app.ui.block.BlockInfo
import com.focusguard.app.ui.block.BlockScreen
import com.focusguard.app.ui.focus.FocusContent
import com.focusguard.app.ui.limits.LimitsContent
import com.focusguard.app.ui.onboarding.OnboardingContent
import com.focusguard.app.ui.settings.SettingsContent
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.today.AppUsageRow
import com.focusguard.app.ui.today.TodayContent
import com.focusguard.app.ui.today.TodayState
import com.focusguard.app.util.PermissionSnapshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the app's screens with realistic sample data into docs/screenshots/*.png
 * for the README. Run with:  ./gradlew testDebugUnitTest -Proborazzi.test.record=true
 * (CI does this automatically via .github/workflows/screenshots.yml).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
    // Plain Application: the real one starts WorkManager, which isn't needed to draw screens.
    application = Application::class,
)
class ReadmeScreenshots {

    private val allGranted = PermissionSnapshot(
        usageAccess = true, accessibility = true, notifications = true, batteryUnrestricted = true,
    )

    private val limits = listOf(
        limit("com.instagram.android", "Instagram", 60, focus = true),
        limit("com.google.android.youtube", "YouTube", 90, focus = true),
        limit("com.netflix.mediaclient", "Netflix", 45, focus = true),
        limit("com.reddit.frontpage", "Reddit", 20, focus = true),
        limit("com.whatsapp", "WhatsApp", null, focus = true),
    )

    private val todayState = TodayState(
        loading = false,
        totalMs = minutes(3 * 60 + 12),
        rows = listOf(
            AppUsageRow("com.instagram.android", "Instagram", minutes(60), 60),
            AppUsageRow("com.google.android.youtube", "YouTube", minutes(48), 90),
            AppUsageRow("com.whatsapp", "WhatsApp", minutes(32), null),
            AppUsageRow("com.android.chrome", "Chrome", minutes(21), null),
            AppUsageRow("com.reddit.frontpage", "Reddit", minutes(14), 20),
            AppUsageRow("com.spotify.music", "Spotify", minutes(11), null),
            AppUsageRow("com.google.android.gm", "Gmail", minutes(6), null),
        ),
        focusSessions = 3,
        focusMinutes = 95,
        blocks = 4,
    )

    @Test fun today() = shot("today") {
        InApp(Destination.TODAY) {
            TodayContent(todayState, allGranted, "Monday, 23 November", {}, {})
        }
    }

    @Test fun todayDark() = shot("today_dark", dark = true) {
        InApp(Destination.TODAY) {
            TodayContent(todayState, allGranted, "Monday, 23 November", {}, {})
        }
    }

    @Test fun limits() = shot("limits") {
        InApp(Destination.LIMITS) {
            LimitsContent(limits = limits, strict = false, onAdd = {}, onEdit = {}, onDelete = {})
        }
    }

    @Test fun focusRunning() = shot("focus") {
        val now = 1_800_000_000_000L
        val started = now - (7 * 60 + 18) * 1000L // 17:42 left of 25:00
        val session = FocusSessionEntity(
            startedAt = started,
            plannedDurationMinutes = 25,
            plannedEndAt = started + minutes(25),
        )
        InApp(Destination.FOCUS) {
            FocusContent(
                running = session, now = now, blockedCount = 5, strict = true,
                blockingEnabled = true, defaultMinutes = 25,
                onStart = {}, onEndEarly = {}, onOpenLimits = {}, onOpenSettings = {},
            )
        }
    }

    @Test fun focusStart() = shot("focus_start") {
        InApp(Destination.FOCUS) {
            FocusContent(
                running = null, now = 0L, blockedCount = 5, strict = false,
                blockingEnabled = true, defaultMinutes = 25,
                onStart = {}, onEndEarly = {}, onOpenLimits = {}, onOpenSettings = {},
            )
        }
    }

    @Test fun blockScreen() = shot("block") {
        BlockScreen(
            info = BlockInfo(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                reason = BlockReason.DAILY_LIMIT,
                dailyLimitMinutes = 60,
                usedTodayMs = minutes(60),
                focusEndAt = null,
            ),
            onGoHome = {},
            onOpenApp = {},
            initialSuggestion = 2,
        )
    }

    @Test fun onboarding() = shot("onboarding") {
        OnboardingContent(
            permissions = PermissionSnapshot(
                usageAccess = true, accessibility = false, notifications = false, batteryUnrestricted = false,
            ),
            notificationsGranted = false,
            onAllowUsage = {}, onAllowBlocking = {}, onAllowNotifications = {},
            onOpenBattery = {}, onFinished = {},
        )
    }

    @Test fun settings() = shot("settings") {
        InApp(Destination.SETTINGS) {
            SettingsContent(
                strict = true,
                installId = "3f9c2a7e-0000-0000-0000-000000000000",
                permissions = allGranted.copy(batteryUnrestricted = false),
                message = null,
                onStrictChange = {}, onFixUsage = {}, onFixAccessibility = {},
                onFixNotifications = {}, onFixBattery = {}, onDeleteAll = {},
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        // Path is relative to the app/ module, so images land in <repo>/docs/screenshots/.
        captureRoboImage("../docs/screenshots/$name.png") {
            FocusGuardTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }

    /** Wraps a tab's content with the real bottom navigation bar, like in the app. */
    @Composable
    private fun InApp(tab: Destination, content: @Composable () -> Unit) {
        Scaffold(bottomBar = { AppBottomBar(currentRoute = tab.route, onSelect = {}) }) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }

    private fun limit(pkg: String, label: String, minutes: Int?, focus: Boolean) = AppLimitEntity(
        packageName = pkg, appLabel = label, dailyLimitMinutes = minutes, blockDuringFocus = focus,
    )

    private fun minutes(m: Int): Long = m * 60_000L
}
