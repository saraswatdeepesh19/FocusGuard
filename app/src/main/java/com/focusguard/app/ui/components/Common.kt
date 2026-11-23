package com.focusguard.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.focusguard.app.util.PermissionSnapshot
import com.focusguard.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Permission status that refreshes every time the user comes back from system Settings. */
@Composable
fun rememberPermissionSnapshot(): State<PermissionSnapshot> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Permissions.snapshot(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = Permissions.snapshot(context) }
    return state
}

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(150)

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(packageName)
            .toBitmap(width = 96, height = 96)
            .asImageBitmap()
            .also { cache.put(packageName, it) }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = IconCache.peek(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { IconCache.load(context, packageName) }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Amber card shown when something needs the user's attention. */
@Composable
fun WarningCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null) {
                    TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Google Play "prominent disclosure" for the Accessibility API.
 * Must be shown, and accepted, BEFORE sending the user to Accessibility settings.
 */
@Composable
fun AccessibilityDisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow FocusGuard to block apps?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FocusGuard uses Android's Accessibility service to:")
                Text("• See the name of the app you open (for example \"Instagram\").")
                Text("• Show a block screen when that app's daily limit is used up, or during a focus session.")
                Text("It does NOT read messages, passwords, text or anything else on your screen.")
                Text(
                    "This information stays on your phone. It is never sent to us or anyone else.",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "On the next screen, tap \"FocusGuard app blocker\" and turn it on. " +
                        "If the switch is greyed out, open App info → ⋮ → \"Allow restricted settings\" first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Agree & continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
