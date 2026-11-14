package com.riffle.app.launcher

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.dock.DockRunGeometry
import com.riffle.core.domain.launcher.dock.DockRunScroll
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import kotlin.math.min

/**
 * The Dock in the contextual mode (ADR 0003, Amendment 3): one row that scrolls along the Dock's run, with fling,
 * holding
 * the pinned icons, a divider, the live entries, a divider and then one tile per Stream page that is not an app. The
 * icons shared with the Home mode keep their order; the row scrolls so the open page's icon (the ringed one) is in
 * view,
 * and again each time that changes, by a tap on a tile or a swipe of the Stream's pager. The row's own scroll is the
 * platform's ([horizontalScroll] / [verticalScroll] with their default fling), so a drag along the run scrolls it
 * and the
 * press that toggles the mode (across the run) is left to the Dock's gesture.
 */
@Composable
@Suppress("LongParameterList")
internal fun DockContextualRow(
    dock: DockModel,
    surfaceMetrics: DockSurfaceMetrics,
    run: DockRunGeometry.Run,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    position: DockPosition,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview?,
    dynamicEntries: List<DockDynamicEntry>,
    onDynamicEntryDelegated: (String) -> Unit,
) {
    val runsHorizontally = position.isHorizontalEdge
    val viewportDp = surfaceMetrics.contextualMainAxisDp - (DOCK_MAIN_AXIS_PADDING_DP * 2)
    val scrollState = rememberScrollState()
    val overflow = DockOverflowAffordance(scrollOffsetPx = scrollState.value, maxScrollOffsetPx = scrollState.maxValue)
    val choice = LocalDockChoice.current
    // Hold to choose: holding the finger near an end scrolls the row, and choosing from Home brings the live entries
    // and tiles into view as the open mode would.
    DockChoiceRowRegistration(choice) { deltaPx -> scrollState.dispatchRawDelta(deltaPx) }
    RevealForChoosing(
        choice = choice,
        run = run,
        scrollState = scrollState,
        reducedMotion = presentation.interactions.reducedMotion,
    )
    RevealOpenEntry(
        entries = dynamicEntries,
        run = run,
        viewportDp = viewportDp,
        scrollState = scrollState,
        reducedMotion = presentation.interactions.reducedMotion,
    )
    val scrollModifier =
        if (runsHorizontally) {
            Modifier.horizontalScroll(scrollState)
        } else {
            Modifier.verticalScroll(scrollState)
        }
    Box(
        modifier =
            Modifier
                .then(
                    if (runsHorizontally) {
                        Modifier.width(viewportDp.dp)
                    } else {
                        Modifier.height(viewportDp.dp)
                    },
                )
                .clipToBounds()
                .onGloballyPositioned { coordinates -> choice?.rowViewport = coordinates.boundsInRoot() }
                .dockOverflowMask(
                    runsHorizontally = runsHorizontally,
                    fadeStart = overflow.showStart,
                    fadeEnd = overflow.showEnd,
                )
                .testTag(DOCK_CONTEXTUAL_ROW_TEST_TAG),
    ) {
        DockSectionRun(
            runsHorizontally = runsHorizontally,
            modifier = scrollModifier,
        ) {
            DockSlotStrip(
                dock = dock,
                renderedSlotCount = surfaceMetrics.renderedSlotCount,
                contentViewportMainAxisDp = surfaceMetrics.contentViewportMainAxisDp,
                slotMetrics = surfaceMetrics.slotMetrics.copy(itemSpacingDp = dock.itemSpacingDp),
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
                position = position,
                widgetPickerDockPreview = widgetPickerDockPreview,
                inline = true,
            )
            DockSectionDivider(runsHorizontally = runsHorizontally)
            DockDynamicSection(
                entries = dynamicEntries,
                slotMetrics = surfaceMetrics.slotMetrics.copy(itemSpacingDp = dock.itemSpacingDp),
                mainAxisDp = run.contentDp,
                runsHorizontally = runsHorizontally,
                appIconLoader = appIconLoader,
                interactions = presentation.interactions,
                onEntryDelegated = onDynamicEntryDelegated,
                inline = true,
                dividerBeforeIndex = dynamicEntries.indexOfFirst(DockDynamicEntry::isContextTile).takeIf { it > 0 },
            )
        }
    }
}

