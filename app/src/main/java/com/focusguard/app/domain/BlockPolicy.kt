package com.focusguard.app.domain

import com.focusguard.app.data.db.AppLimitEntity
import com.focusguard.app.data.db.BlockReason

sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(val reason: BlockReason) : BlockDecision
}

/**
 * The single source of truth for "should this app be blocked right now?".
 * Pure function with no Android dependencies, so it's covered by plain unit tests.
 */
object BlockPolicy {

    fun decide(limit: AppLimitEntity?, usedTodayMs: Long, focusActive: Boolean): BlockDecision {
        if (limit == null || !limit.enabled || limit.deleted) return BlockDecision.Allow
        if (focusActive && limit.blockDuringFocus) return BlockDecision.Block(BlockReason.FOCUS_SESSION)
        val capMinutes = limit.dailyLimitMinutes ?: return BlockDecision.Allow
        return if (usedTodayMs >= capMinutes * 60_000L) {
            BlockDecision.Block(BlockReason.DAILY_LIMIT)
        } else {
            BlockDecision.Allow
        }
    }
}

/** Strict mode: the user can make rules stricter, never looser. */
object StrictRules {

    /**
     * True if changing [original] to the new values would make the rule weaker:
     * removing the daily cap, raising it, or no longer blocking during focus.
     */
    fun isLoosening(
        original: AppLimitEntity?,
        newDailyLimitMinutes: Int?,
        newBlockDuringFocus: Boolean,
    ): Boolean {
        if (original == null) return false // adding a new rule is always stricter
        val oldCap = original.dailyLimitMinutes
        val capLoosened = oldCap != null && (newDailyLimitMinutes == null || newDailyLimitMinutes > oldCap)
        val focusLoosened = original.blockDuringFocus && !newBlockDuringFocus
        return capLoosened || focusLoosened
    }
}
