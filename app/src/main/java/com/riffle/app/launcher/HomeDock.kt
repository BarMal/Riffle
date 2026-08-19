@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
internal fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationGroupsByApp: List<AppNotificationGroup>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    position: DockPosition = DockPosition.BOTTOM,
    interactions: DockInteractions,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview? = null,
    dynamicEntries: List<DockDynamicEntry> = emptyList(),
    onShowAllNotifications: () -> Unit = {},
) {
    val presentation = DockPresentation(notificationGroupsByApp, appShortcutsByApp, widgetViewFactory, interactions)

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = dock.alignment.toBoxAlignment(),
    ) {
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = dock,
                isEditing = isEditing,
                // The dock's run is the width of this Box on a top or bottom edge, its height on a side.
                availableMainAxisDp =
                    if (position.isHorizontalEdge) maxWidth.value.toInt() else maxHeight.value.toInt(),
                previewSlotCount = if (widgetPickerDockPreview != null) 1 else 0,
                runsHorizontally = position.isHorizontalEdge,
                dynamicEntryCount = dynamicEntries.size,
            ) ?: return@BoxWithConstraints
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )
        DockSurfaceStrip(
            modifier =
                Modifier
                    .dockShelfPolicies(interactions)
                    .testTag(HOME_DOCK_SURFACE_TEST_TAG),
            dock = dock,
            surfaceMetrics = surfaceMetrics,
            isEditing = isEditing,
            presentation = presentation,
            appIconLoader = appIconLoader,
            position = position,
            widgetPickerDockPreview = widgetPickerDockPreview,
            dynamicEntries = dynamicEntries,
            onShowAllNotifications = onShowAllNotifications,
        )
    }
}

@Suppress("LongMethod")
@Composable
internal fun DockSlotStrip(
    dock: DockModel,
    renderedSlotCount: Int,
    contentViewportMainAxisDp: Int,
    slotMetrics: DockSlotRenderMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    position: DockPosition = DockPosition.BOTTOM,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview? = null,
) {
    val scrollState = rememberScrollState()
    val dragState = remember { mutableStateOf<DockDragState?>(null) }
    val moveToHomeItemId = remember { mutableStateOf<LauncherItemId?>(null) }
    val homeLayout = presentation.interactions.homeLayout
    val slotPresentation =
        presentation.copy(
            interactions =
                presentation.interactions.copy(
                    onAction = { action ->
                        if (action is LauncherShellAction.MoveDockItemToHome && homeLayout != null) {
                            moveToHomeItemId.value = action.itemId
                        } else {
                            presentation.interactions.onAction(action)
                        }
                    },
                ),
        )
    val previewItems = dock.items.dockItemsForPreview(dragState.value)
    val overflowAffordance =
        DockOverflowAffordance(
            scrollOffsetPx = scrollState.value,
            maxScrollOffsetPx = scrollState.maxValue,
        )
    val fadeColor = dockSurfaceColor(dock)

    val runsHorizontally = position.isHorizontalEdge
    val contentMainAxisDp = dockSlotContentMainAxisDp(renderedSlotCount, slotMetrics).dp

    Box(
        modifier =
            Modifier
                .dockRunSize(runsHorizontally, contentViewportMainAxisDp.dp)
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        DockSlotRun(
            runsHorizontally = runsHorizontally,
            modifier =
                Modifier
                    .dockRunSize(runsHorizontally, contentMainAxisDp)
                    .dockRunScroll(runsHorizontally, scrollState),
            itemSpacingDp = slotMetrics.itemSpacingDp,
        ) {
            repeat(renderedSlotCount) { index ->
                val candidateIndex = widgetPickerDockPreview?.dockIndex
                val isWidgetCandidate = candidateIndex == index
                val itemIndex = if (candidateIndex != null && index > candidateIndex) index - 1 else index
                val previewItem = previewItems.getOrNull(itemIndex)
                // A preview reflow moves items between visual slots. Key actual items by their
                // stable launcher ID so the dragged node keeps its pointer-input coroutine until
                // the gesture commits or cancels.
                val slotKey =
                    if (isWidgetCandidate) {
                        "widget-candidate:$index"
                    } else {
                        previewItem?.id ?: "dock-placeholder:$index"
                    }
                key(slotKey) {
                    if (isWidgetCandidate) {
                        WidgetPickerDockPlaceholder(
                            preview = requireNotNull(widgetPickerDockPreview),
                            sizeDp = slotMetrics.iconSizeDp,
                        )
                    } else {
                        DockSlot(
                            modifier = Modifier.requiredSize(slotMetrics.iconSizeDp.dp),
                            state =
                                DockSlotState(
                                    item = dockSlotItemState(previewItem),
                                    shortcutIndex = dock.items.indexOfFirst { item -> item.id == previewItem?.id },
                                    visualIndex = index,
                                    shortcutCount = dock.items.size,
                                    iconSizeDp = slotMetrics.iconSizeDp,
                                    itemSpacingDp = slotMetrics.itemSpacingDp,
                                    isEditing = isEditing,
                                    position = position,
                                ),
                            presentation = slotPresentation,
                            appIconLoader = appIconLoader,
                            dragState = dragState.value,
                            dragViewport = DockDragViewport(scrollState, contentViewportMainAxisDp),
                            onDragStateChanged = { dragState.value = it },
                        )
                    }
                }
            }
        }

        if (overflowAffordance.showStart) {
            DockOverflowFade(runsHorizontally = runsHorizontally, atRunStart = true, color = fadeColor)
        }
        if (overflowAffordance.showEnd) {
            DockOverflowFade(runsHorizontally = runsHorizontally, atRunStart = false, color = fadeColor)
        }
    }

    moveToHomeItemId.value?.let { itemId ->
        homeLayout?.let { layout ->
            DockToHomeDestinationDialog(
                itemId = itemId,
                layout = layout,
                onDismissRequest = { moveToHomeItemId.value = null },
                onDestinationSelected = { pageId, cell ->
                    presentation.interactions.onAction(dockMoveToHomeAction(itemId, pageId, cell))
                    moveToHomeItemId.value = null
                },
            )
        }
    }
}

