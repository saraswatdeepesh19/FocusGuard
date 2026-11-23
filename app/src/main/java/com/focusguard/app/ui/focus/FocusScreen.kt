package com.focusguard.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.appContainer
import com.focusguard.app.data.db.FocusSessionEntity
import com.focusguard.app.ui.components.ScreenHeader
import com.focusguard.app.ui.components.WarningCard
import com.focusguard.app.ui.components.rememberPermissionSnapshot
import com.focusguard.app.util.formatClock
import com.focusguard.app.util.formatMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DURATIONS = listOf(15, 25, 45, 60, 90)

@Composable
fun FocusScreen(onOpenLimits: () -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val activeFlow = remember { container.focus.observeActive() }
    val strictFlow = remember { container.settings.strictMode }
    val limitsFlow = remember { container.limits.observeActive() }
    val defaultMinutesFlow = remember { container.settings.focusDefaultMinutes }

    val active by activeFlow.collectAsStateWithLifecycle(initialValue = null)
    val strict by strictFlow.collectAsStateWithLifecycle(initialValue = false)
    val limits by limitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultMinutes by defaultMinutesFlow.collectAsStateWithLifecycle(initialValue = 25)
    val permissions by rememberPermissionSnapshot()

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // If the session ran out while the background service wasn't running, close it here.
    val expiredId = active?.takeIf { now >= it.plannedEndAt }?.id
    LaunchedEffect(expiredId) {
        if (expiredId != null) container.focus.completeIfActive(expiredId)
    }

    val running = active?.takeIf { now < it.plannedEndAt }

    FocusContent(
        running = running,
        now = now,
        blockedCount = limits.count { it.blockDuringFocus },
        strict = strict,
        blockingEnabled = permissions.accessibility,
        defaultMinutes = defaultMinutes,
        onStart = { minutes ->
            scope.launch {
                container.settings.setFocusDefaultMinutes(minutes)
                container.focus.start(minutes)
            }
        },
        onEndEarly = { session -> scope.launch { container.focus.cancelIfActive(session.id) } },
        onOpenLimits = onOpenLimits,
        onOpenSettings = onOpenSettings,
    )
}

/** Stateless UI for the Focus tab (also used to render README screenshots). */
@Composable
fun FocusContent(
    running: FocusSessionEntity?,
    now: Long,
    blockedCount: Int,
    strict: Boolean,
    blockingEnabled: Boolean,
    defaultMinutes: Int,
    onStart: (Int) -> Unit,
    onEndEarly: (FocusSessionEntity) -> Unit,
    onOpenLimits: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(
            title = "Focus",
            subtitle = "Pause your distracting apps for a set time and do something that matters.",
        )
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!blockingEnabled) {
                WarningCard(
                    title = "Blocking is off",
                    body = "Focus sessions can't block apps until the FocusGuard accessibility service is on.",
                    actionLabel = "Turn on in Settings",
                    onAction = onOpenSettings,
                )
            }
            if (blockedCount == 0) {
                WarningCard(
                    title = "No apps to block",
                    body = "Add apps in Limits and tick \"Also block during focus sessions\".",
                    actionLabel = "Go to Limits",
                    onAction = onOpenLimits,
                )
            }

            if (running != null) {
                RunningSession(
                    session = running,
                    now = now,
                    blockedCount = blockedCount,
                    strict = strict,
                    onEndEarly = { onEndEarly(running) },
                )
            } else {
                StartSession(defaultMinutes = defaultMinutes, onStart = onStart)
            }
        }
    }
}

@Composable
private fun RunningSession(
    session: FocusSessionEntity,
    now: Long,
    blockedCount: Int,
    strict: Boolean,
    onEndEarly: () -> Unit,
) {
    val totalMs = (session.plannedEndAt - session.startedAt).coerceAtLeast(1L)
    val remainingMs = (session.plannedEndAt - now).coerceAtLeast(0L)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 16.dp)) {
            CircularProgressIndicator(
                progress = { remainingMs.toFloat() / totalMs },
                modifier = Modifier.size(240.dp),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatClock(remainingMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("left", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "$blockedCount ${if (blockedCount == 1) "app is" else "apps are"} paused. Put the phone down — you've got this.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = onEndEarly, enabled = !strict) { Text("End early") }
        if (strict) {
            Text(
                "Strict mode is on: this session can't be ended early.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartSession(defaultMinutes: Int, onStart: (Int) -> Unit) {
    var selected by remember(defaultMinutes) {
        mutableIntStateOf(if (defaultMinutes in DURATIONS) defaultMinutes else 25)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How long?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATIONS.forEach { minutes ->
                FilterChip(
                    selected = selected == minutes,
                    onClick = { selected = minutes },
                    label = { Text(formatMinutes(minutes)) },
                )
            }
        }
        Button(onClick = { onStart(selected) }, modifier = Modifier.fillMaxWidth()) {
            Text("Start ${formatMinutes(selected)} focus")
        }
        Text(
            "Tip: 25 minutes of focus followed by a 5-minute break (the Pomodoro method) works well for studying.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
