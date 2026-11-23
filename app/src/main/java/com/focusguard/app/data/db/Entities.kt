package com.focusguard.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.UUID

/*
 * ── Sync-ready schema ────────────────────────────────────────────────────────
 * v1.0 is 100% offline, but every table already carries the columns a future
 * login + cloud-sync version needs:
 *   id          UUID string (never clashes when merging data from two phones)
 *   created_at / updated_at   for "last write wins" conflict resolution
 *   sync_status LOCAL_ONLY in v1; PENDING / SYNCED once sync exists
 *   deleted     soft delete, so deletions can be synced too
 */

enum class SyncStatus {
    LOCAL_ONLY, PENDING, SYNCED;

    /** Status a row should get after the user edits it on this device. */
    fun afterLocalEdit(): SyncStatus = if (this == LOCAL_ONLY) LOCAL_ONLY else PENDING
}

enum class FocusStatus { ACTIVE, COMPLETED, CANCELLED }

enum class BlockReason { DAILY_LIMIT, FOCUS_SESSION }

fun newId(): String = UUID.randomUUID().toString()

/** A rule for one app: an optional daily cap and/or "block during focus". */
@Entity(
    tableName = "app_limit",
    indices = [Index(value = ["package_name"], unique = true)],
)
data class AppLimitEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    /** null = no daily cap (app is only blocked during focus sessions). */
    @ColumnInfo(name = "daily_limit_minutes") val dailyLimitMinutes: Int?,
    @ColumnInfo(name = "block_during_focus") val blockDuringFocus: Boolean = true,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)

@Entity(tableName = "focus_session", indices = [Index("status"), Index("started_at")])
data class FocusSessionEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "planned_duration_minutes") val plannedDurationMinutes: Int,
    @ColumnInfo(name = "planned_end_at") val plannedEndAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "status") val status: FocusStatus = FocusStatus.ACTIVE,
    @ColumnInfo(name = "created_at") val createdAt: Long = startedAt,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = startedAt,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)

/** One row each time an app was blocked: powers "blocks today" and future insights. */
@Entity(tableName = "block_event", indices = [Index("occurred_at")])
data class BlockEventEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "reason") val reason: BlockReason,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = occurredAt,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = occurredAt,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)

/**
 * Daily per-app totals. Android only keeps detailed usage events for a few
 * days, so we snapshot them to build long-term history (weekly reports in v1.x).
 */
@Entity(
    tableName = "daily_usage",
    indices = [Index(value = ["day", "package_name"], unique = true)],
)
data class DailyUsageEntity(
    @PrimaryKey val id: String = newId(),
    /** ISO date in the phone's time zone, e.g. "2026-11-23". */
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)

class Converters {
    @TypeConverter fun syncToString(value: SyncStatus): String = value.name
    @TypeConverter fun stringToSync(value: String): SyncStatus = SyncStatus.valueOf(value)
    @TypeConverter fun focusToString(value: FocusStatus): String = value.name
    @TypeConverter fun stringToFocus(value: String): FocusStatus = FocusStatus.valueOf(value)
    @TypeConverter fun reasonToString(value: BlockReason): String = value.name
    @TypeConverter fun stringToReason(value: String): BlockReason = BlockReason.valueOf(value)
}
