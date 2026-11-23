package com.focusguard.app.ui.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.focusguard.app.data.InstalledApp
import com.focusguard.app.data.db.AppLimitEntity
import com.focusguard.app.domain.StrictRules
import com.focusguard.app.ui.components.AppIcon
import com.focusguard.app.ui.components.ScreenHeader
import com.focusguard.app.util.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What the editor dialog is working on (a new app or an existing rule). */
private data class EditTarget(val packageName: String, val label: String, val original: AppLimitEntity?)

@Composable
fun LimitsScreen() {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val limitsFlow = remember { container.limits.observeActive() }
    val strictFlow = remember { container.settings.strictMode }
    val limits by limitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val strict by strictFlow.collectAsStateWithLifecycle(initialValue = false)

    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    LimitsContent(
        limits = limits,
        strict = strict,
        onAdd = { picking = true },
        onEdit = { limit -> editing = EditTarget(limit.packageName, limit.appLabel, limit) },
        onDelete = { limit -> scope.launch { container.limits.delete(limit) } },
    )

    if (picking) {
        AppPickerDialog(
            excluded = limits.map { it.packageName }.toSet(),
            onPick = { app ->
                picking = false
                editing = EditTarget(app.packageName, app.label, original = null)
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { target ->
        LimitEditorDialog(
            target = target,
            strict = strict,
            onSave = { minutes, blockInFocus ->
                scope.launch {
                    container.limits.save(target.packageName, target.label, minutes, blockInFocus)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Stateless UI for the Limits tab (also used to render README screenshots). */
@Composable
fun LimitsContent(
    limits: List<AppLimitEntity>,
    strict: Boolean,
    onAdd: () -> Unit,
    onEdit: (AppLimitEntity) -> Unit,
    onDelete: (AppLimitEntity) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Limits",
                    subtitle = if (strict) "Strict mode is on: limits can only get stricter."
                    else "Daily time caps and focus-session blocking, per app.",
                )
            }
            if (limits.isEmpty()) {
                item {
                    Text(
                        "No limits yet. Tap \"Add app\" and start with the app that takes most of your time.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(limits, key = { it.id }) { limit ->
                LimitCard(
                    limit = limit,
                    canDelete = !strict,
                    onEdit = { onEdit(limit) },
                    onDelete = { onDelete(limit) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add app") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

}

@Composable
private fun LimitCard(
    limit: AppLimitEntity,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(limit.packageName, label = limit.appLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(limit.appLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val parts = buildList {
                    add(limit.dailyLimitMinutes?.let { "${formatMinutes(it)} / day" } ?: "No daily cap")
                    if (limit.blockDuringFocus) add("blocked in focus")
                }
                Text(
                    parts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    excluded: Set<String>,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { container.apps.launchableApps() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filtered = list.filter {
                        it.packageName !in excluded && it.label.contains(query.trim(), ignoreCase = true)
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, size = 32.dp, label = app.label)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LimitEditorDialog(
    target: EditTarget,
    strict: Boolean,
    onSave: (dailyLimitMinutes: Int?, blockDuringFocus: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val original = target.original
    var hasDailyLimit by remember { mutableStateOf(original?.dailyLimitMinutes != null || original == null) }
    var minutes by remember { mutableIntStateOf(original?.dailyLimitMinutes ?: 30) }
    var blockInFocus by remember { mutableStateOf(original?.blockDuringFocus ?: true) }

    val newCap = if (hasDailyLimit) minutes else null
    val loosening = strict && StrictRules.isLoosening(original, newCap, blockInFocus)
    val noRule = !hasDailyLimit && !blockInFocus
    val error = when {
        loosening -> "Strict mode is on, so you can only make this limit stricter."
        noRule -> "Choose at least one: a daily limit or blocking during focus."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(target.packageName, size = 48.dp, label = target.label) },
        title = { Text(target.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily time limit", modifier = Modifier.weight(1f))
                    Switch(checked = hasDailyLimit, onCheckedChange = { hasDailyLimit = it })
                }
                if (hasDailyLimit) {
                    Text(
                        "${formatMinutes(minutes)} per day",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 240) },
                        valueRange = 5f..240f,
                        steps = 46, // 5-minute steps between 5 and 240
                    )
                    Text(
                        "When it's used up, the app is blocked until midnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable { blockInFocus = !blockInFocus },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = blockInFocus, onCheckedChange = { blockInFocus = it })
                    Text("Also block during focus sessions")
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newCap, blockInFocus) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
