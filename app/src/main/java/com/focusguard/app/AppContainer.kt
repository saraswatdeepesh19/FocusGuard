package com.focusguard.app

import android.content.Context
import com.focusguard.app.data.FocusRepository
import com.focusguard.app.data.InstalledAppsRepository
import com.focusguard.app.data.LimitRepository
import com.focusguard.app.data.SettingsStore
import com.focusguard.app.data.UsageRepository
import com.focusguard.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple manual dependency injection: one place that creates every shared object.
 * In v2, add the auth client and sync engine here; screens won't need to change.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = AppDatabase.get(context)
    val settings = SettingsStore(context)
    val usage = UsageRepository(context)
    val apps = InstalledAppsRepository(context)
    val limits = LimitRepository(database.appLimitDao())
    val focus = FocusRepository(database.focusSessionDao())
    val blockEvents = database.blockEventDao()
    val dailyUsage = database.dailyUsageDao()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FocusGuardApp).container
