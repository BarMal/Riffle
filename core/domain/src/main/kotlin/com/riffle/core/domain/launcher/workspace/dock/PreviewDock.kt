package com.riffle.core.domain.launcher.workspace.dock

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.workspace.DockPresentation

/** One pinned dock entry the preview can draw and activate. */
sealed interface PreviewDockPin {
    /** Stable within the dock: the `LauncherItemId` of the pinned item. */
    val key: String
    val label: String

    data class App(val item: AppShortcutItem) : PreviewDockPin {
        override val key: String get() = item.id.value
        override val label: String get() = item.label
    }

    data class Folder(val item: FolderItem) : PreviewDockPin {
        override val key: String get() = item.id.value
        override val label: String get() = item.label
    }
}

/**
 * What the Workspaces preview dock draws for its pinned section, derived from the device class's [DockModel].
 *
 * [pins] are the pinned apps and folders in dock order; [unsupportedCount] counts pinned items the preview does
 * not draw (hosted widgets). [iconSizeDp] and [itemSpacingDp] are the user's dock settings clamped to their
 * documented ranges. [isShown] is `false` when the user hid the dock (`DockModel.isEnabled`): the preview keeps a
 * bar so the workspace menu handle stays anchored, but draws no pins.
 *
 * [isHiddenHere] is the shown workspace's own "no dock" override (11.2): then there is no bar at all and nothing is
 * reserved. [edge] is the edge the preview draws the bar on: the workspace's edge override, else the bottom (as the
 * preview always drew it). The preview has its top bar on the top edge, so a top dock is drawn at the bottom there.
 */
data class PreviewDockModel(
    val pins: List<PreviewDockPin>,
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
    val isShown: Boolean,
    val unsupportedCount: Int,
    val edge: DockPosition = DockPosition.BOTTOM,
    val isHiddenHere: Boolean = false,
) {
    /** Whether the bar runs down a side edge (pins in a column) instead of along the bottom. */
    val isVertical: Boolean get() = edge == DockPosition.LEFT || edge == DockPosition.RIGHT

    /** The space the bar takes from the pages on its [edge]: none when the workspace hides the dock. */
    val reservedExtentDp: Int get() = if (isHiddenHere) 0 else barHeightDp

    /** Whether there is nothing pinned to draw. */
    val hasNoPins: Boolean get() = !isShown || pins.isEmpty()

    /** The bar's height for these settings, never below [PreviewDock.MIN_BAR_HEIGHT_DP]. */
    val barHeightDp: Int get() = PreviewDock.barHeightDp(iconSizeDp)

    /** The square each pin gets: the icon, but never less than the 48dp touch target. */
    val targetSizeDp: Int get() = PreviewDock.targetSizeDp(iconSizeDp)
}

/** Pure rules of the preview dock: what is pinned, how large it is, how it is announced. */
object PreviewDock {
    const val MIN_BAR_HEIGHT_DP = 72
    const val MIN_TARGET_DP = 48
    private const val BAR_VERTICAL_PADDING_DP = 12

    /** Pins from the shared dock of the active device class; `null` (no dock stored) reads as an empty dock. */
    fun from(dock: DockModel?): PreviewDockModel {
        val items = dock?.items.orEmpty()
        val pins =
            items.mapNotNull { item ->
                when (item) {
                    is AppShortcutItem -> PreviewDockPin.App(item)
                    is FolderItem -> PreviewDockPin.Folder(item)
                    else -> null
                }
            }
        return PreviewDockModel(
            pins = pins.distinctBy { it.key },
            iconSizeDp =
                (dock?.iconSizeDp ?: DEFAULT_DOCK_ICON_SIZE_DP)
                    .coerceIn(MIN_DOCK_ICON_SIZE_DP, MAX_DOCK_ICON_SIZE_DP),
            itemSpacingDp =
                (dock?.itemSpacingDp ?: DEFAULT_DOCK_ITEM_SPACING_DP)
                    .coerceIn(MIN_DOCK_ITEM_SPACING_DP, MAX_DOCK_ITEM_SPACING_DP),
            isShown = dock?.isEnabled ?: true,
            unsupportedCount = items.size - pins.size,
        )
    }

    /**
     * The preview dock while a workspace with [presentation] is shown: the shared [dock] with the override layered on
     * (`EffectiveDock.apply`; pass the presentation after the stranding rule, `EffectiveDock.presentationFor`).
     */
    fun from(
        dock: DockModel?,
        presentation: DockPresentation?,
    ): PreviewDockModel {
        val effective = dock?.let { EffectiveDock.apply(it, presentation) }
        val base = from(effective ?: dock)
        // The preview has always drawn the shared dock at the bottom; only a workspace's own edge moves it.
        val edge = presentation?.edge ?: DockPosition.BOTTOM
        val iconSize = presentation?.iconSizeDp ?: base.iconSizeDp
        return base.copy(
            iconSizeDp = iconSize.coerceIn(MIN_DOCK_ICON_SIZE_DP, MAX_DOCK_ICON_SIZE_DP),
            isShown = base.isShown && presentation?.hidden != true,
            edge = if (edge == DockPosition.TOP) DockPosition.BOTTOM else edge,
            isHiddenHere = presentation?.hidden == true,
        )
    }

    fun targetSizeDp(iconSizeDp: Int): Int = maxOf(iconSizeDp, MIN_TARGET_DP)

    fun barHeightDp(iconSizeDp: Int): Int =
        maxOf(MIN_BAR_HEIGHT_DP, targetSizeDp(iconSizeDp) + BAR_VERTICAL_PADDING_DP * 2)

    /** What a screen reader says for [pin]: the app's label, or the folder with how many apps it holds. */
    fun spokenLabel(pin: PreviewDockPin): String =
        when (pin) {
            is PreviewDockPin.App -> pin.label
            is PreviewDockPin.Folder -> {
                val count = pin.item.items.size
                "${pin.label} folder, $count ${if (count == 1) "app" else "apps"}"
            }
        }

    /** The click label (what double tap does) for [pin]. */
    fun actionLabel(pin: PreviewDockPin): String =
        when (pin) {
            is PreviewDockPin.App -> "Open ${pin.label}"
            is PreviewDockPin.Folder -> "Open ${pin.label} folder"
        }
}
