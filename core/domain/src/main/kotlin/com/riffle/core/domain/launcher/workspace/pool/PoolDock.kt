package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_NOTIFICATION_SLOT_COUNT
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage

/**
 * Everything about a layout's dock that is not an item: the fields of `DockModel` minus `items` and `panel`, plus the
 * edge Library places it on (`HomeLayoutSet.libraryDockEdgesByDeviceClass`), so the dock needs nothing from
 * `HomeLayoutSet` once it is in the pool. Values are stored as given; the dock settings UI owns clamping.
 */
data class PoolDockSettings(
    val capacity: Int = DEFAULT_CAPACITY,
    val isEnabled: Boolean = true,
    val showNotificationCards: Boolean = false,
    val notificationSlotCount: Int = DEFAULT_DOCK_NOTIFICATION_SLOT_COUNT,
    val iconSizeDp: Int = DEFAULT_DOCK_ICON_SIZE_DP,
    val backgroundAlphaPercent: Int = DEFAULT_DOCK_BACKGROUND_ALPHA_PERCENT,
    val visualEffect: DockVisualEffect = DockVisualEffect.FLAT,
    val backgroundSizing: DockBackgroundSizing = DockBackgroundSizing.DYNAMIC,
    val alignment: DockAlignment = DockAlignment.CENTER,
    val itemSpacingDp: Int = DEFAULT_DOCK_ITEM_SPACING_DP,
    val cornerRadiusDp: Int = DEFAULT_DOCK_CORNER_RADIUS_DP,
    val homeControlsSpacingDp: Int = DEFAULT_DOCK_HOME_CONTROLS_SPACING_DP,
    val isExpandable: Boolean = true,
    val expandAffordance: DockExpandAffordance = DockExpandAffordance.GESTURE,
    /** Home's edge; null = the template's or the fallback edge, as `DockModel.position`. */
    val position: DockPosition? = null,
    /** Library's edge; null = `DEFAULT_LIBRARY_DOCK_EDGE`. */
    val libraryPosition: DockPosition? = null,
) {
    /** The `DockModel` these settings describe, holding [items] and [panel]. */
    fun toDockModel(
        items: List<LauncherItem>,
        panel: LauncherPage?,
    ): DockModel =
        DockModel(
            capacity = capacity,
            items = items,
            isEnabled = isEnabled,
            showNotificationCards = showNotificationCards,
            notificationSlotCount = notificationSlotCount,
            iconSizeDp = iconSizeDp,
            backgroundAlphaPercent = backgroundAlphaPercent,
            visualEffect = visualEffect,
            backgroundSizing = backgroundSizing,
            alignment = alignment,
            itemSpacingDp = itemSpacingDp,
            cornerRadiusDp = cornerRadiusDp,
            homeControlsSpacingDp = homeControlsSpacingDp,
            isExpandable = isExpandable,
            expandAffordance = expandAffordance,
            position = position,
            panel = panel,
        )

    companion object {
        const val DEFAULT_CAPACITY = 5

        /** The settings of [dock] (its items and panel are not settings), with Library's edge [libraryPosition]. */
        fun of(
            dock: DockModel,
            libraryPosition: DockPosition? = null,
        ): PoolDockSettings =
            PoolDockSettings(
                capacity = dock.capacity,
                isEnabled = dock.isEnabled,
                showNotificationCards = dock.showNotificationCards,
                notificationSlotCount = dock.notificationSlotCount,
                iconSizeDp = dock.iconSizeDp,
                backgroundAlphaPercent = dock.backgroundAlphaPercent,
                visualEffect = dock.visualEffect,
                backgroundSizing = dock.backgroundSizing,
                alignment = dock.alignment,
                itemSpacingDp = dock.itemSpacingDp,
                cornerRadiusDp = dock.cornerRadiusDp,
                homeControlsSpacingDp = dock.homeControlsSpacingDp,
                isExpandable = dock.isExpandable,
                expandAffordance = dock.expandAffordance,
                position = dock.position,
                libraryPosition = libraryPosition,
            )
    }
}

/**
 * The layout's one dock, shared by every workspace (pins are never per workspace, Q17). [pins] reference pool items in
 * dock order (pins have no cell); [panel] is the optional dock panel page (widgets and shortcuts on the expanded
 * shelf).
 *
 * Dock items are exclusive to the dock: an item the dock references is referenced by no arrangement. The dock is drawn
 * beside every workspace's home pages, so sharing an item with an arrangement would put one `LauncherItemId` twice into
 * the projected `HomeLayout` (and one widget instance twice on screen). An item is referenced at most once in the dock
 * (pins and panel together).
 */
data class PoolDock(
    val pins: List<PoolItemId> = emptyList(),
    val panel: ArrangementPage? = null,
    val settings: PoolDockSettings = PoolDockSettings(),
) {
    /** Every item the dock references: pins, then the panel's placements. */
    val referenced: Set<PoolItemId>
        get() = (pins + panel?.placements?.map { it.item }.orEmpty()).toSet()
}
