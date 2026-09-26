package com.riffle.app.launcher.designsystem

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.ContinuousCornerGeometry
import com.riffle.core.domain.launcher.designsystem.RiffleRadiusScale

/** Riffle's single radius scale plus the continuous-corner shape that renders it. */
object RiffleShapes {
    val radiusS: Dp = RiffleRadiusScale.S.dp
    val radiusM: Dp = RiffleRadiusScale.M.dp
    val radiusL: Dp = RiffleRadiusScale.L.dp
    val radiusXl: Dp = RiffleRadiusScale.XL.dp

    val small: CornerBasedShape = continuous(radiusS)
    val medium: CornerBasedShape = continuous(radiusM)
    val large: CornerBasedShape = continuous(radiusL)
    val extraLarge: CornerBasedShape = continuous(radiusXl)

    /** Pill / circle. Continuous smoothing has no room on a fully rounded edge, so this stays circular. */
    val full: CornerBasedShape = RoundedCornerShape(percent = 50)

    fun continuous(
        radius: Dp,
        smoothing: Float = ContinuousCornerGeometry.DEFAULT_SMOOTHING,
    ): CornerBasedShape = ContinuousRoundedCornerShape(CornerSize(radius), smoothing)

    /**
     * Material 3 shape roles mapped onto the Riffle radius scale. [square] collapses every role to
     * sharp corners for presets whose corner token is zero (Terminal), so Material components agree
     * with the launcher's own card and panel shapes.
     */
    fun materialShapes(square: Boolean = false): Shapes =
        if (square) {
            val sharp = continuous(0.dp)
            Shapes(
                extraSmall = sharp,
                small = sharp,
                medium = sharp,
                large = sharp,
                extraLarge = sharp,
            )
        } else {
            Shapes(
                extraSmall = small,
                small = small,
                medium = medium,
                large = large,
                extraLarge = extraLarge,
            )
        }
}

/**
 * A rounded rectangle whose corners blend into the edges with continuous curvature (a squircle
 * approximation) instead of the abrupt start of a circular arc. See [ContinuousCornerGeometry].
 *
 * Behaves like [RoundedCornerShape] for sizing (dp, px or percent corners, RTL-aware start/end) so
 * Material components can `copy` individual corners.
 */
class ContinuousRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = ContinuousCornerGeometry.DEFAULT_SMOOTHING,
) : CornerBasedShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
    ) {
    constructor(
        all: CornerSize,
        smoothing: Float = ContinuousCornerGeometry.DEFAULT_SMOOTHING,
    ) : this(all, all, all, all, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        val geometry =
            ContinuousCornerGeometry.rectOutline(
                width = size.width,
                height = size.height,
                topLeft = if (ltr) topStart else topEnd,
                topRight = if (ltr) topEnd else topStart,
                bottomRight = if (ltr) bottomEnd else bottomStart,
                bottomLeft = if (ltr) bottomStart else bottomEnd,
                smoothing = smoothing,
            )
        val path = Path()
        geometry.corners.forEachIndexed { index, corner ->
            if (index == 0) {
                path.moveTo(corner.start.x, corner.start.y)
            } else {
                path.lineTo(corner.start.x, corner.start.y)
            }
            corner.cubics.forEach { cubic ->
                path.cubicTo(
                    cubic.control1.x,
                    cubic.control1.y,
                    cubic.control2.x,
                    cubic.control2.y,
                    cubic.end.x,
                    cubic.end.y,
                )
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = ContinuousRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousRoundedCornerShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousRoundedCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = " +
            "$bottomEnd, bottomStart = $bottomStart, smoothing = $smoothing)"
}
