package com.focusguard.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusguard.app.ui.components.AppIcon
import com.focusguard.app.ui.components.ScreenHeader
import com.focusguard.app.ui.components.WarningCard
import com.focusguard.app.ui.components.rememberPermissionSnapshot
import com.focusguard.app.util.PermissionSnapshot
import com.focusguard.app.util.formatDuration
import com.focusguard.app.util.formatMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onOpenLimits: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions by rememberPermissionSnapshot()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            viewModel.refresh()
        }
    }

    TodayContent(
        state = state,
        permissions = permissions,
        dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        onOpenLimits = onOpenLimits,
        onOpenSettings = onOpenSettings,
    )
}

/** Stateless UI for the Today tab (also used to render README screenshots). */
@Composable
fun TodayContent(
    state: TodayState,
    permissions: PermissionSnapshot,
    dateText: String,
    onOpenLimits: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Today", subtitle = dateText)
        }

        if (!permissions.usageAccess) {
            item {
                WarningCard(
                    title = "Screen time is off",
                    body = "Allow usage access so FocusGuard can measure how long you use each app.",
                    actionLabel = "Fix in Settings",
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            return@LazyColumn
        }
        if (!permissions.accessibility) {
            item {
                WarningCard(
                    title = "Blocking is off",
                    body = "Limits are tracked but apps won't be blocked until you turn on the FocusGuard accessibility service.",
                    actionLabel = "Fix in Settings",
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { SummaryCard(state) }

        if (state.loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.rows.isEmpty()) {
            item {
                Text(
                    "No app has been used for more than a minute today. Nice!",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    "Apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(state.rows, key = { it.packageName }) { row -> UsageRow(row) }
            if (state.rows.none { it.limitMinutes != null }) {
                item {
                    WarningCard(
                        title = "Set your first limit",
                        body = "Pick the app that eats most of your time and give it a daily limit.",
                        actionLabel = "Add a limit",
                        onAction = onOpenLimits,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TodayState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Screen time today", style = MaterialTheme.typography.labelLarge)
            Text(
                if (state.loading) "…" else formatDuration(state.totalMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(value = state.focusSessions.toString(), label = "Focus sessions")
                Stat(value = formatMinutes(state.focusMinutes), label = "Focused")
                Stat(value = state.blocks.toString(), label = "Blocks")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UsageRow(row: AppUsageRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.packageName, label = row.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(row.usedMs), style = MaterialTheme.typography.bodyMedium)
            }
            val limit = row.limitMinutes
            if (limit != null) {
                val fraction = (row.usedMs.toFloat() / (limit * 60_000f)).coerceIn(0f, 1f)
                val over = row.usedMs >= limit * 60_000L
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (over) "Limit reached (${formatMinutes(limit)})" else "Limit ${formatMinutes(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
