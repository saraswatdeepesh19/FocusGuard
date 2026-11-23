package com.focusguard.app.data

import com.focusguard.app.data.db.FocusSessionDao
import com.focusguard.app.data.db.FocusSessionEntity
import com.focusguard.app.data.db.FocusStatus
import kotlinx.coroutines.flow.Flow

class FocusRepository(private val dao: FocusSessionDao) {

    fun observeActive(): Flow<FocusSessionEntity?> = dao.observeActive()

    fun observeCompletedCountSince(since: Long): Flow<Int> = dao.observeCompletedCountSince(since)

    fun observeCompletedMinutesSince(since: Long): Flow<Int> = dao.observeCompletedMinutesSince(since)

    /** Starts a new session (any session still marked active is cancelled first). */
    suspend fun start(minutes: Int): FocusSessionEntity {
        val now = System.currentTimeMillis()
        dao.getAllActive().forEach { dao.finishIfActive(it.id, FocusStatus.CANCELLED.name, now) }
        val session = FocusSessionEntity(
            startedAt = now,
            plannedDurationMinutes = minutes,
            plannedEndAt = now + minutes * 60_000L,
        )
        dao.insert(session)
        return session
    }

    /** @return true if this call completed the session (false if already finished). */
    suspend fun completeIfActive(id: String): Boolean =
        dao.finishIfActive(id, FocusStatus.COMPLETED.name, System.currentTimeMillis()) > 0

    suspend fun cancelIfActive(id: String): Boolean =
        dao.finishIfActive(id, FocusStatus.CANCELLED.name, System.currentTimeMillis()) > 0
}
