package com.riffle.core.domain.launcher.home

@Suppress("TooManyFunctions")
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

    fun setDockNotificationCardsEnabled(
        layout: HomeLayout,
        enabled: Boolean,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(showNotificationCards = enabled),
            ),
        )

    fun setDockExpandable(
        layout: HomeLayout,
        expandable: Boolean,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(isExpandable = expandable),
            ),
        )

    fun setDockExpandAffordance(
        layout: HomeLayout,
        affordance: DockExpandAffordance,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(expandAffordance = affordance),
            ),
        )

    /**
     * Gives the dock a panel, or takes it away.
     *
     * Seeding uses the layout's own grid width so the panel reads as a short home page rather than
     * a differently-proportioned thing, and a couple of rows because the shelf grows into the
     * screen the dock sits against. Turning it off removes the page and anything the user placed
     * on it, the same way removing a home page does; re-enabling seeds an empty one.
     */
    fun setDockPanelEnabled(
        layout: HomeLayout,
        enabled: Boolean,
    ): DockEditResult {
        val existing = layout.dock.panel
        val panel =
            when {
                !enabled -> null
                existing != null -> existing
                else ->
                    LauncherPage(
                        id = LauncherPageId(DOCK_PANEL_PAGE_ID),
                        grid =
                            GridDimensions(
                                columns = layout.settings.grid.dimensions.columns,
                                rows = DEFAULT_DOCK_PANEL_ROWS,
                            ),
                    )
            }
        return DockEditResult.Updated(layout.copy(dock = layout.dock.copy(panel = panel)))
    }

    fun setDockPosition(
        layout: HomeLayout,
        position: DockPosition,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(position = position),
            ),
        )

    fun setDockCapacity(
        layout: HomeLayout,
        capacity: Int,
    ): DockEditResult =
        when {
            capacity < MIN_DOCK_CAPACITY -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_CAPACITY)
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

    fun setDockVisualEffect(
        layout: HomeLayout,
        effect: DockVisualEffect,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(dock = layout.dock.copy(visualEffect = effect)),
        )

    fun setDockBackgroundSizing(
        layout: HomeLayout,
        sizing: DockBackgroundSizing,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(backgroundSizing = sizing),
            ),
        )

    fun setDockAlignment(
        layout: HomeLayout,
        alignment: DockAlignment,
    ): DockEditResult =
        DockEditResult.Updated(
            layout.copy(
                dock = layout.dock.copy(alignment = alignment),
            ),
        )

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

    fun setDockCornerRadius(
        layout: HomeLayout,
        cornerRadiusDp: Int,
    ): DockEditResult =
        when (cornerRadiusDp) {
            in MIN_DOCK_CORNER_RADIUS_DP..MAX_DOCK_CORNER_RADIUS_DP ->
                DockEditResult.Updated(
                    layout.copy(dock = layout.dock.copy(cornerRadiusDp = cornerRadiusDp)),
                )

            else -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_ITEM_SPACING)
        }

    fun setDockHomeControlsSpacing(
        layout: HomeLayout,
        spacingDp: Int,
    ): DockEditResult =
        when (spacingDp) {
            in MIN_DOCK_HOME_CONTROLS_SPACING_DP..MAX_DOCK_HOME_CONTROLS_SPACING_DP ->
                DockEditResult.Updated(
                    layout.copy(dock = layout.dock.copy(homeControlsSpacingDp = spacingDp)),
                )

            else -> DockEditResult.Rejected(DockEditRejectionReason.INVALID_ITEM_SPACING)
        }
}

private const val MIN_DOCK_CAPACITY = 0

/** Fixed rather than generated: a dock has at most one panel, so its page needs no unique id. */
private const val DOCK_PANEL_PAGE_ID = "dock-panel"
private const val DEFAULT_DOCK_PANEL_ROWS = 2
