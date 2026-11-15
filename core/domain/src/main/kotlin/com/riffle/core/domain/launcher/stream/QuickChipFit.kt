package com.riffle.core.domain.launcher.stream

/**
 * How a row of quick-action chips fits the room it has (ADR 0003, Amendment 3, "Quick actions"): chips show whole when
 * they can. When their natural widths do not fit, every chip wider than a common cap gives up its label's end (an
 * ellipsis) so that all of them fit; only when even that would leave a chip narrower than [minChipPx] does the row
 * keep their natural widths and scroll. Pure arithmetic in px, so a chip is never cut mid-word by the row's edge
 * when it
 * could have been made to fit.
 */
object QuickChipFit {
    /** The widths to measure each chip at, or null when they cannot all be at least [minChipPx] and fit. */
    @Suppress("ReturnCount")
    fun fitted(
        naturalPx: List<Int>,
        gapPx: Int,
        availablePx: Int,
        minChipPx: Int,
    ): List<Int>? {
        if (naturalPx.isEmpty()) return naturalPx
        val room = availablePx - gapPx * (naturalPx.size - 1)
        if (naturalPx.sum() <= room) return naturalPx
        if (naturalPx.size * minChipPx > room) return null
        // The widest cap at which the capped widths still sum to the room: lowered one px at a time is exact but slow;
        // the capped sum is monotone in the cap, so bisect.
        var low = minChipPx
        var high = naturalPx.max()
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (naturalPx.sumOf { minOf(it, mid) } <= room) low = mid else high = mid - 1
        }
        return naturalPx.map { minOf(it, low) }
    }
}
