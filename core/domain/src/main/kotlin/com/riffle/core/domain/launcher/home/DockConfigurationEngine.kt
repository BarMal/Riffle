package com.riffle.core.domain.launcher.home

class DockConfigurationEngine {
    fun setDockEnabled(
        layout: HomeLayout,
        enabled: Boolean,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(isEnabled = enabled),
            ),
        )

    fun setDockCapacity(
        layout: HomeLayout,
        capacity: Int,
    ): DockEditResult =
        when {
            capacity < MIN_DOCK_CAPACITY -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_CAPACITY)
            capacity < layout.dock.items.size ->
                DockEditResult.Rejected(DockEditRejectionReason.CAPACITY_BELOW_ITEM_COUNT)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock = layout.dock.copy(capacity = capacity),
                    ),
                )
        }

    fun setDockIconSize(
        layout: HomeLayout,
        sizeDp: Int,
    ): DockEditResult =
        when (sizeDp) {
            in MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP ->
                DockEditResult.Updated(
                    layout.copy(
                        dock = layout.dock.copy(iconSizeDp = sizeDp),
                    ),
                )

            else -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_ICON_SIZE)
        }

    fun setDockBackgroundAlpha(
        layout: HomeLayout,
        alphaPercent: Int,
    ): DockEditResult =
        when (alphaPercent) {
            in MIN_DOCK_BACKGROUND_ALPHA_PERCENT..MAX_DOCK_BACKGROUND_ALPHA_PERCENT ->
                DockEditResult.Updated(
                    layout.copy(
                        dock = layout.dock.copy(backgroundAlphaPercent = alphaPercent),
                    ),
                )

            else -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_BACKGROUND_ALPHA)
        }

    fun setDockItemSpacing(
        layout: HomeLayout,
        spacingDp: Int,
    ): DockEditResult =
        when (spacingDp) {
            in MIN_DOCK_ITEM_SPACING_DP..MAX_DOCK_ITEM_SPACING_DP ->
                DockEditResult.Updated(
                    layout.copy(
                        dock = layout.dock.copy(itemSpacingDp = spacingDp),
                    ),
                )

            else -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_ITEM_SPACING)
        }
}

private const val MIN_DOCK_CAPACITY = 0
