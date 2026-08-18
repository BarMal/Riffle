package com.riffle.core.domain.launcher.home

data class DockModel(
    val capacity: Int,
    val items: List<LauncherItem> = emptyList(),
    val isEnabled: Boolean = true,
    val showNotificationCards: Boolean = false,
    val iconSizeDp: Int = DEFAULT_DOCK_ICON_SIZE_DP,
    val backgroundAlphaPercent: Int = DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT,
    val visualEffect: DockVisualEffect = DockVisualEffect.FLAT,
    val backgroundSizing: DockBackgroundSizing = DockBackgroundSizing.DYNAMIC,
    val alignment: DockAlignment = DockAlignment.CENTER,
    val itemSpacingDp: Int = DEFAULT_DOCK_ITEM_SPACING_DP,
    val cornerRadiusDp: Int = DEFAULT_DOCK_CORNER_RADIUS_DP,
    val homeControlsSpacingDp: Int = DEFAULT_DOCK_HOME_CONTROLS_SPACING_DP,
    /**
     * Whether the dock offers its expanded shelf at all. `false` leaves the dock as a plain strip
     * of shortcuts, and frees whatever input the expand affordance would otherwise have claimed.
     */
    val isExpandable: Boolean = true,
    /** How the user reaches the expanded shelf when [isExpandable]. */
    val expandAffordance: DockExpandAffordance = DockExpandAffordance.GESTURE,
) {
    val availableSlots: Int = (capacity - items.size).coerceAtLeast(0)
}

enum class DockBackgroundSizing {
    DYNAMIC,
    FIXED,
}

/**
 * How the expanded dock shelf is reached.
 *
 * [GESTURE] is the original behaviour: swipe up on the dock to expand, down to collapse. It costs a
 * gesture, and it is the affordance a dock on a side edge cannot have -- a swipe inward from the
 * screen's edge is Android's own back gesture, so an edge dock has to offer [BUTTON] instead.
 *
 * [BUTTON] puts a visible control on the dock. It is discoverable rather than learned, and it hands
 * the swipe back to whatever the dock's own swipe-up gesture action is bound to.
 */
enum class DockExpandAffordance {
    GESTURE,
    BUTTON,
}

/** Horizontal placement for a dock that does not consume the available home width. */
enum class DockAlignment {
    START,
    CENTER,
    END,
}

/** Material treatment for the Dock container; colour always comes from the active launcher theme. */
enum class DockVisualEffect {
    FLAT,
    ELEVATED,
    OUTLINED,
}

const val DEFAULT_DOCK_ICON_SIZE_DP = 48
const val MIN_DOCK_ICON_SIZE_DP = 32
const val MAX_DOCK_ICON_SIZE_DP = 56
const val DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT = 72
const val MIN_DOCK_BACKGROUND_ALPHA_PERCENT = 0
const val MAX_DOCK_BACKGROUND_ALPHA_PERCENT = 100
const val DEFAULT_DOCK_ITEM_SPACING_DP = 10
const val MIN_DOCK_ITEM_SPACING_DP = 0
const val MAX_DOCK_ITEM_SPACING_DP = 24
const val DEFAULT_DOCK_CORNER_RADIUS_DP = 32
const val MIN_DOCK_CORNER_RADIUS_DP = 0
const val MAX_DOCK_CORNER_RADIUS_DP = 48
const val DEFAULT_DOCK_HOME_CONTROLS_SPACING_DP = 8
const val MIN_DOCK_HOME_CONTROLS_SPACING_DP = 0
const val MAX_DOCK_HOME_CONTROLS_SPACING_DP = 48
