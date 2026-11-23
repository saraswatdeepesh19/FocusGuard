package com.focusguard.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusguard.app.appContainer
import com.focusguard.app.data.db.DailyUsageEntity
import com.focusguard.app.util.Permissions
import com.focusguard.app.util.dayKey
import com.focusguard.app.util.startOfDay
import java.util.concurrent.TimeUnit

/**
 * Every few hours, saves per-app totals for yesterday and today into the local
 * database. Android discards detailed usage events after some days, so this is
 * what makes long-term history (and future weekly reports / cloud backup) possible.
 */
class UsageSnapshotWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Permissions.hasUsageAccess(applicationContext)) return Result.success()
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val yesterdayStart = startOfDay(todayStart - 1)
        snapshot(yesterdayStart, todayStart)
        snapshot(todayStart, now)
        return Result.success()
    }

    private suspend fun snapshot(start: Long, end: Long) {
        val container = applicationContext.appContainer
        val dao = container.dailyUsage
        val day = dayKey(start)
        val now = System.currentTimeMillis()
        val rows = container.usage.usageBetween(start, end)
            .filterValues { it >= 1_000L }
            .map { (pkg, ms) ->
                val existing = dao.find(day, pkg)
                if (existing != null) existing.copy(
                    foregroundMs = ms,
                    updatedAt = now,
                    syncStatus = existing.syncStatus.afterLocalEdit(),
                ) else DailyUsageEntity(day = day, packageName = pkg, foregroundMs = ms, createdAt = now)
            }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    companion object {
        private const val UNIQUE_NAME = "usage_snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSnapshotWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
