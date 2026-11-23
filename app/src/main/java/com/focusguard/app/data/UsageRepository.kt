package com.focusguard.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusguard.app.util.Permissions
import com.focusguard.app.util.startOfDay

/**
 * Calculates how long each app was on screen, using Android's own usage log.
 *
 * Why raw events instead of UsageStatsManager.queryUsageStats()? The aggregated
 * stats use fixed system "buckets" that often don't line up with midnight, so
 * they over- or under-count "today". Walking the open/close events ourselves
 * gives numbers that match what the user experienced.
 */
class UsageRepository(private val context: Context) {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    @Volatile private var cachedAt = 0L
    @Volatile private var cached: Map<String, Long> = emptyMap()

    /**
     * Foreground time per package since local midnight.
     * @param maxAgeMs reuse the last result if it is younger than this
     *                 (the blocker checks often; recomputing every time wastes battery).
     */
    fun todayUsage(maxAgeMs: Long = 0L): Map<String, Long> {
        val now = System.currentTimeMillis()
        val dayStart = startOfDay(now)
        if (cachedAt >= dayStart && now - cachedAt <= maxAgeMs) return cached
        val fresh = usageBetween(dayStart, now)
        cached = fresh
        cachedAt = now
        return fresh
    }

    /** Foreground time per package between [start] and [end] (epoch millis). */
    fun usageBetween(start: Long, end: Long): Map<String, Long> {
        if (end <= start || !Permissions.hasUsageAccess(context)) return emptyMap()

        val totals = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()

        fun addSession(pkg: String, from: Long, to: Long) {
            val s = maxOf(from, start)
            val e = minOf(to, end)
            if (e > s) totals[pkg] = (totals[pkg] ?: 0L) + (e - s)
        }

        val events = try {
            // Look back a little so an app opened before midnight and still open counts correctly.
            usageStatsManager.queryEvents(start - LOOKBACK_MS, end)
        } catch (e: SecurityException) {
            return emptyMap()
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val time = event.timeStamp
            when (event.eventType) {
                EVENT_RESUMED -> openSince.putIfAbsent(pkg, time)
                EVENT_PAUSED -> openSince.remove(pkg)?.let { addSession(pkg, it, time) }
                EVENT_SCREEN_OFF, EVENT_SHUTDOWN -> {
                    // Screen went off: whatever was open stops counting.
                    openSince.forEach { (openPkg, since) -> addSession(openPkg, since, time) }
                    openSince.clear()
                }
            }
        }
        // Apps still in the foreground right now.
        openSince.forEach { (pkg, since) -> addSession(pkg, since, end) }
        return totals
    }

    private companion object {
        const val LOOKBACK_MS = 3 * 60 * 60 * 1000L

        // Raw values so the code works on API 26+ (named constants arrived in later APIs).
        const val EVENT_RESUMED = 1       // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
        const val EVENT_PAUSED = 2        // ACTIVITY_PAUSED  / MOVE_TO_BACKGROUND
        const val EVENT_SCREEN_OFF = 16   // SCREEN_NON_INTERACTIVE (API 28+)
        const val EVENT_SHUTDOWN = 26     // DEVICE_SHUTDOWN (API 29+)
    }
}
