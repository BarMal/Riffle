package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup

private const val DOCK_SHELF_CONTENT_SPACING_DP = 6

internal data class DockSurfaceMetrics(
    val renderedSlotCount: Int,
    val containerMainAxisDp: Int,
    val contentViewportMainAxisDp: Int,
    val slotMetrics: DockSlotRenderMetrics,
)

internal fun dockSurfaceMetrics(
    dock: DockModel,
    isEditing: Boolean,
    availableMainAxisDp: Int,
    previewSlotCount: Int = 0,
): DockSurfaceMetrics? {
    val renderedSlotCount =
        dockRenderedSlotCount(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = isEditing,
        ) + previewSlotCount.coerceAtLeast(0)
    if (
        !dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = isEditing,
            backgroundSizing = dock.backgroundSizing,
        )
    ) {
        return null
    }

    val containerMainAxisDp =
        dockContainerMainAxisDp(
            availableMainAxisDp = availableMainAxisDp,
            slotCount = renderedSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            backgroundSizing = dock.backgroundSizing,
        )
    val contentViewportMainAxisDp =
        dockContentViewportMainAxisDp(
            slotCount = renderedSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            availableDockMainAxisDp = containerMainAxisDp,
        )

    return DockSurfaceMetrics(
        renderedSlotCount = renderedSlotCount,
        containerMainAxisDp = containerMainAxisDp,
        contentViewportMainAxisDp = contentViewportMainAxisDp,
        slotMetrics =
            dockSlotRenderMetrics(
                slotCount = renderedSlotCount,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                availableContentMainAxisDp = contentViewportMainAxisDp,
            ),
    )
}

@Composable
internal fun ExpandedDockSurface(
    dock: DockModel,
    notificationShelfState: DockNotificationShelfState,
    notificationGroupsByApp: List<AppNotificationGroup>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    interactions: DockInteractions,
) {
    val primaryDock = dock.primaryDock(showShelf = true)
    val overflowDock = dock.overflowShelfDock()
    val hasOverflow = dockHasOverflow(capacity = dock.capacity, itemCount = dock.items.size)
    val presentation = DockPresentation(notificationGroupsByApp, appShortcutsByApp, widgetViewFactory, interactions)

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = dock.alignment.toBoxAlignment(),
    ) {
        // Horizontal today, so the dock's run is this Box's width; the sizing helpers below are
        // named for the axis rather than the dimension so a side dock can pass its height here.
        val availableMainAxisDp = maxWidth.value.toInt()
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = primaryDock,
                isEditing = false,
                availableMainAxisDp = availableMainAxisDp,
            ) ?: return@BoxWithConstraints
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier =
                Modifier
                    .dockShelfPolicies(interactions)
                    .width(surfaceMetrics.containerMainAxisDp.dp)
                    .dockSurfaceAppearance(dock),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (notificationShelfState != DockNotificationShelfState.Hidden) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DOCK_MAIN_AXIS_PADDING_DP.dp,
                                vertical = DOCK_CROSS_AXIS_PADDING_DP.dp,
                            ),
                ) {
                    DockNotificationShelf(
                        state = notificationShelfState,
                        appIconLoader = appIconLoader,
                        interactions = interactions,
                    )
                }
                Spacer(modifier = Modifier.height(DOCK_SHELF_CONTENT_SPACING_DP.dp))
            }
            if (hasOverflow) {
                DockSurfaceRow(
                    dock = overflowDock,
                    surfaceMetrics =
                        expandedOverflowSurfaceMetrics(
                            dock = overflowDock,
                            availableMainAxisDp = availableMainAxisDp,
                            mainSurfaceMetrics = surfaceMetrics,
                        ),
                    isEditing = false,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    renderBackground = false,
                )
                Spacer(modifier = Modifier.height(DOCK_SHELF_CONTENT_SPACING_DP.dp))
            }
            DockSurfaceRow(
                dock = primaryDock,
                surfaceMetrics = surfaceMetrics,
                isEditing = false,
                presentation = presentation,
                appIconLoader = appIconLoader,
                renderBackground = false,
            )
        }
    }
}

private fun expandedOverflowSurfaceMetrics(
    dock: DockModel,
    availableMainAxisDp: Int,
    mainSurfaceMetrics: DockSurfaceMetrics,
): DockSurfaceMetrics =
    checkNotNull(
        dockSurfaceMetrics(
            dock = dock,
            isEditing = false,
            availableMainAxisDp = availableMainAxisDp,
        ),
    ).copy(containerMainAxisDp = mainSurfaceMetrics.containerMainAxisDp)

@Composable
@Suppress("LongParameterList")
internal fun DockSurfaceRow(
    dock: DockModel,
    surfaceMetrics: DockSurfaceMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
    renderBackground: Boolean = true,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview? = null,
) {
    Box(
        modifier =
            modifier
                .width(surfaceMetrics.containerMainAxisDp.dp)
                .height(dockCrossAxisDp(surfaceMetrics.slotMetrics.iconSizeDp).dp)
                .then(
                    if (renderBackground) {
                        Modifier.dockSurfaceAppearance(dock)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = DOCK_MAIN_AXIS_PADDING_DP.dp, vertical = DOCK_CROSS_AXIS_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (surfaceMetrics.renderedSlotCount > 0 && surfaceMetrics.contentViewportMainAxisDp > 0) {
            DockSlotsRow(
                dock = dock,
                renderedSlotCount = surfaceMetrics.renderedSlotCount,
                contentViewportMainAxisDp = surfaceMetrics.contentViewportMainAxisDp,
                slotMetrics = surfaceMetrics.slotMetrics,
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
                widgetPickerDockPreview = widgetPickerDockPreview,
            )
        }
    }
}
