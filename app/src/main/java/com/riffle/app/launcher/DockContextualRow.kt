package com.riffle.app.launcher

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.riffle.core.domain.launcher.dock.DockRunGeometry
import com.riffle.core.domain.launcher.dock.DockRunScroll
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.roundToInt

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
@Suppress("LongParameterList", "LongMethod")
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
    // Where each icon and the row's viewport are on screen, so the ringed icon's reveal is measured, not computed.
    val outerSink = LocalDockDynamicEntryBoundsSink.current
    val iconBounds = remember { mutableStateMapOf<String, Rect>() }
    val boundsSink: (String, Rect?) -> Unit =
        remember(outerSink) {
            { key, rect ->
                if (rect == null) {
                    iconBounds.remove(key)
                } else if (iconBounds[key] != rect) {
                    iconBounds[key] = rect
                }
                outerSink(key, rect)
            }
        }
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    val runViewport = remember(runsHorizontally) { DockRunViewport(runsHorizontally) }
    val pinnedSelected = LocalDockPinnedStream.current?.selectedKey
    RevealOpenEntry(
        selectedKey = dynamicEntries.firstOrNull(DockDynamicEntry::isSelected)?.key ?: pinnedSelected,
        bounds = iconBounds,
        viewport = { viewportBounds },
        runsHorizontally = runsHorizontally,
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
                .onGloballyPositioned { coordinates ->
                    choice?.rowViewport = coordinates.boundsInRoot()
                    val whole = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                    if (viewportBounds != whole) viewportBounds = whole
                    if (runViewport.bounds != whole) runViewport.bounds = whole
                }
                .dockOverflowMask(
                    runsHorizontally = runsHorizontally,
                    fadeStart = overflow.showStart,
                    fadeEnd = overflow.showEnd,
                )
                .testTag(DOCK_CONTEXTUAL_ROW_TEST_TAG),
    ) {
        CompositionLocalProvider(
            LocalDockDynamicEntryBoundsSink provides boundsSink,
            LocalDockRunViewport provides runViewport,
        ) {
            DockSectionRun(
                runsHorizontally = runsHorizontally,
                modifier = scrollModifier,
            ) {
                DockSlotStrip(
                    dock = dock,
                    renderedSlotCount = surfaceMetrics.renderedSlotCount,
                    contentViewportMainAxisDp = surfaceMetrics.contentViewportMainAxisDp,
                    slotMetrics = surfaceMetrics.contextualSlotMetrics,
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
                    slotMetrics = surfaceMetrics.contextualSlotMetrics,
                    mainAxisDp = run.contentDp,
                    runsHorizontally = runsHorizontally,
                    appIconLoader = appIconLoader,
                    interactions = presentation.interactions,
                    onEntryDelegated = onDynamicEntryDelegated,
                    inline = true,
                    dividerBeforeIndex = dynamicEntries.indexOfFirst(DockDynamicEntry::isContextTile).takeIf { it > 0 },
                )
                RunEndPadding(run.endPaddingDp, runsHorizontally)
            }
        }
    }
}

/** The end padding that makes the row's last scroll position an item's start, never a clipped sliver. */
@Composable
private fun RunEndPadding(
    paddingDp: Int,
    runsHorizontally: Boolean,
) {
    if (paddingDp > 0) Spacer(if (runsHorizontally) Modifier.width(paddingDp.dp) else Modifier.height(paddingDp.dp))
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
        val target =
            ((first.startDp - DOCK_SECTION_DIVIDER_MAIN_AXIS_DP) * density).toInt().coerceIn(0, scrollState.maxValue)
        if (target != scrollState.value) {
            if (reducedMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
        }
    }
}

/**
 * Brings the ringed icon (a live entry, a tile or a pinned app) into view when the ring moves to it, and otherwise
 * leaves the row alone: an icon that is already wholly in view never moves the row, and one that is clipped or off
 * screen moves it by the least that shows it whole (`DockRunScroll.toReveal`). It works from where the icon and the
 * row's viewport actually are on screen (measured, so no rounding or spacing difference can make a visible icon look
 * clipped), and only when the ringed icon changes, so a row the user has scrolled elsewhere stays there.
 */
@Composable
private fun RevealOpenEntry(
    selectedKey: String?,
    bounds: Map<String, Rect>,
    viewport: () -> Rect?,
    runsHorizontally: Boolean,
    scrollState: ScrollState,
    reducedMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val latestViewport = rememberUpdatedState(viewport)
    LaunchedEffect(selectedKey) {
        val key = selectedKey ?: return@LaunchedEffect
        val (item, port) =
            snapshotFlow { bounds[key]?.let { item -> latestViewport.value()?.let { port -> item to port } } }
                .filterNotNull()
                .first()
        val itemStart = if (runsHorizontally) item.left - port.left else item.top - port.top
        val itemEnd = if (runsHorizontally) item.right - port.left else item.bottom - port.top
        val viewportPx = if (runsHorizontally) port.width else port.height
        val target =
            DockRunScroll.toReveal(
                scrollPx = scrollState.value,
                viewportPx = viewportPx.roundToInt(),
                maxScrollPx = scrollState.maxValue,
                // The icon's place in the row's content: its place in the viewport plus how far the row is scrolled.
                itemStartPx = scrollState.value + itemStart.roundToInt(),
                itemEndPx = scrollState.value + itemEnd.roundToInt(),
                marginPx = (DockRunScroll.PEEK_MARGIN_DP * density).roundToInt(),
            )
        if (target != scrollState.value) {
            if (reducedMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
        }
    }
}

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
 * How the contextual mode's one row is drawn: every item (pinned icon, live app, tile) is the 48 dp touch target,
 * the gap closed up from the Dock's own spacing until six whole items fit the Dock's run (see
 * [DockRunGeometry.contextualSpacingDp]). The Home mode's icon size and spacing are not touched.
 */
internal fun dockContextualSlotMetrics(
    dock: DockModel,
    maxRunMainAxisDp: Int,
): DockSlotRenderMetrics =
    DockSlotRenderMetrics(
        iconSizeDp = DockRunGeometry.CONTEXTUAL_ITEM_DP,
        itemSpacingDp =
            DockRunGeometry.contextualSpacingDp(
                viewportDp = (maxRunMainAxisDp - (DOCK_MAIN_AXIS_PADDING_DP * 2)).coerceAtLeast(0),
                preferredSpacingDp = dock.itemSpacingDp,
            ),
        overflowMode = DockOverflowMode.Fits,
    )

/**
 * Where everything lies in the contextual mode's one row: the pinned icons, a divider, the live entries, a divider,
 * the tiles, all at [metrics] (see [dockContextualSlotMetrics]), and the row's end padded so scrolling to it leaves a
 * whole item at the leading edge.
 */
internal fun dockContextualRun(
    renderedSlotCount: Int,
    entryCount: Int,
    tileCount: Int,
    metrics: DockSlotRenderMetrics,
    maxRunMainAxisDp: Int,
): DockRunGeometry.Run =
    DockRunGeometry.contextual(
        staticDp = dockSlotContentMainAxisDp(renderedSlotCount, metrics),
        liveCount = (entryCount - tileCount).coerceAtLeast(0),
        tileCount = tileCount,
        entryDp = dockDynamicEntryExtentDp(),
        spacingDp = metrics.itemSpacingDp.coerceAtLeast(0),
        dividerDp = DOCK_SECTION_DIVIDER_MAIN_AXIS_DP,
        staticCount = renderedSlotCount,
        viewportDp = (maxRunMainAxisDp - (DOCK_MAIN_AXIS_PADDING_DP * 2)).coerceAtLeast(0),
    )
