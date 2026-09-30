package com.riffle.app.launcher.expressions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import kotlin.math.abs

/**
 * Which card of an expression stack is focused, plus the counters the shared `CardStack` needs to
 * animate a change: [settleTransitionId] increments on every focus change and [settleStepCount]
 * says how many cards it skipped, so a long fling travels proportionally longer.
 *
 * Bounds are passed per call because the item count changes as the lens result does; [clamped]
 * keeps a stale index safe when the result shrinks.
 */
internal class CardStackFocusState(initialIndex: Int = 0) {
    var index: Int by mutableIntStateOf(initialIndex)
        private set
    var settleTransitionId: Int by mutableIntStateOf(0)
        private set
    var settleStepCount: Int by mutableIntStateOf(1)
        private set

    /** The focused index kept inside a stack of [count] cards. */
    fun clamped(count: Int): Int = index.coerceIn(0, (count - 1).coerceAtLeast(0))

    /** Focuses [target] (clamped into [count] cards). Returns true when focus actually changed. */
    fun moveTo(
        target: Int,
        count: Int,
    ): Boolean {
        val from = clamped(count)
        val to = target.coerceIn(0, (count - 1).coerceAtLeast(0))
        val changed = to != from
        if (changed) {
            settleStepCount = abs(to - from).coerceAtLeast(1)
            settleTransitionId++
        }
        index = to
        return changed
    }

    /** One card towards [direction]. Returns false at either end, where there is nowhere to go. */
    fun step(
        direction: CardStackNavigationDirection,
        count: Int,
    ): Boolean {
        val delta = if (direction == CardStackNavigationDirection.NEXT) 1 else -1
        return moveTo(clamped(count) + delta, count)
    }
}
