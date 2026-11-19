package com.riffle.core.domain.launcher.home

data class DockModel(
    /** How many pinned icons show at once before the static side scrolls. */
    val capacity: Int,
    val items: List<LauncherItem> = emptyList(),
    val isEnabled: Boolean = true,
    /**
     * How many live apps (apps with something new) the dynamic side shows, between [MIN_DOCK_LIVE_APP_SLOTS] (none:
     * the side holds only now playing) and [MAX_DOCK_LIVE_APP_SLOTS]. It replaces the earlier
     * "show notification cards" switch and "notification slot count" (ADR 0003, The Dock is the handle); the dynamic
     * side is drawn in full ahead of the pinned icons, and more live apps than this are reached in the Stream.
     */
    val liveAppSlots: Int = DEFAULT_DOCK_LIVE_APP_SLOTS,
    val iconSizeDp: Int = DEFAULT_DOCK_ICON_SIZE_DP,
    val backgroundAlphaPercent: Int = DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT,
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

const val DEFAULT_DOCK_LIVE_APP_SLOTS = 3
const val MIN_DOCK_LIVE_APP_SLOTS = 0
const val MAX_DOCK_LIVE_APP_SLOTS = 5
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

/**
 * The live app slots a dock stored before the Stream means, from the two settings they replaced: "show notification
 * cards" and "notification slot count". Showing the cards keeps the slot count (clamped); not showing them keeps the
 * user's choice of no live apps; a dock that never stored either takes the default.
 */
fun dockLiveAppSlotsFromLegacy(
    showNotificationCards: Boolean?,
    notificationSlotCount: Int?,
): Int =
    when (showNotificationCards) {
        true ->
            (notificationSlotCount ?: DEFAULT_DOCK_LIVE_APP_SLOTS).coerceIn(
                MIN_DOCK_LIVE_APP_SLOTS,
                MAX_DOCK_LIVE_APP_SLOTS,
            )
        false -> MIN_DOCK_LIVE_APP_SLOTS
        null -> DEFAULT_DOCK_LIVE_APP_SLOTS
    }