/**
 * Choosing from Home extends the Dock early: the row scrolls so the first live entry (and the tiles after it) is at its
 * start, as it would be when the cards are out. Not while choosing from the open Stream (the row stays where it is) and
 * no more once holding near an end has scrolled it. Instant under reduced motion.
 */
@Composable
private fun RevealForChoosing(
    choice: DockChoiceState?,
    run: DockRunGeometry.Run,
    scrollState: ScrollState,
    reducedMotion: Boolean,
) {
    if (choice == null) return
    val density = LocalDensity.current.density
    val reveal = choice.choosing && !choice.fromContextual && !choice.edgeScrolled
    val first = run.entries.firstOrNull()
    LaunchedEffect(reveal, first, scrollState.maxValue) {
        if (!reveal || first == null) return@LaunchedEffect
        val target = ((first.startDp - REVEAL_MARGIN_DP) * density).toInt().coerceIn(0, scrollState.maxValue)
        if (target != scrollState.value) {
            if (reducedMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
        }
    }
}

/** Scrolls the row so the ringed entry is in view, whenever the ring moves (and once the row has been measured). */
@Composable
private fun RevealOpenEntry(
    entries: List<DockDynamicEntry>,
    run: DockRunGeometry.Run,
    viewportDp: Int,
    scrollState: ScrollState,
    reducedMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val index = entries.indexOfFirst(DockDynamicEntry::isSelected)
    val extent = run.entries.getOrNull(index)
    LaunchedEffect(index, extent, viewportDp, scrollState.maxValue) {
        if (extent == null) return@LaunchedEffect
        val target =
            DockRunScroll.toReveal(
                scrollPx = scrollState.value,
                viewportPx = (viewportDp * density).toInt(),
                maxScrollPx = scrollState.maxValue,
                itemStartPx = (extent.startDp * density).toInt(),
                itemEndPx = (extent.endDp * density).toInt(),
                // The neighbour peeks past the open icon, so the row reads as one that goes on.
                marginPx = (REVEAL_MARGIN_DP * density).toInt(),
            )
        if (target != scrollState.value) {
            if (reducedMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
        }
    }
}

private const val REVEAL_MARGIN_DP = 24

internal const val DOCK_CONTEXTUAL_ROW_TEST_TAG = "dock-contextual-row"

/**
 * The run a dock with no pinned icon and nothing dynamic keeps: the body of one icon. Without it the dock would be
 * a few dp of padding, too small to press (the Stream button used to keep it one entry long).
 */
internal fun dockEmptyBodyMainAxisDp(
    dock: DockModel,
    visibleSlotCount: Int,
    dynamicEntryCount: Int,
    maxRunMainAxisDp: Int,
): Int =
    if (visibleSlotCount == 0 && dynamicEntryCount == 0) {
        min(maxRunMainAxisDp, dock.iconSizeDp + (DOCK_MAIN_AXIS_PADDING_DP * 2))
    } else {
        0
    }

/**
 * Where everything lies in the contextual mode's one row: the pinned icons at full size and spacing (a crowded Home
 * dock compacts them, the row scrolls instead), a divider, the live entries, a divider, the tiles.
 */
internal fun dockContextualRun(
    dock: DockModel,
    renderedSlotCount: Int,
    entryCount: Int,
    tileCount: Int,
): DockRunGeometry.Run =
    DockRunGeometry.contextual(
        staticDp =
            dockSlotContentMainAxisDp(
                renderedSlotCount,
                DockSlotRenderMetrics(dock.iconSizeDp, dock.itemSpacingDp, DockOverflowMode.Fits),
            ),
        liveCount = (entryCount - tileCount).coerceAtLeast(0),
        tileCount = tileCount,
        entryDp = dockDynamicEntryExtentDp(),
        spacingDp = dock.itemSpacingDp.coerceAtLeast(0),
        dividerDp = DOCK_SECTION_DIVIDER_MAIN_AXIS_DP,
    )
