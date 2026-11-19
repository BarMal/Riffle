package com.riffle.core.domain.launcher.gestures

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldMenuRuleTest {
    @Test
    fun aHoldThatStaysWithinSlopKeepsItsMenu() {
        assertFalse(HoldMenuRule.leavesMenu(3f, 4f, touchSlopPx = 8f))
        assertFalse(HoldMenuRule.leavesMenu(0f, 0f, touchSlopPx = 8f))
    }

    @Test
    fun movingPastSlopInAnyDirectionLeavesTheMenu() {
        assertTrue(HoldMenuRule.leavesMenu(9f, 0f, touchSlopPx = 8f))
        assertTrue(HoldMenuRule.leavesMenu(0f, -9f, touchSlopPx = 8f))
        assertTrue(HoldMenuRule.leavesMenu(7f, 7f, touchSlopPx = 8f))
    }
}
