package com.focusguard.app.util

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Midnight (local time) of the day that contains [millis]. */
fun startOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()

/** "2026-11-23" for the local day containing [millis]. */
fun dayKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

/** 0 → "<1m", 25 min → "25m", 90 min → "1h 30m", 120 min → "2h". */
fun formatDuration(ms: Long): String {
    if (ms < 60_000L) return "<1m"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(minutes * 60_000L)

/** Countdown text: 1499000 ms → "24:59". Rounds up so it never shows 0:00 early. */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