/**
 * The slots themselves, laid out along the dock's run.
 *
 * A Row and a Column with the same children, chosen by axis, rather than one scrollable list with a
 * direction parameter: the slots are a handful of fixed-size items, so nothing here needs lazy
 * layout, and the two arrangements read plainly.
 */
@Composable
private fun DockSlotRun(
    runsHorizontally: Boolean,
    modifier: Modifier,
    itemSpacingDp: Int,
    content: @Composable () -> Unit,
) {
    if (runsHorizontally) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(itemSpacingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(itemSpacingDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/** Sizes along the dock's run and leaves the other axis to the content. */
private fun Modifier.dockRunSize(
    runsHorizontally: Boolean,
    mainAxis: Dp,
): Modifier = if (runsHorizontally) width(mainAxis) else height(mainAxis)

@Composable
private fun Modifier.dockRunScroll(
    runsHorizontally: Boolean,
    scrollState: ScrollState,
): Modifier = if (runsHorizontally) horizontalScroll(scrollState) else verticalScroll(scrollState)

/** The hint that the strip continues past an edge, drawn across the run at whichever end it is. */
@Composable
private fun BoxScope.DockOverflowFade(
    runsHorizontally: Boolean,
    atRunStart: Boolean,
    color: Color,
) {
    val transparent = color.copy(alpha = 0f)
    val colors = if (atRunStart) listOf(color, transparent) else listOf(transparent, color)
    Box(
        modifier =
            Modifier
                .align(dockOverflowFadeAlignment(runsHorizontally, atRunStart))
                .then(
                    if (runsHorizontally) {
                        Modifier.width(DOCK_OVERFLOW_FADE_WIDTH_DP.dp).fillMaxHeight()
                    } else {
                        Modifier.height(DOCK_OVERFLOW_FADE_WIDTH_DP.dp).fillMaxWidth()
                    },
                )
                .background(
                    if (runsHorizontally) {
                        Brush.horizontalGradient(colors = colors)
                    } else {
                        Brush.verticalGradient(colors = colors)
                    },
                ),
    )
}

private fun dockOverflowFadeAlignment(
    runsHorizontally: Boolean,
    atRunStart: Boolean,
): Alignment =
    when {
        runsHorizontally && atRunStart -> Alignment.CenterStart
        runsHorizontally -> Alignment.CenterEnd
        atRunStart -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }

@Composable
private fun WidgetPickerDockPlaceholder(
    preview: WidgetPickerDockPlacementPreview,
    sizeDp: Int,
) {
    val color =
        if (preview.isValid) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    Box(
        modifier =
            Modifier
                .requiredSize(sizeDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .testTag(WIDGET_PICKER_DOCK_PREVIEW_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (preview.isValid) "Place" else "Full",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (preview.isValid) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
        )
    }
}

/**
 * Caps how long a horizontal dock's run may get, so a wide tablet does not stretch six icons across
 * the whole screen. An absolute dp is the right shape here: the cap exists because screens get
 * wider than a dock usefully needs to be, which is a statement about dp, not about proportion.
 */
private const val DOCK_MAX_HORIZONTAL_MAIN_AXIS_DP = 560

/**
 * Caps a vertical dock's run as a share of the height it was offered instead.
 *
 * A side dock runs down a screen whose height it does not know in advance, and a fixed dp would
 * either crowd a short screen or stop well short of a tall one. The overlay dock reaches the same
 * conclusion for its tall expanded form with its own screen fraction.
 */
private const val DOCK_MAX_VERTICAL_MAIN_AXIS_FRACTION = 0.7f
internal const val DOCK_CROSS_AXIS_CHROME_DP = 32
internal const val DOCK_MAIN_AXIS_PADDING_DP = 14
internal const val DOCK_CROSS_AXIS_PADDING_DP = 10

/** The seam between the dock's two sections along its run: a 1dp rule with 8dp either side. */
internal const val DOCK_SECTION_DIVIDER_MAIN_AXIS_DP = 17

private const val DOCK_OVERFLOW_FADE_WIDTH_DP = 20
private const val DOCK_EDGE_AUTO_SCROLL_ZONE_DP = 28
private const val DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT = 24f
private const val DOCK_DRAG_SLOT_HYSTERESIS = 0.15f
private const val DOCK_EDGE_AUTO_SCROLL_FRAME_DELAY_MILLIS = 16L
internal const val HOME_DOCK_SURFACE_TEST_TAG = "home-dock-surface"
internal const val WIDGET_PICKER_DOCK_PREVIEW_TEST_TAG = "widget-picker-dock-preview"

/**
 * The longest run the dock may have, given how much it was offered and which way it runs.
 *
 * The two axes want different kinds of cap, so this is where the difference lives rather than in
 * one number that has to suit both.
 */
internal fun dockMaxMainAxisDp(
    availableMainAxisDp: Int,
    runsHorizontally: Boolean,
): Int =
    if (runsHorizontally) {
        DOCK_MAX_HORIZONTAL_MAIN_AXIS_DP
    } else {
        (availableMainAxisDp.coerceAtLeast(0) * DOCK_MAX_VERTICAL_MAIN_AXIS_FRACTION).toInt()
    }

/**
 * The dock's thickness -- across its run, not along it. The strip is one icon deep plus chrome
 * whichever edge it sits on, so this is the same arithmetic for a bottom dock's height and a side
 * dock's width.
 */
internal fun dockCrossAxisDp(iconSizeDp: Int): Int = iconSizeDp + DOCK_CROSS_AXIS_CHROME_DP

/**
 * How much room the slots themselves get along the dock's run, after the dock's own padding.
 *
 * [availableDockMainAxisDp] is taken as already capped, because how long a run may get depends on
 * which way it runs and only the caller knows that -- re-applying the horizontal cap here would
 * clamp a vertical dock to a number chosen for wide screens. The default is that horizontal cap,
 * for callers asking about a horizontal dock without a container to measure against.
 */
internal fun dockContentViewportMainAxisDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableDockMainAxisDp: Int = DOCK_MAX_HORIZONTAL_MAIN_AXIS_DP,
): Int {
    if (slotCount <= 0) {
        return 0
    }
    val contentMainAxis = (slotCount * iconSizeDp) + ((slotCount - 1) * itemSpacingDp)
    val maxContentMainAxis =
        (availableDockMainAxisDp - (DOCK_MAIN_AXIS_PADDING_DP * 2)).coerceAtLeast(0)
    return min(contentMainAxis, maxContentMainAxis)
}

internal fun dockContainerMainAxisDp(
    availableMainAxisDp: Int,
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    backgroundSizing: DockBackgroundSizing,
    runsHorizontally: Boolean = true,
): Int {
    val maxDockMainAxis =
        min(availableMainAxisDp, dockMaxMainAxisDp(availableMainAxisDp, runsHorizontally)).coerceAtLeast(0)
    if (backgroundSizing == DockBackgroundSizing.FIXED) {
        return maxDockMainAxis
    }
    val contentViewportMainAxis =
        dockContentViewportMainAxisDp(
            slotCount = slotCount,
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            availableDockMainAxisDp = maxDockMainAxis,
        )
    return min(maxDockMainAxis, contentViewportMainAxis + (DOCK_MAIN_AXIS_PADDING_DP * 2))
}

/**
 * How much of the dock's run the dynamic section gets, once the static side has had its share.
 *
 * The static side is the one the user built, so it is sized first and in full; the dynamic section
 * takes what is left. That ordering is the point -- notifications come and go, and a section that
 * sized itself first would shove the pinned icons along the dock every time one arrived.
 *
 * [maxRunMainAxisDp] is taken as already capped for the same reason [dockContentViewportMainAxisDp]
 * takes its own: how long a run may get depends on which way it runs, and only the caller knows
 * that. What does not fit is reached by scrolling the section rather than by shrinking its tiles,
 * so an entry is always the size of a dock icon whatever the run has room for.
 */
internal fun dockDynamicSectionMainAxisDp(
    entryCount: Int,
    entryExtentDp: Int,
    entrySpacingDp: Int,
    staticContainerMainAxisDp: Int,
    maxRunMainAxisDp: Int,
): Int {
    if (entryCount <= 0 || entryExtentDp <= 0) {
        return 0
    }
    val wanted = (entryCount * entryExtentDp) + ((entryCount - 1) * entrySpacingDp.coerceAtLeast(0))
    val room = maxRunMainAxisDp - staticContainerMainAxisDp - DOCK_SECTION_DIVIDER_MAIN_AXIS_DP
    return min(wanted, room).coerceAtLeast(0)
}

internal fun dockRenderedSlotCount(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
): Int =
    when {
        capacity <= 0 && isEditing -> 0
        isEditing -> capacity.coerceAtLeast(itemCount)
        // Browsing never renders vacant slots. This also keeps legacy capacity-zero layouts
        // recoverable: their persisted items remain reachable for launch or removal.
        else -> itemCount
    }

internal fun dockBackgroundVisible(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
    backgroundSizing: DockBackgroundSizing,
): Boolean =
    when {
        backgroundSizing == DockBackgroundSizing.FIXED -> true
        !isEditing && itemCount > 0 -> true
        capacity <= 0 -> false
        // An empty app catalog can be a transient recovery state. Keep an enabled dynamic dock
        // visible so it does not disappear until app discovery completes.
        else -> true
    }

internal data class DockOverflowAffordance(
    val showStart: Boolean,
    val showEnd: Boolean,
) {
    constructor(
        scrollOffsetPx: Int,
        maxScrollOffsetPx: Int,
    ) : this(
        showStart = maxScrollOffsetPx > 0 && scrollOffsetPx > 0,
        showEnd = maxScrollOffsetPx > 0 && scrollOffsetPx < maxScrollOffsetPx,
    )
}

internal data class DockPresentation(
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp,
    val widgetViewFactory: HomeWidgetViewFactory,
    val interactions: DockInteractions,
)

internal data class DockInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onFolderOpen: (FolderItem) -> Unit = {},
    val isShelfExpanded: Boolean = false,
    val onShelfExpandedChange: ((Boolean) -> Unit)? = null,
    /**
     * How the shelf is reached. Only [DockExpandAffordance.GESTURE] attaches the swipe handler;
     * [DockExpandAffordance.BUTTON] leaves the drag alone and drives [onShelfExpandedChange] from a
     * visible control instead.
     */
    val shelfExpandAffordance: DockExpandAffordance = DockExpandAffordance.GESTURE,
    /** The edge this dock is on, which is the direction its shelf opens away from. */
    val position: DockPosition = DockPosition.BOTTOM,
    val reducedMotion: Boolean = false,
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
    val homeLayout: HomeLayout? = null,
    /** What a tap on a pinned app opens: the app (grid), or its stage where it has one (Cards). */
    val staticTapBehaviour: DockStaticTapBehaviour = DockStaticTapBehaviour.Launch,
    val onAction: (LauncherShellAction) -> Unit,
)

internal fun DockAlignment.toBoxAlignment(): Alignment =
    when (this) {
        DockAlignment.START -> Alignment.CenterStart
        DockAlignment.CENTER -> Alignment.Center
        DockAlignment.END -> Alignment.CenterEnd
    }

internal fun dockMoveToHomeAction(
    itemId: LauncherItemId,
    pageId: LauncherPageId,
    cell: GridCell,
): LauncherShellAction.MoveDockItemToHome =
    LauncherShellAction.MoveDockItemToHome(
        itemId = itemId,
        pageId = pageId,
        cell = cell,
    )

@Composable
private fun DockToHomeDestinationDialog(
    itemId: LauncherItemId,
    layout: HomeLayout,
    onDismissRequest: () -> Unit,
    onDestinationSelected: (LauncherPageId, GridCell) -> Unit,
) {
    val pages = layout.pages.filter { page -> page.type !is LauncherPageType.Generated }
    if (pages.isEmpty()) {
        return
    }
    val selectedPageId =
        remember(itemId) {
            mutableStateOf(
                layout.selectedPageId.takeIf { it in pages.map(LauncherPage::id) } ?: pages.first().id,
            )
        }
    val selectedPage = pages.first { page -> page.id == selectedPageId.value }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Move to Home") },
        text = {
            Column {
                Text("Choose page")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    pages.forEachIndexed { index, page ->
                        TextButton(onClick = { selectedPageId.value = page.id }) {
                            Text(if (page.id == selectedPage.id) "Page ${index + 1}" else "${index + 1}")
                        }
                    }
                }
                Text("Choose cell")
                repeat(selectedPage.grid.rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(selectedPage.grid.columns) { column ->
                            val cell = GridCell(column = column, row = row)
                            TextButton(onClick = { onDestinationSelected(selectedPage.id, cell) }) {
                                Text("${column + 1},${row + 1}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Cancel") } },
    )
}

private data class DockSlotState(
    val item: DockSlotItemState?,
    val shortcutIndex: Int,
    val visualIndex: Int,
    val shortcutCount: Int,
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
    val isEditing: Boolean,
    /** The edge the dock is on, which is what tells a drag which way is along its run and which is off it. */
    val position: DockPosition,
)

internal data class DockDragState(
    val itemId: LauncherItemId,
    val originIndex: Int,
    val targetIndex: Int,
)

private data class DockDragViewport(
    val scrollState: androidx.compose.foundation.ScrollState,
    val contentViewportMainAxisDp: Int,
)

internal fun dockItemTestTag(itemId: LauncherItemId): String = "dock-item:${itemId.value}"

internal fun DockSlotItemState.isDirectDockDragEligible(): Boolean {
    return this is DockSlotItemState.Shortcut || this is DockSlotItemState.Folder
}

internal fun List<LauncherItem>.dockItemsForPreview(drag: DockDragState?): List<LauncherItem> {
    if (drag == null || drag.itemId !in map { it.id }) return this
    val sourceIndex = indexOfFirst { it.id == drag.itemId }
    val targetIndex = drag.targetIndex.coerceIn(0, lastIndex)
    return toMutableList().apply { add(targetIndex, removeAt(sourceIndex)) }
}

/**
 * Returns a bounded content-scroll delta while a drag is inside either overflow edge zone.
 * Callers apply the result to [androidx.compose.foundation.ScrollState.dispatchRawDelta], which
 * additionally clamps it to the actual scroll range.
 */
internal fun dockEdgeAutoScrollDelta(
    pointerX: Float,
    viewportWidthPx: Float,
    edgeZonePx: Float,
): Float {
    if (viewportWidthPx <= 0f || edgeZonePx <= 0f) return 0f

    val edgePressure =
        when {
            pointerX < edgeZonePx -> (pointerX - edgeZonePx) / edgeZonePx
            pointerX > viewportWidthPx - edgeZonePx -> (pointerX - (viewportWidthPx - edgeZonePx)) / edgeZonePx
            else -> 0f
        }
    return (edgePressure * DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT)
        .coerceIn(-DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT, DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT)
}

/** Keeps a candidate in its current slot until the drag clears a hysteresis-adjusted boundary. */
internal fun dockDragTargetIndex(
    originIndex: Int,
    currentTargetIndex: Int,
    draggedSlotDeltaPx: Float,
    slotWidthPx: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || slotWidthPx <= 0f) return originIndex

    val draggedSlots = draggedSlotDeltaPx / slotWidthPx
    var targetIndex = currentTargetIndex.coerceIn(0, itemCount - 1)
    while (
        targetIndex < itemCount - 1 &&
        draggedSlots > (targetIndex - originIndex) + 0.5f + DOCK_DRAG_SLOT_HYSTERESIS
    ) {
        targetIndex += 1
    }
    while (
        targetIndex > 0 &&
        draggedSlots < (targetIndex - originIndex) - 0.5f - DOCK_DRAG_SLOT_HYSTERESIS
    ) {
        targetIndex -= 1
    }
    return targetIndex
}

private data class DockShortcutState(
    val iconSizeDp: Int,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
)

@Composable
private fun DockSlot(
    modifier: Modifier,
    state: DockSlotState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    dragState: DockDragState?,
    dragViewport: DockDragViewport,
    onDragStateChanged: (DockDragState?) -> Unit,
) {
    val editingSlotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .then(
                    if (state.isEditing) {
                        Modifier.clip(LocalLauncherCardShape.current).background(editingSlotColor)
                    } else {
                        Modifier
                    },
                )
                .then(state.item?.let { item -> Modifier.testTag(dockItemTestTag(item.id)) } ?: Modifier)
                .dockItemDrag(
                    state = state,
                    slotSizeDp = state.iconSizeDp,
                    itemSpacingDp = state.itemSpacingDp,
                    isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
                    dragViewport = dragViewport,
                    onDragStateChanged = onDragStateChanged,
                    onAction = presentation.interactions.onAction,
                )
                .graphicsLayer {
                    if (dragState?.itemId == state.item?.id) {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        shadowElevation = 12f
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        when (val item = state.item) {
            null -> Unit
            is DockSlotItemState.Shortcut ->
                DockShortcut(
                    shortcut = item.shortcut,
                    state =
                        DockShortcutState(
                            iconSizeDp = state.iconSizeDp,
                            shortcutIndex = state.shortcutIndex,
                            shortcutCount = state.shortcutCount,
                            isEditing = state.isEditing,
                            notificationCount =
                                presentation.notificationGroupsByApp.notificationCountFor(
                                    item.shortcut,
                                ),
                            appShortcuts = presentation.appShortcutsByApp[item.shortcut.appIdentity].orEmpty(),
                        ),
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                )
            is DockSlotItemState.Folder ->
                DockFolder(
                    folder = item.folder,
                    state = state,
                    presentation = presentation,
                )
            is DockSlotItemState.Widget ->
                DockWidgetSlot(
                    widget = item.widget,
                    iconSizeDp = state.iconSizeDp,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                    presentation = presentation,
                )
            is DockSlotItemState.Placeholder ->
                DockItemPlaceholder(
                    item = item,
                    iconSizeDp = state.iconSizeDp,
                )
        }
    }
}

@Suppress("LongParameterList")
private fun Modifier.dockItemDrag(
    state: DockSlotState,
    slotSizeDp: Int,
    itemSpacingDp: Int,
    isRtl: Boolean,
    dragViewport: DockDragViewport,
    onDragStateChanged: (DockDragState?) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
): Modifier {
    return state.item
        ?.takeIf { item -> state.isEditing && item.isDirectDockDragEligible() }
        ?.id
        ?.let { itemId ->
            pointerInput(itemId, state.shortcutIndex, state.shortcutCount, slotSizeDp, itemSpacingDp, state.position) {
                coroutineScope {
                    var dragXPx = 0f
                    var dragYPx = 0f
                    var targetIndex = state.shortcutIndex
                    var initialScrollOffset = 0
                    var edgeAutoScrollDelta = 0f
                    var autoScrollJob: Job? = null

                    fun updateCandidate(slotSizePx: Float) {
                        targetIndex =
                            dockDragTargetIndex(
                                originIndex = state.shortcutIndex,
                                currentTargetIndex = targetIndex,
                                draggedSlotDeltaPx =
                                    state.position.dragAlongRunPx(dragXPx, dragYPx) +
                                        dragViewport.scrollState.value - initialScrollOffset,
                                slotWidthPx = slotSizePx,
                                itemCount = state.shortcutCount,
                            )
                        onDragStateChanged(DockDragState(itemId, state.shortcutIndex, targetIndex))
                    }

                    fun updateEdgeAutoScroll(slotSizePx: Float) {
                        if (edgeAutoScrollDelta == 0f) {
                            autoScrollJob?.cancel()
                            return
                        }
                        if (autoScrollJob?.isActive == true) return
                        autoScrollJob =
                            launch {
                                while (isActive && edgeAutoScrollDelta != 0f) {
                                    val scrollOffset = dragViewport.scrollState.value
                                    dragViewport.scrollState.dispatchRawDelta(edgeAutoScrollDelta)
                                    if (dragViewport.scrollState.value == scrollOffset) break
                                    updateCandidate(slotSizePx)
                                    delay(DOCK_EDGE_AUTO_SCROLL_FRAME_DELAY_MILLIS)
                                }
                            }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragXPx = 0f
                            dragYPx = 0f
                            targetIndex = state.shortcutIndex
                            initialScrollOffset = dragViewport.scrollState.value
                            edgeAutoScrollDelta = 0f
                            onDragStateChanged(DockDragState(itemId, state.shortcutIndex, state.shortcutIndex))
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragXPx += amount.x
                            dragYPx += amount.y
                            val slotSizePx = density * (slotSizeDp + itemSpacingDp)
                            val viewportSizePx = density * dragViewport.contentViewportMainAxisDp
                            val pointerAlongRunPx =
                                (state.visualIndex * slotSizePx) - dragViewport.scrollState.value +
                                    state.position.dragAlongRunPx(change.position.x, change.position.y)
                            edgeAutoScrollDelta =
                                dockEdgeAutoScrollDelta(
                                    pointerX = pointerAlongRunPx,
                                    viewportWidthPx = viewportSizePx,
                                    edgeZonePx = density * DOCK_EDGE_AUTO_SCROLL_ZONE_DP,
                                )
                            updateCandidate(slotSizePx)
                            updateEdgeAutoScroll(slotSizePx)
                        },
                        onDragEnd = {
                            autoScrollJob?.cancel()
                            dockDragDropAction(
                                itemId = itemId,
                                originIndex = state.shortcutIndex,
                                targetIndex = targetIndex,
                                awayFromEdgePx =
                                    state.position.dragAwayFromEdgePx(dragXPx, dragYPx, isRtl = isRtl),
                                dockItemSizePx = density * slotSizeDp,
                            )?.let(onAction)
                            onDragStateChanged(null)
                        },
                        onDragCancel = {
                            autoScrollJob?.cancel()
                            onDragStateChanged(null)
                        },
                    )
                }
            }
        } ?: this
}

/**
 * What a finished dock drag means: a move to another slot, a move out to home, or nothing.
 *
 * [awayFromEdgePx] is the drag measured off the dock's edge rather than up the screen, so a dock on
 * a side reads a sideways pull the same way a bottom dock reads an upward one.
 */
internal fun dockDragDropAction(
    itemId: LauncherItemId,
    originIndex: Int,
    targetIndex: Int,
    awayFromEdgePx: Float,
    dockItemSizePx: Float,
): LauncherShellAction? =
    when {
        awayFromEdgePx >= dockItemSizePx -> LauncherShellAction.MoveDockItemToHome(itemId)
        targetIndex != originIndex -> LauncherShellAction.MoveDockShortcutToIndex(itemId, targetIndex)
        else -> null
    }

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockFolder(
    folder: FolderItem,
    state: DockSlotState,
    presentation: DockPresentation,
) {
    val isContextMenuExpanded = remember(folder.id) { mutableStateOf(false) }
    val modifier =
        if (state.isEditing) {
            Modifier.clickable(onClick = { isContextMenuExpanded.value = true })
        } else {
            Modifier.clickable(onClick = { presentation.interactions.onFolderOpen(folder) })
        }

    Box(modifier = Modifier.requiredSize(state.iconSizeDp.dp)) {
        DockItemPlaceholder(
            item =
                DockSlotItemState.Placeholder(
                    id = folder.id,
                    label = folder.label,
                    kind = DockSlotPlaceholderKind.FOLDER,
                ),
            iconSizeDp = state.iconSizeDp,
            modifier = modifier,
        )
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockFolderContextMenuItems(
                    folder = folder,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockShortcut(
    shortcut: AppShortcutItem,
    state: DockShortcutState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .requiredSize(state.iconSizeDp.dp),
    ) {
        LauncherAppIcon(
            identity = shortcut.appIdentity,
            label = shortcut.label,
            iconLoader = appIconLoader,
            modifier =
                Modifier.requiredSize(state.iconSizeDp.dp).then(
                    if (state.isEditing) {
                        Modifier.clickable(onClick = { isContextMenuExpanded.value = true })
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                presentation.interactions.onAction(
                                    presentation.interactions.staticTapBehaviour.actionFor(shortcut),
                                )
                            },
                            onLongClick = {
                                presentation.interactions.haptics.longPress()
                                isContextMenuExpanded.value = true
                            },
                            onLongClickLabel = "Show ${shortcut.label} actions",
                        )
                    },
                ),
        )

        if (!state.isEditing) {
            NotificationCountBadge(
                count = state.notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockShortcutContextMenuItems(
                    shortcut = shortcut,
                    appShortcuts = state.appShortcuts,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}

internal fun dockShortcutContextMenuItems(
    shortcut: AppShortcutItem,
    appShortcuts: List<AppShortcut> = emptyList(),
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(shortcut.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action =
                        LauncherShellAction.MoveDockShortcutToIndex(
                            shortcut.id,
                            targetIndex = shortcutCount - 1,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }

    // Browsing, a tap may select the app's stage rather than open it (Cards), so opening has to
    // stay reachable somewhere; the long-press menu is that somewhere. Harmless where a tap already
    // opens -- there it is simply the same thing the tap does.
    val openItems =
        if (isEditing) {
            emptyList()
        } else {
            listOf(ShortcutContextMenuItem(label = "Open", action = shortcut.launchAction()))
        }

    return editItems +
        openItems +
        shortcutContextMenuItems(
            shortcut = shortcut,
            surface = ShortcutContextSurface.DOCK,
            appShortcuts = appShortcuts,
        )
}
