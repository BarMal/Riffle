package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.dock.DockDynamicSide
import com.riffle.core.domain.launcher.dock.DockRunGeometry
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import kotlin.math.min

private const val DOCK_SHELF_CONTENT_SPACING_DP = 6

internal data class DockSurfaceMetrics(
    val renderedSlotCount: Int,
    val containerMainAxisDp: Int,
    val contentViewportMainAxisDp: Int,
    val slotMetrics: DockSlotRenderMetrics,
    val dynamicSectionMainAxisDp: Int = 0,
    /**
     * The contextual mode's one scrolling row (ADR 0003, Amendment 3), or null in the Home mode: where each entry lies
     * along it. When set, [contextualMainAxisDp] is the whole dock's run and the row scrolls inside it.
     */
    val contextualRun: DockRunGeometry.Run? = null,
    val contextualMainAxisDp: Int = 0,
    /** The contextual row's item size and gap; the Home mode's [slotMetrics] otherwise. */
    val contextualSlotMetrics: DockSlotRenderMetrics = slotMetrics,
) {
    /**
     * The whole dock's run: the static side, and the dynamic section and rule beside it.
     *
     * Equal to [containerMainAxisDp] whenever there is no dynamic section, which is what every
     * caller that predates one is asking for.
     */
    val surfaceMainAxisDp: Int
        get() =
            when {
                contextualRun != null -> contextualMainAxisDp
                dynamicSectionMainAxisDp <= 0 -> containerMainAxisDp
                else -> containerMainAxisDp + DOCK_SECTION_DIVIDER_MAIN_AXIS_DP + dynamicSectionMainAxisDp
            }
}

