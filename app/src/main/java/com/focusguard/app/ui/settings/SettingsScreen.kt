package com.focusguard.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.BuildConfig
import com.focusguard.app.appContainer
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.ScreenHeader
import com.focusguard.app.ui.components.rememberPermissionSnapshot
import com.focusguard.app.util.PermissionSnapshot
import com.focusguard.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    val strictFlow = remember { container.settings.strictMode }
    val installIdFlow = remember { container.settings.installId }
    val activeFocusFlow = remember { container.focus.observeActive() }
    val strict by strictFlow.collectAsStateWithLifecycle(initialValue = false)
    val installId by installIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val activeFocus by activeFocusFlow.collectAsStateWithLifecycle(initialValue = null)
    val permissions by rememberPermissionSnapshot()

    var confirmStrictOff by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) Permissions.openAppDetails(context) }

    val focusRunning = activeFocus?.let { System.currentTimeMillis() < it.plannedEndAt } == true

    SettingsContent(
        strict = strict,
        installId = installId,
        permissions = permissions,
        message = message,
        onStrictChange = { turnOn ->
            when {
                turnOn -> scope.launch { container.settings.setStrictMode(true) }
                focusRunning -> message = "Strict mode can't be turned off during a focus session."
                else -> confirmStrictOff = true
            }
        },
        onFixUsage = { Permissions.openUsageAccessSettings(context) },
        onFixAccessibility = { showDisclosure = true },
        onFixNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onFixBattery = { Permissions.openBatterySettings(context) },
        onDeleteAll = { confirmDeleteAll = true },
    )

    if (confirmStrictOff) {
        AlertDialog(
            onDismissRequest = { confirmStrictOff = false },
            title = { Text("Turn off strict mode?") },
            text = { Text("You set strict mode to protect yourself from impulse. Are you sure you want to loosen your rules?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStrictOff = false
                    message = null
                    scope.launch { container.settings.setStrictMode(false) }
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmStrictOff = false }) { Text("Keep it on") } },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently deletes your limits, focus history and saved screen-time history from this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch {
                        withContext(Dispatchers.IO) { container.database.clearAllTables() }
                        message = "All data deleted."
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }

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

/** Stateless UI for the Settings tab (also used to render README screenshots). */
@Composable
fun SettingsContent(
    strict: Boolean,
    installId: String?,
    permissions: PermissionSnapshot,
    message: String?,
    onStrictChange: (Boolean) -> Unit,
    onFixUsage: () -> Unit,
    onFixAccessibility: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(title = "Settings")

        Section("Commitment") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Limits can only be made stricter, can't be deleted, and focus sessions can't be ended early.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = strict,
                    onCheckedChange = onStrictChange,
                )
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("Permissions") {
            PermissionRow(
                title = "Usage access",
                description = "Needed to measure screen time.",
                granted = permissions.usageAccess,
                onFix = onFixUsage,
            )
            HorizontalDivider()
            PermissionRow(
                title = "App blocking (Accessibility)",
                description = "Needed to block apps. Only sees which app is open.",
                granted = permissions.accessibility,
                onFix = onFixAccessibility,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                HorizontalDivider()
                PermissionRow(
                    title = "Notifications",
                    description = "Tells you when a focus session ends.",
                    granted = permissions.notifications,
                    onFix = onFixNotifications,
                )
            }
            HorizontalDivider()
            PermissionRow(
                title = "Battery: unrestricted",
                description = "Stops your phone from killing the blocker in the background.",
                granted = permissions.batteryUnrestricted,
                onFix = onFixBattery,
            )
        }

        Section("Your data") {
            Text(
                "FocusGuard has no account and no internet access. Your screen time, limits and focus " +
                    "history are stored only on this phone and are deleted when you uninstall the app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Device ID: ${installId?.take(8) ?: "…"} (random, never leaves this phone)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDeleteAll, enabled = !strict) {
                Text("Delete all my data", color = if (strict) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
            if (strict) {
                Text(
                    "Turn off strict mode to delete data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("About") {
            Text("FocusGuard ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        }
    }

}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (granted) "Granted" else "Missing",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) TextButton(onClick = onFix) { Text("Fix") }
    }
}
