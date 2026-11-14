package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.defaultDockEdge
import kotlin.math.max

/**
 * `null` [configuredDockPosition] means the user has never chosen a dock edge for this layout, so the device class's
 * [templateDockPosition] applies; once the user picks an edge in settings it always wins.
 */
internal fun resolveDockPosition(
    configuredDockPosition: DockPosition?,
    templateDockPosition: DockPosition?,
): DockPosition = configuredDockPosition ?: templateDockPosition ?: DockPosition.LEFT

/**
 * The dock edge a device class defaults to before the user picks one -- the [templateDockPosition] that
 * [resolveDockPosition] falls back to (ADR 0003, Amendment 2): the bottom folded, the right edge on an unfolded
 * foldable, and the left edge on a tablet or a desktop window. Every class is mapped, so [resolveDockPosition]'s own
 * `LEFT` fallback stays unreached.
 */
internal val HomeLayoutDeviceClass.templateDockPosition: DockPosition
    get() = defaultDockEdge

/** The WCAG contrast ratio between two colours (1 to 21). */
internal fun contrastRatio(
    first: Color,
    second: Color,
): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}