internal fun dockSurfaceMetrics(
    dock: DockModel,
    isEditing: Boolean,
    availableMainAxisDp: Int,
    previewSlotCount: Int = 0,
    runsHorizontally: Boolean = true,
    dynamicEntryCount: Int = 0,
    /**
     * The contextual tiles among the [dynamicEntryCount] entries; above zero, the dock is the contextual mode's row.
     */
    contextualTileCount: Int = 0,
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
    //
    // FIXED (Full width/height) skips this cap too: it is the user asking the dock to actually use
    // the room it now has, and a pinned item past the configured capacity (kept, not deleted, when
    // that setting is lowered after the fact -- see dockRenderedSlotCount's own doc) is exactly the
    // kind of "still scrolls even though the dock got longer" mismatch that setting exists to fix.
    // dockMaxMainAxisDp's own run-length cap (lifted the same way for FIXED, above) is what still
    // stops this from outgrowing the physical screen.
    val visibleSlotCount =
        if (dock.capacity > 0 && dock.backgroundSizing != DockBackgroundSizing.FIXED) {
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

    val maxRunMainAxisDp =
        minOf(availableMainAxisDp, dockMaxMainAxisDp(availableMainAxisDp, runsHorizontally, dock.backgroundSizing))
            .coerceAtLeast(0)

    // The dynamic side takes only what its entries need, but never so much that the pinned run is left with less than a
    // few icons' worth: the pins keep their size and scroll in what remains (they are never shrunk to make room).
    val minStaticMainAxisDp =
        dockStaticFloorMainAxisDp(
            slotCount = visibleSlotCount,
            iconSizeDp = dock.iconSizeDp,
            spacingDp = dock.itemSpacingDp,
        )

    // Notifications claim their room first; the static side is sized from whatever is left (see
    // dockDynamicSectionMainAxisDp), not the other way around -- a busy notification section
    // scrolls the pinned icons out of the way rather than getting squeezed itself.
    val dynamicSectionMainAxisDp =
        dockDynamicSectionMainAxisDp(
            entryCount = dynamicEntryCount,
            slotLimit = dock.liveAppSlots + DockDynamicSide.FIXED_ENTRY_COUNT,
            entryExtentDp = dockDynamicEntryExtentDp(),
            entrySpacingDp = dock.itemSpacingDp,
            maxRunMainAxisDp = (maxRunMainAxisDp - minStaticMainAxisDp).coerceAtLeast(0),
        )
    val staticMaxRunMainAxisDp =
        if (dynamicSectionMainAxisDp > 0) {
            (maxRunMainAxisDp - dynamicSectionMainAxisDp - DOCK_SECTION_DIVIDER_MAIN_AXIS_DP).coerceAtLeast(0)
        } else {
            maxRunMainAxisDp
        }

    val staticSide =
        dockStaticSideMetrics(dock, visibleSlotCount, availableMainAxisDp, runsHorizontally, staticMaxRunMainAxisDp)
    val contextualMetrics = dockContextualSlotMetrics(dock, maxRunMainAxisDp)
    val contextualRun =
        contextualTileCount.takeIf { it > 0 }?.let {
            dockContextualRun(
                renderedSlotCount,
                dynamicEntryCount,
                contextualTileCount,
                contextualMetrics,
                maxRunMainAxisDp,
            )
        }
    return DockSurfaceMetrics(
        renderedSlotCount = renderedSlotCount,
        containerMainAxisDp =
            maxOf(
                staticSide.containerMainAxisDp,
                dockEmptyBodyMainAxisDp(dock, visibleSlotCount, dynamicEntryCount, maxRunMainAxisDp),
            ),
        contentViewportMainAxisDp = staticSide.contentViewportMainAxisDp,
        slotMetrics = staticSide.slotMetrics,
        dynamicSectionMainAxisDp = dynamicSectionMainAxisDp,
        contextualRun = contextualRun,
        // Every icon of the Home mode stays on show if there is room, then the row scrolls: the dock is as long as its
        // row, never longer than the run it may have.
        contextualMainAxisDp =
            contextualRun?.let { run -> min(maxRunMainAxisDp, run.contentDp + (DOCK_MAIN_AXIS_PADDING_DP * 2)) } ?: 0,
        contextualSlotMetrics = contextualMetrics,
    )
}

/** The pinned side's sizes: its container, the viewport its icons scroll in and how the slots are drawn in it. */
private class DockStaticSideMetrics(
    val containerMainAxisDp: Int,
    val contentViewportMainAxisDp: Int,
    val slotMetrics: DockSlotRenderMetrics,
)

private fun dockStaticSideMetrics(
    dock: DockModel,
    visibleSlotCount: Int,
    availableMainAxisDp: Int,
    runsHorizontally: Boolean,
    staticMaxRunMainAxisDp: Int,
): DockStaticSideMetrics {
    val containerMainAxisDp =
        dockContainerMainAxisDp(
            availableMainAxisDp = availableMainAxisDp,
            slotCount = visibleSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            backgroundSizing = dock.backgroundSizing,
            runsHorizontally = runsHorizontally,
            runMainAxisCapDp = staticMaxRunMainAxisDp,
        )
    val fittedViewportMainAxisDp =
        dockContentViewportMainAxisDp(
            slotCount = visibleSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            availableDockMainAxisDp = containerMainAxisDp,
        )
    val slotMetrics =
        dockSlotRenderMetrics(
            slotCount = visibleSlotCount,
            iconSizeDp = dock.iconSizeDp,
            itemSpacingDp = dock.itemSpacingDp,
            availableContentMainAxisDp = fittedViewportMainAxisDp,
        )
    // A run that still does not fit once the gap has given what it can scrolls; its viewport then holds whole icons
    // only, so it never rests with an icon cut in half (it scrolls by whole icons too, see DockSlotStrip).
    val scrolls = dockPinnedRunScrolls(visibleSlotCount, slotMetrics, fittedViewportMainAxisDp)
    val contentViewportMainAxisDp =
        if (scrolls) {
            dockWholeIconsViewportMainAxisDp(
                viewportMainAxisDp = fittedViewportMainAxisDp,
                iconSizeDp = slotMetrics.iconSizeDp,
                spacingDp = slotMetrics.itemSpacingDp,
            )
        } else {
            fittedViewportMainAxisDp
        }
    return DockStaticSideMetrics(
        // A fixed-length dock keeps its length; a dynamic one is as long as the whole icons it shows.
        containerMainAxisDp =
            if (scrolls && dock.backgroundSizing != DockBackgroundSizing.FIXED) {
                min(containerMainAxisDp, contentViewportMainAxisDp + (DOCK_MAIN_AXIS_PADDING_DP * 2))
            } else {
                containerMainAxisDp
            },
        contentViewportMainAxisDp = contentViewportMainAxisDp,
        slotMetrics = slotMetrics,
    )
}

/**
 * The longest run of whole icons, [iconSizeDp] each with [spacingDp] between, that fits in [viewportMainAxisDp]: at
 * least one icon (a viewport shorter than an icon is left as it is). What a scrolling pinned run is as long as, so
 * neither end of it shows half an icon at rest.
 */
internal fun dockWholeIconsViewportMainAxisDp(
    viewportMainAxisDp: Int,
    iconSizeDp: Int,
    spacingDp: Int,
): Int {
    val spacing = spacingDp.coerceAtLeast(0)
    val stride = iconSizeDp + spacing
    if (iconSizeDp <= 0 || viewportMainAxisDp < iconSizeDp) return viewportMainAxisDp.coerceAtLeast(0)
    val whole = ((viewportMainAxisDp + spacing) / stride).coerceAtLeast(1)
    return (whole * iconSizeDp) + ((whole - 1) * spacing)
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
    dynamicEntryCount: Int = 0,
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
                // The strip's own dynamic section stays undrawn here (the shelf's card row already
                // shows those entries -- see the comment where dynamicEntries is withheld in
                // DockOrShelf), but the static side still needs to reserve the same room for it that
                // the collapsed dock does, or the pinned-icon strip's width jumps when the shelf
                // opens and closes.
                dynamicEntryCount = dynamicEntryCount,
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
    onDynamicEntryDelegated: (String) -> Unit = {},
) {
    val runsHorizontally = position.isHorizontalEdge
    val showDynamicSection =
        surfaceMetrics.contextualRun == null && dockSurfaceStripShowsDynamicSection(surfaceMetrics, dynamicEntries)
    val mainAxisDp = dockSurfaceStripMainAxisDp(surfaceMetrics, showDynamicSection).dp
    // The bar is as tall as the pinned icon is big: the configured size, which a short run never shrinks.
    val crossAxisDp = dockCrossAxisDp(surfaceMetrics.slotMetrics.iconSizeDp).dp

    // The spotlight belongs to the dock's own background, so a strip drawn without one (the
    // expanded shelf's, which sits on the shelf's background instead) carries no glow either.
    val spotlight =
        rememberGlassSpotlightState(
            axis = if (runsHorizontally) GlassSpotlightAxis.Horizontal else GlassSpotlightAxis.Vertical,
            enabled = renderBackground,
        )
    val backgroundAlpha = LocalDockBackgroundAlpha.current
    val bodyBoundsSink = LocalDockBodyBoundsSink.current
    DisposableEffect(bodyBoundsSink) { onDispose { if (renderBackground) bodyBoundsSink(null) } }

    Box(
        modifier =
            modifier
                .width(if (runsHorizontally) mainAxisDp else crossAxisDp)
                .height(if (runsHorizontally) crossAxisDp else mainAxisDp)
                .then(
                    if (renderBackground) {
                        Modifier
                            .onGloballyPositioned { coordinates -> bodyBoundsSink(coordinates.boundsInRoot()) }
                            .dockSurfaceAppearance(dock)
                    } else {
                        Modifier
                    },
                )
                .glassSpotlight(
                    spotlight = spotlight,
                    shape = RoundedCornerShape(dock.cornerRadiusDp.dp),
                    // The edge facing into the screen is the inner one: the top of a bottom dock,
                    // the right of a left dock, and so on.
                    innerAtStart = position == DockPosition.BOTTOM || position == DockPosition.RIGHT,
                    alphaProvider = backgroundAlpha,
                )
                .dockDropHighlight(
                    dock,
                    presentation.interactions.isDropHighlighted,
                    reducedMotion = presentation.interactions.reducedMotion,
                )
                .padding(
                    horizontal = if (runsHorizontally) DOCK_MAIN_AXIS_PADDING_DP.dp else DOCK_CROSS_AXIS_PADDING_DP.dp,
                    vertical = if (runsHorizontally) DOCK_CROSS_AXIS_PADDING_DP.dp else DOCK_MAIN_AXIS_PADDING_DP.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalGlassSpotlight provides spotlight) {
            DockStripBody(
                dock = dock,
                surfaceMetrics = surfaceMetrics,
                showDynamicSection = showDynamicSection,
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
                position = position,
                widgetPickerDockPreview = widgetPickerDockPreview,
                dynamicEntries = dynamicEntries,
                onDynamicEntryDelegated = onDynamicEntryDelegated,
            )
        }
    }
}
