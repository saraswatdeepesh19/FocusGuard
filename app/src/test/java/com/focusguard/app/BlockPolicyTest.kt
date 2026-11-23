package com.focusguard.app

import com.focusguard.app.data.db.AppLimitEntity
import com.focusguard.app.data.db.BlockReason
import com.focusguard.app.domain.BlockDecision
import com.focusguard.app.domain.BlockPolicy
import com.focusguard.app.domain.StrictRules
import com.focusguard.app.util.formatClock
import com.focusguard.app.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {

    private fun limit(minutes: Int?, focus: Boolean = false) = AppLimitEntity(
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        dailyLimitMinutes = minutes,
        blockDuringFocus = focus,
    )

    @Test fun noRuleMeansAllow() {
        assertEquals(BlockDecision.Allow, BlockPolicy.decide(null, 10_000_000L, focusActive = true))
    }

    @Test fun underLimitIsAllowed() {
        assertEquals(BlockDecision.Allow, BlockPolicy.decide(limit(30), 29 * 60_000L, focusActive = false))
    }

    @Test fun reachingLimitBlocks() {
        assertEquals(
            BlockDecision.Block(BlockReason.DAILY_LIMIT),
            BlockPolicy.decide(limit(30), 30 * 60_000L, focusActive = false),
        )
    }

    @Test fun focusBlocksEvenUnderLimit() {
        assertEquals(
            BlockDecision.Block(BlockReason.FOCUS_SESSION),
            BlockPolicy.decide(limit(30, focus = true), 0L, focusActive = true),
        )
    }

    @Test fun focusIgnoredWhenAppNotMarkedForFocus() {
        assertEquals(BlockDecision.Allow, BlockPolicy.decide(limit(30, focus = false), 0L, focusActive = true))
    }

    @Test fun disabledOrDeletedRulesNeverBlock() {
        assertEquals(BlockDecision.Allow, BlockPolicy.decide(limit(1).copy(enabled = false), 999_999L, false))
        assertEquals(BlockDecision.Allow, BlockPolicy.decide(limit(1).copy(deleted = true), 999_999L, false))
    }

    @Test fun strictModeRules() {
        val original = limit(30, focus = true)
        assertFalse(StrictRules.isLoosening(null, 60, false))          // new rule
        assertFalse(StrictRules.isLoosening(original, 15, true))       // stricter
        assertTrue(StrictRules.isLoosening(original, 45, true))        // more minutes
        assertTrue(StrictRules.isLoosening(original, null, true))      // removed cap
        assertTrue(StrictRules.isLoosening(original, 30, false))       // stopped focus block
        assertFalse(StrictRules.isLoosening(limit(null, true), 60, true)) // adding a cap
    }

    @Test fun formatting() {
        assertEquals("<1m", formatDuration(59_000L))
        assertEquals("25m", formatDuration(25 * 60_000L))
        assertEquals("1h 30m", formatDuration(90 * 60_000L))
        assertEquals("2h", formatDuration(120 * 60_000L))
        assertEquals("24:59", formatClock(1_499_000L))
        assertEquals("0:00", formatClock(-5L))
    }
}
