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
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
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
        // The expanded shelf stacks notifications, the panel and the dock's own strip vertically,
        // so it is a bottom-edge arrangement whatever edge the collapsed dock ends up on -- how a
        // side dock expands is its own question. Its strip therefore runs along this Box's width.
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
            DockSurfaceStrip(
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
internal fun DockSurfaceStrip(
    dock: DockModel,
    surfaceMetrics: DockSurfaceMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
    position: DockPosition = DockPosition.BOTTOM,
    renderBackground: Boolean = true,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview? = null,
) {
    val runsHorizontally = position.isHorizontalEdge
    val mainAxisDp = surfaceMetrics.containerMainAxisDp.dp
    val crossAxisDp = dockCrossAxisDp(surfaceMetrics.slotMetrics.iconSizeDp).dp

    Box(
        modifier =
            modifier
                .width(if (runsHorizontally) mainAxisDp else crossAxisDp)
                .height(if (runsHorizontally) crossAxisDp else mainAxisDp)
                .then(
                    if (renderBackground) {
                        Modifier.dockSurfaceAppearance(dock)
                    } else {
                        Modifier
                    },
                )
                .padding(
                    horizontal = if (runsHorizontally) DOCK_MAIN_AXIS_PADDING_DP.dp else DOCK_CROSS_AXIS_PADDING_DP.dp,
                    vertical = if (runsHorizontally) DOCK_CROSS_AXIS_PADDING_DP.dp else DOCK_MAIN_AXIS_PADDING_DP.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (surfaceMetrics.renderedSlotCount > 0 && surfaceMetrics.contentViewportMainAxisDp > 0) {
            DockSlotStrip(
                dock = dock,
                renderedSlotCount = surfaceMetrics.renderedSlotCount,
                contentViewportMainAxisDp = surfaceMetrics.contentViewportMainAxisDp,
                slotMetrics = surfaceMetrics.slotMetrics,
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
                position = position,
                widgetPickerDockPreview = widgetPickerDockPreview,
            )
        }
    }
}
