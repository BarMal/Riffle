package com.riffle.core.domain.launcher.summon

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A point in screen pixels: x to the right, y down. Angles are in degrees, 0 pointing right, 90 pointing down. */
data class RingPoint(
    val x: Float,
    val y: Float,
) {
    fun distanceTo(other: RingPoint): Float = hypot(other.x - x, other.y - y)

    fun degreesTo(other: RingPoint): Float =
        Math.toDegrees(
            atan2((other.y - y).toDouble(), (other.x - x).toDouble()),
        ).toFloat()
}

/** Where a slot's centre may sit: the window minus the system bars and half a slot. */
data class RingBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(point: RingPoint): Boolean = point.x in left..right && point.y in top..bottom
}

/**
 * Where each slot sits around [centre]: [angles] by slot index (unwrapped, so an arc's angles increase along it).
 * [gap] is the angular spacing between neighbours; [isFullCircle] when nothing had to move to stay on screen.
 */
data class RingLayout(
    val centre: RingPoint,
    val radius: Float,
    val angles: List<Float>,
    val gap: Float,
    val isFullCircle: Boolean,
) {
    fun position(index: Int): RingPoint {
        val a = Math.toRadians(angles[index].toDouble())
        return RingPoint(centre.x + (radius * cos(a)).toFloat(), centre.y + (radius * sin(a)).toFloat())
    }

    /**
     * The slot [finger] points at from the centre, by direction alone (the caller handles the dead zone). The slot in
     * [current] keeps the finger until a neighbour is more than [hysteresisDegrees] nearer, so a thumb resting on a
     * boundary neither flickers nor ticks twice. Directions further than half a gap past an arc's ends pick nothing.
     */
    fun pick(
        finger: RingPoint,
        current: Int?,
        hysteresisDegrees: Float = DEFAULT_HYSTERESIS_DEGREES,
    ): Int? {
        val direction = centre.degreesTo(finger)
        val distances = angles.map { abs(wrap(direction - it)) }
        val best = distances.indices.minByOrNull { distances[it] }
        val tolerance = max(gap / 2f, MIN_PICK_TOLERANCE_DEGREES) + PICK_SLACK_DEGREES
        val held = current?.takeIf { it in angles.indices }
        val keepsHeld =
            held != null && best != null &&
                distances[held] <= tolerance + hysteresisDegrees &&
                distances[held] - distances[best] < hysteresisDegrees
        return if (keepsHeld) held else best?.takeIf { distances[it] <= tolerance }
    }
}

const val DEFAULT_HYSTERESIS_DEGREES: Float = 8f
private const val MIN_PICK_TOLERANCE_DEGREES = 18f
private const val PICK_SLACK_DEGREES = 4f
private const val ANGLE_STEP_DEGREES = 3

/** Radii tried, as multiples of the base radius, before neighbours are squeezed closer than they like. */
private val RADIUS_GROWTH = listOf(1f, 1.13f, 1.26f, 1.39f)

object SummonRingGeometry {
    /** [count] directions clockwise from straight up. */
    fun baseAngles(count: Int): List<Float> = List(count) { -90f + it * 360f / count }

    /**
     * The ring for [count] slots around [centre]. A full circle on [baseAngles] when every slot fits inside [bounds];
     * otherwise the largest arc that fits, with each slot as close to its own direction as the arc allows (slots that
     * already fit stay put, the displaced ones bunch up along the edge). [clearance] is half the distance neighbouring
     * centres keep apart; the ring grows before that is given up.
     */
    fun layout(
        centre: RingPoint,
        count: Int,
        bounds: RingBounds,
        radius: Float,
        clearance: Float,
    ): RingLayout = layout(centre, baseAngles(count), bounds, radius, clearance)

    /** As above, for slots on explicit [base] directions (a folder's level, which leaves a position empty). */
    fun layout(
        centre: RingPoint,
        base: List<Float>,
        bounds: RingBounds,
        radius: Float,
        clearance: Float,
    ): RingLayout {
        val attempts =
            RADIUS_GROWTH.asSequence().mapNotNull {
                    growth ->
                attempt(centre, base, bounds, radius * growth, clearance)
            }
        val comfortable = attempts.firstOrNull { it.comfortable }
        // Nothing fits comfortably: keep whichever radius leaves neighbours furthest apart on screen.
        return comfortable?.layout
            ?: attempts.maxByOrNull { chord(it.layout) }?.layout
            ?: RingLayout(centre, radius, base, smallestSpacing(base), isFullCircle = true)
    }

