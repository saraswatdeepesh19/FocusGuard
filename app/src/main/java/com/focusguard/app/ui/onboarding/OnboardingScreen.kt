package com.focusguard.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusguard.app.appContainer
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.rememberPermissionSnapshot
import com.focusguard.app.util.PermissionSnapshot
import com.focusguard.app.util.Permissions
import kotlinx.coroutines.launch

/**
 * First-run setup. No account, no sign-up: just the permissions the app needs,
 * each explained in plain words before the user is sent to system Settings.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val permissions by rememberPermissionSnapshot()
    var showDisclosure by remember { mutableStateOf(false) }
    // Bumped when the runtime-permission dialog closes, so the snapshot refreshes immediately.
    var notificationAsked by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationAsked = true }
    val notificationsGranted = permissions.notifications ||
        (notificationAsked && Permissions.hasNotificationPermission(context))

    OnboardingContent(
        permissions = permissions,
        notificationsGranted = notificationsGranted,
        onAllowUsage = { Permissions.openUsageAccessSettings(context) },
        onAllowBlocking = { showDisclosure = true },
        onAllowNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onOpenBattery = { Permissions.openBatterySettings(context) },
        onFinished = onFinished,
    )

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                scope.launch { container.settings.setDisclosureAccepted(true) }
                Permissions.openAccessibilitySettings(context)
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

/** Stateless first-run UI (also used to render README screenshots). */
@Composable
fun OnboardingContent(
    permissions: PermissionSnapshot,
    notificationsGranted: Boolean,
    onAllowUsage: () -> Unit,
    onAllowBlocking: () -> Unit,
    onAllowNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
    onFinished: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Welcome to FocusGuard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Track your screen time, set daily limits for distracting apps, and get back to what matters.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No account. No internet. Everything stays on this phone.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            SetupStep(
                number = 1,
                title = "See your screen time",
                body = "Allow \"Usage access\" so FocusGuard can see how long each app is open. Find FocusGuard in the list and switch it on.",
                done = permissions.usageAccess,
                actionLabel = "Allow usage access",
                onAction = onAllowUsage,
            )

            SetupStep(
                number = 2,
                title = "Block apps when time is up",
                body = "Turn on the FocusGuard accessibility service. It only learns which app is open — it can't read your screen.",
                done = permissions.accessibility,
                actionLabel = "Turn on blocking",
                onAction = onAllowBlocking,
            )

            if (Build.VERSION.SDK_INT >= 33) {
                SetupStep(
                    number = 3,
                    title = "Focus reminders",
                    body = "Get a notification when a focus session ends. Optional.",
                    done = notificationsGranted,
                    actionLabel = "Allow notifications",
                    onAction = onAllowNotifications,
                )
            }

            SetupStep(
                number = if (Build.VERSION.SDK_INT >= 33) 4 else 3,
                title = "Keep blocking reliable",
                body = "Some phones (Xiaomi, Realme, Oppo, Vivo, Samsung) stop background apps to save battery. " +
                    "Set FocusGuard to \"Unrestricted\" / \"Don't optimise\". On Xiaomi also turn on Autostart " +
                    "and \"Display pop-up windows while running in background\".",
                done = permissions.batteryUnrestricted,
                actionLabel = "Open battery settings",
                onAction = onOpenBattery,
            )

            Spacer(Modifier.height(8.dp))
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                Text(if (permissions.coreReady) "Continue" else "Skip for now")
            }
            if (!permissions.coreReady) {
                Text(
                    "You can finish setup later from Settings. Tracking needs step 1; blocking needs step 2.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$number. $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (done) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!done) {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
