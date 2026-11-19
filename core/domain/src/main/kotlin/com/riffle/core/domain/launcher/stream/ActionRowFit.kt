package com.riffle.core.domain.launcher.stream

/**
 * How the player's actions row (the Dock's, or the card's) is laid out: the transport (previous, play or pause,
 * next) is a group centred on the row, and the secondary actions ("Open player", a collapse chevron) sit at its two
 * ends when they fit beside it, else on a second row spread evenly. Pure arithmetic over measured widths, so the rule
 * is tested without a screen.
 */
object ActionRowFit {
    /**
     * Whether a [centerPx] wide group stays truly centred on a row [availablePx] wide with [startPx] before it and
     * [endPx] after it, each kept [gapPx] clear of it. The group is centred, so the wider side decides: the other side
     * is given the same room. With nothing at either end the group only has to fit.
     */
    fun centredFits(
        startPx: Int,
        centerPx: Int,
        endPx: Int,
        availablePx: Int,
        gapPx: Int,
    ): Boolean {
        val side = maxOf(startPx, endPx)
        val needed = if (side == 0) centerPx else centerPx + 2 * (side + gapPx)
        return needed <= availablePx
    }

    /**
     * The x offsets of [widthsPx] spread evenly over [availablePx]: the same space before the first, between each pair
     * and after the last. The space is never negative (a row that does not fit is packed from the start).
     */
    fun evenlySpread(
        widthsPx: List<Int>,
        availablePx: Int,
    ): List<Int> {
        if (widthsPx.isEmpty()) return emptyList()
        val space = ((availablePx - widthsPx.sum()) / (widthsPx.size + 1)).coerceAtLeast(0)
        var x = space
        return widthsPx.map { width -> x.also { x += width + space } }
    }
}
