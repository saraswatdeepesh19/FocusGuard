package com.focusguard.app.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusguard.app.data.db.BlockReason
import com.focusguard.app.ui.MainActivity
import com.focusguard.app.ui.components.AppIcon
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.util.formatClock
import com.focusguard.app.util.formatDuration
import com.focusguard.app.util.formatMinutes
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class BlockInfo(
    val packageName: String,
    val appLabel: String,
    val reason: BlockReason,
    val dailyLimitMinutes: Int?,
    val usedTodayMs: Long,
    val focusEndAt: Long?,
)

/** Full-screen "time's up" page shown on top of a blocked app. */
class BlockActivity : ComponentActivity() {

    private var info by mutableStateOf<BlockInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        info = parse(intent)
        // Back must not return to the blocked app.
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusGuardTheme {
                info?.let { BlockScreen(it, onGoHome = { goHome() }, onOpenApp = { openFocusGuard() }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        info = parse(intent)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openFocusGuard() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun parse(intent: Intent): BlockInfo? {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        return BlockInfo(
            packageName = pkg,
            appLabel = intent.getStringExtra(EXTRA_LABEL) ?: pkg,
            reason = intent.getStringExtra(EXTRA_REASON)
                ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
                ?: BlockReason.DAILY_LIMIT,
            dailyLimitMinutes = intent.getIntExtra(EXTRA_LIMIT, -1).takeIf { it >= 0 },
            usedTodayMs = intent.getLongExtra(EXTRA_USED, 0L),
            focusEndAt = intent.getLongExtra(EXTRA_FOCUS_END, -1L).takeIf { it > 0 },
        )
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_LIMIT = "limit_minutes"
        private const val EXTRA_USED = "used_ms"
        private const val EXTRA_FOCUS_END = "focus_end_at"

        fun intent(
            context: Context,
            packageName: String,
            appLabel: String,
            reason: BlockReason,
            dailyLimitMinutes: Int?,
            usedTodayMs: Long,
            focusEndAt: Long?,
        ): Intent = Intent(context, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_LABEL, appLabel)
            .putExtra(EXTRA_REASON, reason.name)
            .putExtra(EXTRA_LIMIT, dailyLimitMinutes ?: -1)
            .putExtra(EXTRA_USED, usedTodayMs)
            .putExtra(EXTRA_FOCUS_END, focusEndAt ?: -1L)
    }
}

/** "Focus on another activity": small, doable things instead of scrolling. */
private val SUGGESTIONS = listOf(
    "Drink a glass of water and stretch for 2 minutes.",
    "Read 5 pages of a book.",
    "Go for a 10-minute walk.",
    "Write down tomorrow's top 3 tasks.",
    "Call a friend or family member.",
    "Tidy your desk for 5 minutes.",
    "Do 10 push-ups or 20 squats.",
    "Take 10 slow, deep breaths.",
    "Revise one topic from your notes.",
    "Step outside and look at something far away for a minute.",
)

@Composable
private fun BlockScreen(info: BlockInfo, onGoHome: () -> Unit, onOpenApp: () -> Unit) {
    var suggestionIndex by remember { mutableIntStateOf(Random.nextInt(SUGGESTIONS.size)) }
    val now by produceState(System.currentTimeMillis(), info.focusEndAt) {
        while (info.focusEndAt != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val (title, body) = when (info.reason) {
        BlockReason.DAILY_LIMIT -> {
            val limitText = info.dailyLimitMinutes?.let { formatMinutes(it) } ?: "your limit"
            "Time's up for ${info.appLabel}" to
                "You've used ${info.appLabel} for ${formatDuration(info.usedTodayMs)} today. " +
                "Your daily limit is $limitText. It unlocks again at midnight."
        }
        BlockReason.FOCUS_SESSION -> {
            val remaining = info.focusEndAt?.let { formatClock(it - now) } ?: ""
            "${info.appLabel} is paused" to
                "You're in a focus session. $remaining left — you can do this."
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(info.packageName, size = 72.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try this instead", style = MaterialTheme.typography.labelLarge)
                    Text(SUGGESTIONS[suggestionIndex], style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { suggestionIndex = (suggestionIndex + 1) % SUGGESTIONS.size },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Another idea") }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
            TextButton(onClick = onOpenApp) { Text("Open FocusGuard") }
        }
    }
}
