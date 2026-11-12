package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
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
 * [resolveDockPosition] falls back to. Narrow postures keep the dock along the bottom; wide postures put it on the
 * left edge. Every class is mapped, so [resolveDockPosition]'s own `LEFT` fallback stays unreached.
 */
internal val HomeLayoutDeviceClass.templateDockPosition: DockPosition
    get() =
        when (this) {
            HomeLayoutDeviceClass.PHONE,
            HomeLayoutDeviceClass.PHONE_LANDSCAPE,
            -> DockPosition.BOTTOM

            HomeLayoutDeviceClass.FOLDABLE,
            HomeLayoutDeviceClass.TABLET,
            HomeLayoutDeviceClass.DESKTOP,
            -> DockPosition.LEFT
        }

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
