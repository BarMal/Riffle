package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
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
    val dynamicSectionMainAxisDp: Int = 0,
) {
    /**
     * The whole dock's run: the static side, and the dynamic section and rule beside it.
     *
     * Equal to [containerMainAxisDp] whenever there is no dynamic section, which is what every
     * caller that predates one is asking for.
     */
    val surfaceMainAxisDp: Int
        get() =
            if (dynamicSectionMainAxisDp <= 0) {
                containerMainAxisDp
            } else {
                containerMainAxisDp + DOCK_SECTION_DIVIDER_MAIN_AXIS_DP + dynamicSectionMainAxisDp
            }
}

internal fun dockSurfaceMetrics(
    dock: DockModel,
    isEditing: Boolean,
    availableMainAxisDp: Int,
    previewSlotCount: Int = 0,
    runsHorizontally: Boolean = true,
    dynamicEntryCount: Int = 0,
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
            runsHorizontally = runsHorizontally,
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
        dynamicSectionMainAxisDp =
            dockDynamicSectionMainAxisDp(
                entryCount = dynamicEntryCount,
                entryExtentDp = dock.iconSizeDp,
                entrySpacingDp = dock.itemSpacingDp,
                staticContainerMainAxisDp = containerMainAxisDp,
                maxRunMainAxisDp =
                    minOf(
                        availableMainAxisDp,
                        dockMaxMainAxisDp(availableMainAxisDp, runsHorizontally),
                    ).coerceAtLeast(0),
            ),
    )
}

@Composable
@Suppress("LongParameterList")
internal fun ExpandedDockSurface(
    dock: DockModel,
    notificationShelfState: DockNotificationShelfState,
    notificationGroupsByApp: List<AppNotificationGroup>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    position: DockPosition = DockPosition.BOTTOM,
    interactions: DockInteractions,
) {
    val presentation = DockPresentation(notificationGroupsByApp, appShortcutsByApp, widgetViewFactory, interactions)
    val runsHorizontally = position.isHorizontalEdge

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = dock.alignment.toBoxAlignment(),
    ) {
        // The shelf grows out of the edge the dock is on, so its strip runs the same way the
        // collapsed dock did: along this Box's width on a horizontal edge, its height on a side.
        val availableMainAxisDp =
            if (runsHorizontally) maxWidth.value.toInt() else maxHeight.value.toInt()
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = dock,
                isEditing = false,
                availableMainAxisDp = availableMainAxisDp,
                runsHorizontally = runsHorizontally,
            ) ?: return@BoxWithConstraints
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )

        DockShelfArrangement(
            runsHorizontally = runsHorizontally,
            stripFirst = position.placedBeforeContent(LocalLayoutDirection.current),
            modifier =
                Modifier
                    .dockShelfPolicies(interactions)
                    .dockShelfExtent(runsHorizontally, surfaceMetrics.containerMainAxisDp.dp)
                    .dockSurfaceAppearance(dock),
            strip = {
                DockSurfaceStrip(
                    dock = dock,
                    surfaceMetrics = surfaceMetrics,
                    isEditing = false,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    position = position,
                    renderBackground = false,
                )
            },
            content = {
                DockShelfContent(
                    dock = dock,
                    notificationShelfState = notificationShelfState,
                    appIconLoader = appIconLoader,
                    presentation = presentation,
                    interactions = interactions,
                    panelFirst = !runsHorizontally,
                )
            },
        )
    }
}

/**
 * The shelf's two parts, laid out so the dock's own strip stays against the edge it came from.
 *
 * The strip and the shelf's content run across each other: a bottom shelf stacks them, a side shelf
 * sets them side by side, and which comes first is which edge the dock is on.
 */
@Composable
private fun DockShelfArrangement(
    runsHorizontally: Boolean,
    stripFirst: Boolean,
    modifier: Modifier,
    strip: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val parts: List<@Composable () -> Unit> = if (stripFirst) listOf(strip, content) else listOf(content, strip)
    if (runsHorizontally) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            parts.forEach { part -> part() }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            parts.forEach { part -> part() }
        }
    }
}

/**
 * The notification section and the panel, stacked.
 *
 * Only one of the two can hold a stable position as notifications come and go, and the panel is the
 * one worth keeping still: it is a place the user aims for, where the notification section is
 * whatever happens to have arrived. So the panel is the one placed against the dock -- directly
 * above the strip on a bottom shelf, and at the top of the column beside it on a side shelf, where
 * "against the dock" is a different direction.
 */
