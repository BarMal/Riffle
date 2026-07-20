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
    val containerWidthDp: Int,
    val contentViewportWidthDp: Int,
    val slotMetrics: DockSlotRenderMetrics,
)

internal fun dockSurfaceMetrics(
    dock: DockModel,
    isEditing: Boolean,
    availableWidthDp: Int,
): DockSurfaceMetrics? {
    val renderedSlotCount =
        dockRenderedSlotCount(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = isEditing,
        )
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

    val containerWidthDp =
        dockContainerWidthDp(
            availableWidthDp = availableWidthDp,
            slotCount = renderedSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            backgroundSizing = dock.backgroundSizing,
        )
    val contentViewportWidthDp =
        dockContentViewportWidthDp(
            slotCount = renderedSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            availableDockWidthDp = containerWidthDp,
        )

    return DockSurfaceMetrics(
        renderedSlotCount = renderedSlotCount,
        containerWidthDp = containerWidthDp,
        contentViewportWidthDp = contentViewportWidthDp,
        slotMetrics =
            dockSlotRenderMetrics(
                slotCount = renderedSlotCount,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                availableContentWidthDp = contentViewportWidthDp,
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
        contentAlignment = Alignment.Center,
    ) {
        val availableWidthDp = maxWidth.value.toInt()
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = primaryDock,
                isEditing = false,
                availableWidthDp = availableWidthDp,
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
                    .width(surfaceMetrics.containerWidthDp.dp)
                    .dockSurfaceAppearance(dock),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (notificationShelfState != DockNotificationShelfState.Hidden) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DOCK_HORIZONTAL_PADDING_DP.dp,
                                vertical = DOCK_VERTICAL_PADDING_DP.dp,
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
                            availableWidthDp = availableWidthDp,
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
    availableWidthDp: Int,
    mainSurfaceMetrics: DockSurfaceMetrics,
): DockSurfaceMetrics =
    checkNotNull(
        dockSurfaceMetrics(
            dock = dock,
            isEditing = false,
            availableWidthDp = availableWidthDp,
        ),
    ).copy(containerWidthDp = mainSurfaceMetrics.containerWidthDp)

@Composable
internal fun DockSurfaceRow(
    dock: DockModel,
    surfaceMetrics: DockSurfaceMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
    renderBackground: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .width(surfaceMetrics.containerWidthDp.dp)
                .height(dockHeightDp(surfaceMetrics.slotMetrics.iconSizeDp).dp)
                .then(
                    if (renderBackground) {
                        Modifier.dockSurfaceAppearance(dock)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = DOCK_HORIZONTAL_PADDING_DP.dp, vertical = DOCK_VERTICAL_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (surfaceMetrics.renderedSlotCount > 0 && surfaceMetrics.contentViewportWidthDp > 0) {
            DockSlotsRow(
                dock = dock,
                renderedSlotCount = surfaceMetrics.renderedSlotCount,
                contentViewportWidthDp = surfaceMetrics.contentViewportWidthDp,
                slotMetrics = surfaceMetrics.slotMetrics,
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
            )
        }
    }
}
