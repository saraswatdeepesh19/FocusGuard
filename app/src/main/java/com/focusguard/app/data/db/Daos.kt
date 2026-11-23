package com.focusguard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limit WHERE deleted = 0 ORDER BY app_label COLLATE NOCASE")
    fun observeActive(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limit WHERE package_name = :packageName LIMIT 1")
    suspend fun findByPackage(packageName: String): AppLimitEntity?

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_session WHERE status = 'ACTIVE' AND deleted = 0 ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_session WHERE status = 'ACTIVE' AND deleted = 0")
    suspend fun getAllActive(): List<FocusSessionEntity>

    @Insert
    suspend fun insert(session: FocusSessionEntity)

    /**
     * Atomically finish a session only if it is still ACTIVE.
     * Returns the number of rows changed (0 = someone else already finished it),
     * so the UI and the background service never both "complete" one session.
     */
    @Query(
        """
        UPDATE focus_session
        SET status = :newStatus, ended_at = :now, updated_at = :now,
            sync_status = CASE WHEN sync_status = 'LOCAL_ONLY' THEN 'LOCAL_ONLY' ELSE 'PENDING' END
        WHERE id = :id AND status = 'ACTIVE'
        """
    )
    suspend fun finishIfActive(id: String, newStatus: String, now: Long): Int

    @Query(
        "SELECT COUNT(*) FROM focus_session " +
            "WHERE status = 'COMPLETED' AND deleted = 0 AND started_at >= :since"
    )
    fun observeCompletedCountSince(since: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(planned_duration_minutes), 0) FROM focus_session " +
            "WHERE status = 'COMPLETED' AND deleted = 0 AND started_at >= :since"
    )
    fun observeCompletedMinutesSince(since: Long): Flow<Int>
}

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Query("SELECT COUNT(*) FROM block_event WHERE deleted = 0 AND occurred_at >= :since")
    fun observeCountSince(since: Long): Flow<Int>
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE day = :day AND package_name = :packageName LIMIT 1")
    suspend fun find(day: String, packageName: String): DailyUsageEntity?

    @Upsert
    suspend fun upsertAll(rows: List<DailyUsageEntity>)
}