    private class Attempt(
        val layout: RingLayout,
        val comfortable: Boolean,
    )

    /** The ring at radius [r]: a full circle, an arc (comfortable or squeezed), or null when no point fits at all. */
    private fun attempt(
        centre: RingPoint,
        base: List<Float>,
        bounds: RingBounds,
        r: Float,
        clearance: Float,
    ): Attempt? {
        val fits =
            BooleanArray(
                360 / ANGLE_STEP_DEGREES,
            ) { bounds.contains(pointOn(centre, r, (it * ANGLE_STEP_DEGREES).toFloat())) }
        val arc = longestRun(fits)
        return when {
            fits.all { it } ->
                Attempt(
                    RingLayout(centre, r, base, smallestSpacing(base), isFullCircle = true),
                    comfortable = true,
                )
            arc == null -> null
            else -> {
                val a0 = (arc.first * ANGLE_STEP_DEGREES).toFloat()
                val a1 = ((arc.first + arc.second - 1) * ANGLE_STEP_DEGREES).toFloat()
                val wanted = Math.toDegrees(2.0 * asin((clearance / r).coerceAtMost(1f).toDouble())).toFloat()
                val room = if (base.size <= 1) wanted else (a1 - a0) / (base.size - 1)
                val gap = minOf(wanted, room)
                Attempt(
                    RingLayout(centre, r, fitInto(base, a0, a1, gap), gap, isFullCircle = false),
                    comfortable = room >= wanted,
                )
            }
        }
    }

    private fun smallestSpacing(base: List<Float>): Float {
        if (base.size < 2) return 360f
        val sorted = base.map { ((it % 360f) + 360f) % 360f }.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a } + (sorted.first() + 360f - sorted.last())
        return gaps.min()
    }

    private fun chord(layout: RingLayout): Float =
        (2.0 * layout.radius * sin(Math.toRadians(layout.gap / 2.0))).toFloat()

    private fun pointOn(
        centre: RingPoint,
        r: Float,
        degrees: Float,
    ): RingPoint {
        val a = Math.toRadians(degrees.toDouble())
        return RingPoint(centre.x + (r * cos(a)).toFloat(), centre.y + (r * sin(a)).toFloat())
    }

    /** The longest circular run of true values: its start index and length, or null when nothing fits. */
    private fun longestRun(fits: BooleanArray): Pair<Int, Int>? {
        val n = fits.size
        var best: Pair<Int, Int>? = null
        for (start in 0 until n) {
            if (!fits[start] || fits[(start - 1 + n) % n]) continue
            var length = 0
            while (length < n && fits[(start + length) % n]) length++
            if (best == null || length > best.second) best = start to length
        }
        return best
    }

    /**
     * Places [base] directions on the arc [a0]..[a1] in their circular order (cut opposite the arc's middle), each as
     * near its own direction as possible while keeping [gap] between neighbours.
     */
    private fun fitInto(
        base: List<Float>,
        a0: Float,
        a1: Float,
        gap: Float,
    ): List<Float> {
        val n = base.size
        val mid = (a0 + a1) / 2f
        val order = base.mapIndexed { index, angle -> index to (mid + wrap(angle - mid)) }.sortedBy { it.second }
        val placed =
            order.mapIndexed { k, (_, t) ->
                val earliest = a0 + k * gap
                // When the arc is exactly full the two ends meet; rounding must not leave an empty range.
                t.coerceIn(earliest, max(earliest, a1 - (n - 1 - k) * gap))
            }.toMutableList()
        for (k in 1 until n) placed[k] = max(placed[k], placed[k - 1] + gap)
        for (k in n - 2 downTo 0) placed[k] = minOf(placed[k], placed[k + 1] - gap)
        val angles = FloatArray(n)
        order.forEachIndexed { k, (index, _) -> angles[index] = placed[k] }
        return angles.toList()
    }
}

/** [degrees] wrapped into -180..180. */
internal fun wrap(degrees: Float): Float {
    var a = (degrees + 180f) % 360f
    if (a < 0) a += 360f
    return a - 180f
}
