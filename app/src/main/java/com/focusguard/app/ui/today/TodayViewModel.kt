package com.focusguard.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusguard.app.AppContainer
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.util.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val usedMs: Long,
    val limitMinutes: Int?,
)

data class TodayState(
    val loading: Boolean = true,
    val totalMs: Long = 0L,
    val rows: List<AppUsageRow> = emptyList(),
    val focusSessions: Int = 0,
    val focusMinutes: Int = 0,
    val blocks: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    /** null until the first calculation finishes. */
    private val usage = MutableStateFlow<Map<String, Long>?>(null)
    private val dayStart = MutableStateFlow(startOfDay(System.currentTimeMillis()))

    val state: StateFlow<TodayState> = combine(
        usage,
        container.limits.observeActive(),
        dayStart.flatMapLatest { container.focus.observeCompletedCountSince(it) },
        dayStart.flatMapLatest { container.focus.observeCompletedMinutesSince(it) },
        dayStart.flatMapLatest { container.blockEvents.observeCountSince(it) },
    ) { usageMap, limits, focusCount, focusMinutes, blocks ->
        if (usageMap == null) return@combine TodayState(loading = true)
        val limitByPackage = limits.associateBy { it.packageName }
        val rows = usageMap
            .filterValues { it >= 60_000L }
            .map { (pkg, ms) ->
                AppUsageRow(
                    packageName = pkg,
                    label = limitByPackage[pkg]?.appLabel ?: container.apps.label(pkg),
                    usedMs = ms,
                    limitMinutes = limitByPackage[pkg]?.dailyLimitMinutes,
                )
            }
            .sortedByDescending { it.usedMs }
        TodayState(
            loading = false,
            totalMs = rows.sumOf { it.usedMs },
            rows = rows,
            focusSessions = focusCount,
            focusMinutes = focusMinutes,
            blocks = blocks,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dayStart.value = startOfDay(now)
            val ignored = container.apps.launcherPackages() + OWN_AND_SYSTEM
            usage.value = container.usage.todayUsage()
                .filterKeys { it !in ignored && container.apps.isUserFacing(it) }
        }
    }

    companion object {
        private val OWN_AND_SYSTEM = setOf("com.focusguard.app", "com.android.systemui", "android")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FocusGuardApp
                TodayViewModel(app.container)
            }
        }
    }
}
