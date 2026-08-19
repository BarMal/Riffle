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
    // Capacity caps how many slots are visible at once; anything past it is reached by scrolling
    // the strip. Sizing from the rendered count alone would shrink every icon as apps are added,
    // which is the opposite of what a capacity setting is for -- but capacity is a ceiling, not a
    // fixed width, so an under-filled dynamic dock still sizes to the items it actually holds
    // rather than reserving empty slots. A capacity-zero legacy layout has no ceiling on record.
    val visibleSlotCount =
        if (dock.capacity > 0) {
            renderedSlotCount.coerceAtMost(dock.capacity + previewSlotCount.coerceAtLeast(0))
        } else {
            renderedSlotCount
        }
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
            slotCount = visibleSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            backgroundSizing = dock.backgroundSizing,
        )
    val contentViewportMainAxisDp =
        dockContentViewportMainAxisDp(
            slotCount = visibleSlotCount,
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
                slotCount = visibleSlotCount,
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
                dock = dock,
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
            // The panel sits directly above the dock's own row, with notifications above it. Only
            // one of the two can hold a stable position as notifications come and go, and the
            // panel is the one worth keeping still: it is a place the user aims for, where the
            // notification section is whatever happens to have arrived.
            dock.panel?.let { panel ->
                DockPanel(
                    panel = panel,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    interactions = interactions,
                    modifier =
                        Modifier.padding(
                            horizontal = DOCK_MAIN_AXIS_PADDING_DP.dp,
                            vertical = DOCK_CROSS_AXIS_PADDING_DP.dp,
                        ),
                )
                Spacer(modifier = Modifier.height(DOCK_SHELF_CONTENT_SPACING_DP.dp))
            }
            DockSurfaceRow(
                dock = dock,
                surfaceMetrics = surfaceMetrics,
                isEditing = false,
                presentation = presentation,
                appIconLoader = appIconLoader,
                renderBackground = false,
            )
        }
    }
}

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
