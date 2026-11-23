package com.focusguard.app.data

import com.focusguard.app.data.db.AppLimitDao
import com.focusguard.app.data.db.AppLimitEntity
import kotlinx.coroutines.flow.Flow

class LimitRepository(private val dao: AppLimitDao) {

    fun observeActive(): Flow<List<AppLimitEntity>> = dao.observeActive()

    /** Create or update the rule for [packageName] (re-activates a previously deleted rule). */
    suspend fun save(
        packageName: String,
        label: String,
        dailyLimitMinutes: Int?,
        blockDuringFocus: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.findByPackage(packageName)
        val row = if (existing != null) existing.copy(
            appLabel = label,
            dailyLimitMinutes = dailyLimitMinutes,
            blockDuringFocus = blockDuringFocus,
            enabled = true,
            deleted = false,
            updatedAt = now,
            syncStatus = existing.syncStatus.afterLocalEdit(),
        ) else AppLimitEntity(
            packageName = packageName,
            appLabel = label,
            dailyLimitMinutes = dailyLimitMinutes,
            blockDuringFocus = blockDuringFocus,
            createdAt = now,
        )
        dao.upsert(row)
    }

    /** Soft delete: row stays (marked deleted) so a future sync can propagate it. */
    suspend fun delete(limit: AppLimitEntity) {
        dao.upsert(
            limit.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                syncStatus = limit.syncStatus.afterLocalEdit(),
            )
        )
    }
}
