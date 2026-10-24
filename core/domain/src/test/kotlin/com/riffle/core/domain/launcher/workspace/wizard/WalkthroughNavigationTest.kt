package com.riffle.core.domain.launcher.workspace.wizard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Next and Back from a step that has just left the sequence: the host rebuilds the context after a permission result,
 * and once every permission is granted the Permissions step is no longer listed while the user is still on it.
 */
class WalkthroughNavigationTest {
    private fun grantedEverything(): Pair<WalkthroughState, WalkthroughContext> {
        val asking = walkthroughContext(access = NOTHING_GRANTED)
        var current = WalkthroughFlow.start(asking)
        while (current.step != WalkthroughStep.PERMISSIONS) current = asking.run(WalkthroughAction.Next, from = current)
        val granted = walkthroughContext()
        assertEquals(emptyList(), WalkthroughFlow.neededPermissions(current, granted))
        assertEquals(false, WalkthroughStep.PERMISSIONS in WalkthroughFlow.steps(current, granted))
        return current to granted
    }

    @Test
    fun nextLeavesForTheReviewNotTheStart() {
        val (state, granted) = grantedEverything()
        val next = granted.run(WalkthroughAction.Next, from = state)
        assertEquals(WalkthroughStep.REVIEW, next.step)
        assertNull(next.exit)
    }

    @Test
    fun backReturnsToTheStepBefore() {
        val (state, granted) = grantedEverything()
        val back = granted.run(WalkthroughAction.Back, from = state)
        assertEquals(WalkthroughStep.DOCK, back.step)
        assertNull(back.exit)
    }

    @Test
    fun listedStepsMoveAsBefore() {
        val ctx = walkthroughContext(access = NOTHING_GRANTED)
        val steps = WalkthroughFlow.steps(WalkthroughFlow.start(ctx), ctx)
        var state = WalkthroughFlow.start(ctx)
        val visited = mutableListOf(state.step)
        while (state.step != WalkthroughStep.DONE) {
            state = ctx.run(WalkthroughAction.Next, from = state)
            if (visited.last() != state.step) visited += state.step
        }
        assertEquals(steps, visited)
        assertEquals(WalkthroughExit.Finished, state.exit)
    }
}
