package com.riffle.core.domain.launcher.home

data class DockModel(
    /** How many pinned icons show at once before the static side scrolls. */
    val capacity: Int,
    val items: List<LauncherItem> = emptyList(),
    val isEnabled: Boolean = true,
    val showNotificationCards: Boolean = false,
    /**
     * How many notification icons show at once before that section scrolls, independent of
     * [capacity] -- the two are separate budgets the user sets on their own terms, not a shared
     * pool one side starves the other out of. Fewer notifications than this shrinks the section
     * rather than padding it out to the full count; more scrolls.
     */
    val notificationSlotCount: Int = DEFAULT_DOCK_NOTIFICATION_SLOT_COUNT,
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
    /**
     * Which edge this dock occupies, or `null` when the user has never chosen one -- in which case
     * the active template's own
     * [com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateVariant.dockPosition] applies,
     * falling back to [DockPosition.LEFT].
     *
     * Per device class, because there is one [DockModel] per device class, shared by every view
     * mode on it ([HomeLayoutSet.docks], #1205). An edge that suits a tablet wastes width on a phone
     * in portrait, and this is where the rest of the dock's configuration already answers that kind
     * of question; it is never per mode, so the dock stays put while the mode changes.
     */
    val position: DockPosition? = null,
    /**
     * A small page of the user's own widgets and shortcuts, shown on the expanded shelf, or `null`
     * when this dock has none.
     *
     * A [LauncherPage] rather than a bespoke panel model, so it is laid out, persisted and
     * (eventually) edited by the same grid machinery as a home page. That is what lets the shelf
     * carry a clock, a media widget or a set of toggles without this codebase growing a component
     * for each: whatever the user has installed, placed on a grid.
     *
     * It is deliberately not where apps past [capacity] go -- those scroll in the dock's own strip.
     * The shelf is for things you consult or act on without leaving where you are.
     */
    val panel: LauncherPage? = null,
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
 * the swipe back: it then does nothing on the dock.
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

const val DEFAULT_DOCK_NOTIFICATION_SLOT_COUNT = 3
const val MIN_DOCK_NOTIFICATION_SLOT_COUNT = 1
const val MAX_DOCK_NOTIFICATION_SLOT_COUNT = 5
const val MIN_DOCK_CAPACITY = 1
const val MAX_DOCK_CAPACITY = 12
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
