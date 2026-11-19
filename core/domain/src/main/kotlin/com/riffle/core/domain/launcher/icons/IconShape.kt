package com.riffle.core.domain.launcher.icons

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The mask every app icon is drawn through (Settings > Appearance > Icons > "Icon shape"). [SYSTEM] is today's look:
 * the icon is drawn exactly as the platform draws it, with no mask of ours. [storedId] is a stored contract.
 */
enum class IconShape(
    val storedId: String,
) {
    SYSTEM("system"),
    CIRCLE("circle"),
    SQUIRCLE("squircle"),
    ROUNDED_SQUARE("rounded_square"),
    SQUARE("square"),
    TEARDROP("teardrop"),
    ;

    companion object {
        val DEFAULT: IconShape = SYSTEM

        fun decode(stored: String?): IconShape = entries.firstOrNull { it.storedId == stored } ?: DEFAULT
    }
}

/** One drawing command of a mask outline, in a [IconShapeGeometry.VIEWPORT] by [IconShapeGeometry.VIEWPORT] box. */
sealed interface MaskCommand {
    data class MoveTo(val x: Float, val y: Float) : MaskCommand

    data class LineTo(val x: Float, val y: Float) : MaskCommand

    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : MaskCommand

    data object Close : MaskCommand
}

/**
 * Pure geometry of the icon shapes: the mask outlines and the scale factors that keep an icon from being cropped by
 * them. No platform types, so the numbers are unit-tested on the JVM; the app turns the commands into a `Path`.
 */
object IconShapeGeometry {
    /** The side of the box the outlines are described in. */
    const val VIEWPORT = 100f

    /**
     * An adaptive icon's layers are 108 dp wide and only the central 72 dp are visible: a layer is drawn this many
     * times wider than the icon, centred on it, and the mask cuts the icon out of its middle.
     */
    const val ADAPTIVE_LAYER_SCALE = 108f / 72f

    /** A legacy icon is never drawn larger than this fraction of its plate, whatever the shape would allow. */
    const val MAX_LEGACY_CONTENT_FRACTION = 0.86f

    // The corner radius, as a fraction of the box, of each rounded-rectangle shape (a circle is the limit, 0.5).
    private const val CIRCLE_RADIUS = 0.5f
    private const val ROUNDED_SQUARE_RADIUS = 0.26f
    private const val SQUARE_RADIUS = 0.04f
    private const val TEARDROP_SHARP_RADIUS = 0.1f

    // The handle length of a quarter-circle cubic, and of the cubic that approximates a quarter of the n = 4
    // superellipse (x^4 + y^4 = 1), found by matching the curve's midpoint to the diagonal point 2^(-1/4).
    private const val QUARTER_CIRCLE_HANDLE = 0.5523f
    private const val SQUIRCLE_HANDLE = 0.909f

    /** The outline of [shape] in a [VIEWPORT] box, or null for [IconShape.SYSTEM] (the platform draws its own). */
    fun outline(shape: IconShape): List<MaskCommand>? =
        when (shape) {
            IconShape.SYSTEM -> null
            IconShape.CIRCLE -> roundedRect(CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS)
            IconShape.SQUIRCLE -> squircle()
            IconShape.ROUNDED_SQUARE ->
                roundedRect(ROUNDED_SQUARE_RADIUS, ROUNDED_SQUARE_RADIUS, ROUNDED_SQUARE_RADIUS, ROUNDED_SQUARE_RADIUS)
            IconShape.SQUARE -> roundedRect(SQUARE_RADIUS, SQUARE_RADIUS, SQUARE_RADIUS, SQUARE_RADIUS)
            IconShape.TEARDROP ->
                roundedRect(CIRCLE_RADIUS, TEARDROP_SHARP_RADIUS, CIRCLE_RADIUS, CIRCLE_RADIUS)
        }

    /**
     * The fraction of the shape's box that a centred square can fill without touching outside the shape: the most a
     * legacy (non-adaptive) icon can be scaled to and still not be cropped. 1 for [IconShape.SYSTEM], which is not
     * masked by us.
     */
    fun inscribedSquareFraction(shape: IconShape): Float =
        when (shape) {
            IconShape.SYSTEM -> 1f
            IconShape.CIRCLE -> 1f - CORNER_INSET * CIRCLE_RADIUS
            IconShape.SQUIRCLE -> 2f * SQUIRCLE_DIAGONAL
            IconShape.ROUNDED_SQUARE -> 1f - CORNER_INSET * ROUNDED_SQUARE_RADIUS
            IconShape.SQUARE -> 1f - CORNER_INSET * SQUARE_RADIUS
            IconShape.TEARDROP -> 1f - CORNER_INSET * CIRCLE_RADIUS
        }

    /** The fraction of its plate a legacy icon is drawn at inside [shape]: the inscribed square, held to the cap. */
    fun legacyContentFraction(shape: IconShape): Float =
        inscribedSquareFraction(shape).coerceAtMost(MAX_LEGACY_CONTENT_FRACTION)

    // A square in a corner arc of radius r (fraction of the box) has half side 0.5 - r + r / sqrt(2), so it fills
    // 1 - (2 - sqrt(2)) * r of the box.
    private val CORNER_INSET = (2.0 - sqrt(2.0)).toFloat()

    // The half side, as a fraction of the box, of the square whose corners touch the n = 4 superellipse: 2^(-1/4) / 2.
    private val SQUIRCLE_DIAGONAL = (2.0.pow(-0.25) / 2.0).toFloat()

    private fun roundedRect(
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): List<MaskCommand> {
        val size = VIEWPORT
        val tl = topLeft * size
        val tr = topRight * size
        val br = bottomRight * size
        val bl = bottomLeft * size
        return listOf(
            MaskCommand.MoveTo(tl, 0f),
            MaskCommand.LineTo(size - tr, 0f),
            corner(size - tr, 0f, size, 0f, size, tr),
            MaskCommand.LineTo(size, size - br),
            corner(size, size - br, size, size, size - br, size),
            MaskCommand.LineTo(bl, size),
            corner(bl, size, 0f, size, 0f, size - bl),
            MaskCommand.LineTo(0f, tl),
            corner(0f, tl, 0f, 0f, tl, 0f),
            MaskCommand.Close,
        )
    }

    // A quarter arc from A to B around the box corner C: each handle runs from its end towards C by the arc handle.
    private fun corner(
        ax: Float,
        ay: Float,
        cx: Float,
        cy: Float,
        bx: Float,
        by: Float,
    ): MaskCommand =
        MaskCommand.CubicTo(
            x1 = ax + (cx - ax) * QUARTER_CIRCLE_HANDLE,
            y1 = ay + (cy - ay) * QUARTER_CIRCLE_HANDLE,
            x2 = bx + (cx - bx) * QUARTER_CIRCLE_HANDLE,
            y2 = by + (cy - by) * QUARTER_CIRCLE_HANDLE,
            x = bx,
            y = by,
        )

    private fun squircle(): List<MaskCommand> {
        val half = VIEWPORT / 2f
        val reach = half * SQUIRCLE_HANDLE
        val end = VIEWPORT
        return listOf(
            MaskCommand.MoveTo(half, 0f),
            MaskCommand.CubicTo(half + reach, 0f, end, half - reach, end, half),
            MaskCommand.CubicTo(end, half + reach, half + reach, end, half, end),
            MaskCommand.CubicTo(half - reach, end, 0f, half + reach, 0f, half),
            MaskCommand.CubicTo(0f, half - reach, half - reach, 0f, half, 0f),
            MaskCommand.Close,
        )
    }
}