@Composable
private fun DockShelfContent(
    dock: DockModel,
    notificationShelfState: DockNotificationShelfState,
    appIconLoader: AppIconLoader,
    presentation: DockPresentation,
    interactions: DockInteractions,
    panelFirst: Boolean,
) {
    val notifications: (@Composable () -> Unit)? =
        notificationShelfState
            .takeIf { state -> state != DockNotificationShelfState.Hidden }
            ?.let { state ->
                {
                    Box(modifier = Modifier.fillMaxWidth().dockShelfContentPadding()) {
                        DockNotificationShelf(
                            state = state,
                            appIconLoader = appIconLoader,
                            interactions = interactions,
                        )
                    }
                }
            }
    val panel: (@Composable () -> Unit)? =
        dock.panel?.let { page ->
            {
                DockPanel(
                    panel = page,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    interactions = interactions,
                    modifier = Modifier.dockShelfContentPadding(),
                )
            }
        }
    val ordered = if (panelFirst) listOfNotNull(panel, notifications) else listOfNotNull(notifications, panel)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ordered.forEachIndexed { index, part ->
            part()
            if (index != ordered.lastIndex) {
                Spacer(modifier = Modifier.height(DOCK_SHELF_CONTENT_SPACING_DP.dp))
            }
        }
    }
}

private fun Modifier.dockShelfContentPadding(): Modifier =
    padding(horizontal = DOCK_MAIN_AXIS_PADDING_DP.dp, vertical = DOCK_CROSS_AXIS_PADDING_DP.dp)

/** Sizes the shelf along the dock's run and lets its content decide the other way. */
private fun Modifier.dockShelfExtent(
    runsHorizontally: Boolean,
    mainAxis: Dp,
): Modifier = if (runsHorizontally) width(mainAxis) else height(mainAxis)

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
    dynamicEntries: List<DockDynamicEntry> = emptyList(),
    onShowAllNotifications: () -> Unit = {},
) {
    val runsHorizontally = position.isHorizontalEdge
    val mainAxisDp = surfaceMetrics.surfaceMainAxisDp.dp
    val crossAxisDp = dockCrossAxisDp(surfaceMetrics.slotMetrics.iconSizeDp).dp
    val staticSide: @Composable () -> Unit = {
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
        // Nothing dynamic to show is the common case and stays exactly as it was: one strip,
        // centred, with no arrangement wrapped around it to shift it by a fraction of a pixel.
        if (surfaceMetrics.dynamicSectionMainAxisDp <= 0) {
            staticSide()
        } else {
            DockSectionRun(runsHorizontally = runsHorizontally) {
                staticSide()
                DockSectionDivider(runsHorizontally = runsHorizontally)
                DockDynamicSection(
                    entries = dynamicEntries,
                    slotMetrics = surfaceMetrics.slotMetrics,
                    mainAxisDp = surfaceMetrics.dynamicSectionMainAxisDp,
                    runsHorizontally = runsHorizontally,
                    appIconLoader = appIconLoader,
                    onAction = presentation.interactions.onAction,
                    onShowAllNotifications = onShowAllNotifications,
                )
            }
        }
    }
}

/** The dock's two sections laid end to end, whichever way the dock runs. */
@Composable
private fun DockSectionRun(
    runsHorizontally: Boolean,
    content: @Composable () -> Unit,
) {
    if (runsHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/**
 * The rule between the static side and the dynamic one.
 *
 * A visible seam on purpose. The two sides answer to different things -- one to what the user
 * pinned, one to what has just arrived -- and running them together would make the dock look like
 * it rearranges itself.
 */
@Composable
private fun DockSectionDivider(runsHorizontally: Boolean) {
    val thickness = DOCK_SECTION_DIVIDER_MAIN_AXIS_DP.dp
    Box(
        modifier =
            if (runsHorizontally) {
                Modifier.width(thickness).fillMaxHeight()
            } else {
                Modifier.height(thickness).fillMaxWidth()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .then(
                        if (runsHorizontally) {
                            Modifier.width(DOCK_SECTION_DIVIDER_RULE_DP.dp).fillMaxHeight()
                        } else {
                            Modifier.height(DOCK_SECTION_DIVIDER_RULE_DP.dp).fillMaxWidth()
                        },
                    )
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DOCK_SECTION_DIVIDER_ALPHA)),
        )
    }
}

private const val DOCK_SECTION_DIVIDER_RULE_DP = 1
private const val DOCK_SECTION_DIVIDER_ALPHA = 0.3f
