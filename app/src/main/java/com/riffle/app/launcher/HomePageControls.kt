@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPageType
import kotlin.math.roundToInt

@Composable
fun PageEditControls(
    pageCount: Int,
    selectedPageIndex: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = selectedPageIndex > 0,
            onClick = { onAction(LauncherShellAction.SelectPreviousHomePage) },
        ) {
            Text(text = "Previous")
        }
        TextButton(onClick = { isPageMenuExpanded.value = true }) {
            Text(text = "Page ${selectedPageIndex + 1} / $pageCount")
        }
        ShortcutContextMenu(
            expanded = isPageMenuExpanded.value,
            items =
                pageManagementMenuItems(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                ),
            onDismissRequest = { isPageMenuExpanded.value = false },
            onAction = onAction,
        )
        TextButton(
            enabled = selectedPageIndex < pageCount - 1,
            onClick = { onAction(LauncherShellAction.SelectNextHomePage) },
        ) {
            Text(text = "Next")
        }
        TextButton(onClick = { onAction(LauncherShellAction.ExitHomeEditMode) }) {
            Text(text = "Done")
        }
    }
}

@Composable
fun PageOverviewControls(
    layout: HomeLayout,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = remember { mutableStateOf(false) }
    val selectedPage = layout.selectedPage

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageOverviewStrip(
            layout = layout,
            reducedMotion = reducedMotion,
            appIconLoader = appIconLoader,
            widgetViewFactory = widgetViewFactory,
            onAction = onAction,
        )
        PageTypeControls(
            selectedType = selectedPage.type,
            onAction = onAction,
        )
        PageGridControls(
            selectedGrid = selectedPage.grid,
            onAction = onAction,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { onAction(LauncherShellAction.EnterHomeEditMode) }) {
                Text(text = "Edit page")
            }
            OutlinedButton(onClick = { onAction(LauncherShellAction.AddHomePage) }) {
                Text(text = "Add page")
            }
            TextButton(onClick = { isPageMenuExpanded.value = true }) {
                Text(text = "More")
            }
            TextButton(onClick = { onAction(LauncherShellAction.ExitHomeEditMode) }) {
                Text(text = "Done")
            }
            ShortcutContextMenu(
                expanded = isPageMenuExpanded.value,
                items =
                    pageOverviewActionsMenuItems(
                        pageCount = layout.pages.size,
                        selectedPageIndex = layout.selectedPageIndex,
                        selectedPage = selectedPage,
                    ),
                onDismissRequest = { isPageMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PageOverviewStrip(
    layout: HomeLayout,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
) {
    val overviewListState = rememberLazyListState()
    var dragPreview by remember { mutableStateOf<PageOverviewDragPreview?>(null) }

    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(PAGE_OVERVIEW_STRIP_TEST_TAG),
        state = overviewListState,
        contentPadding = PaddingValues(horizontal = PAGE_OVERVIEW_CONTENT_PADDING_DP.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_CARD_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = layout.pages,
            key = { _, page -> page.id.value },
        ) { index, page ->
            PageOverviewCard(
                state =
                    PageOverviewCardState(
                        index = index,
                        pageCount = layout.pages.size,
                        page = page,
                        isSelected = page.id == layout.selectedPageId,
                        projectedIndex =
                            pageOverviewProjectedVisualIndex(
                                pageIndex = index,
                                dragPreview = dragPreview,
                            ),
                    ),
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetViewFactory,
                reducedMotion = reducedMotion,
                onClick = { onAction(LauncherShellAction.SelectHomePage(page.id)) },
                onAction = onAction,
                dragActions =
                    PageOverviewCardDragActions(
                        listState = overviewListState,
                        onMoveToIndex = { targetIndex ->
                            onAction(LauncherShellAction.MoveHomePage(pageId = page.id, targetIndex = targetIndex))
                        },
                        onDragPreviewChanged = { targetIndex ->
                            dragPreview = targetIndex?.let { PageOverviewDragPreview(index, it) }
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PageOverviewCard(
    state: PageOverviewCardState,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    dragActions: PageOverviewCardDragActions,
) {
    val isMenuExpanded = remember(state.page.id) { mutableStateOf(false) }

    Surface(
        modifier =
            Modifier
                .width(PAGE_OVERVIEW_CARD_WIDTH_DP.dp)
                .pageOverviewReflow(state = state, reducedMotion = reducedMotion)
                .pageOverviewReorderDrag(
                    state = state,
                    dragActions = dragActions,
                )
                .clip(LocalLauncherCardShape.current)
                .testTag(pageOverviewCardTestTag(state.page.id.value))
                .clickable(onClick = onClick),
        shape = LocalLauncherCardShape.current,
        color =
            if (state.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (state.isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        tonalElevation = if (state.isSelected) 4.dp else 1.dp,
        border =
            if (state.isSelected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PageOverviewCardHeader(
                state = state,
                onMoreClick = {
                    onAction(LauncherShellAction.SelectHomePage(state.page.id))
                    isMenuExpanded.value = true
                },
            )
            Text(
                text = state.page.type.pageOverviewTypeLabel,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Press and hold, then drag to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PageOverviewPreview(
                page = state.page,
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetViewFactory,
            )
            ShortcutContextMenu(
                expanded = isMenuExpanded.value,
                items = pageOverviewCardMenuItems(index = state.index, pageCount = state.pageCount),
                onDismissRequest = { isMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PageOverviewCardHeader(
    state: PageOverviewCardState,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pageOverviewLabel(index = state.index),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onMoreClick) {
            Text(text = "More")
        }
    }
}

@Composable
private fun PageGridControls(
    selectedGrid: GridDimensions,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Page grid",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pageGridDimensionOptions(selectedGrid).forEach { option ->
                FilterChip(
                    selected = option.dimensions == selectedGrid,
                    onClick = {
                        onAction(LauncherShellAction.SelectSelectedHomePageGridDimensions(option.dimensions))
                    },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

@Composable
private fun PageTypeControls(
    selectedType: LauncherPageType,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Page type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pageTypeOptions.forEach { option ->
                FilterChip(
                    selected = option.type == selectedType,
                    onClick = {
                        onAction(LauncherShellAction.SelectSelectedHomePageType(option.type))
                    },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

internal fun pageManagementMenuItems(
    pageCount: Int,
    selectedPageIndex: Int,
    includeOverview: Boolean = true,
    includeCreationActions: Boolean = true,
): List<ShortcutContextMenuItem> =
    listOfNotNull(
        if (includeCreationActions) {
            ShortcutContextMenuItem(
                label = "Add page",
                action = LauncherShellAction.AddHomePage,
            )
        } else {
            null
        },
        overviewMenuItem(includeOverview),
        if (includeCreationActions) {
            ShortcutContextMenuItem(
                label = "Duplicate page",
                action = LauncherShellAction.DuplicateSelectedHomePage,
            )
        } else {
            null
        },
        ShortcutContextMenuItem(
            label = "Move page left",
            action = LauncherShellAction.MoveSelectedHomePageLeft,
            enabled = selectedPageIndex > 0,
        ),
        ShortcutContextMenuItem(
            label = "Move page right",
            action = LauncherShellAction.MoveSelectedHomePageRight,
            enabled = selectedPageIndex < pageCount - 1,
        ),
        ShortcutContextMenuItem(
            label = "Delete page",
            action = LauncherShellAction.DeleteSelectedHomePage,
            enabled = pageCount > 1,
        ),
    )

private fun overviewMenuItem(includeOverview: Boolean): ShortcutContextMenuItem? =
    if (includeOverview) {
        ShortcutContextMenuItem(
            label = "Manage pages",
            action = LauncherShellAction.EnterHomePageOverview,
        )
    } else {
        null
    }

internal fun pageOverviewCardMenuItems(
    index: Int,
    pageCount: Int,
): List<ShortcutContextMenuItem> =
    pageManagementMenuItems(
        pageCount = pageCount,
        selectedPageIndex = index,
        includeOverview = false,
    )

internal fun pageOverviewActionsMenuItems(
    pageCount: Int,
    selectedPageIndex: Int,
    selectedPage: com.riffle.core.domain.launcher.home.LauncherPage,
): List<ShortcutContextMenuItem> =
    pageManagementMenuItems(
        pageCount = pageCount,
        selectedPageIndex = selectedPageIndex,
        includeOverview = false,
    ) +
        listOfNotNull(
            pageOverviewPinActionLabel(
                type = selectedPage.type,
                isPinned = selectedPage.isPinned,
            )?.let { label ->
                ShortcutContextMenuItem(label, LauncherShellAction.ToggleSelectedHomePagePinned)
            },
        )

/**
 * A track of dots with a draggable handle riding over the current page, for jumping several pages
 * at once faster than tapping "Next" repeatedly or waiting through one long glide.
 *
 * The handle follows the finger 1:1 -- unanimated -- while a drag is live, reporting each page it
 * crosses through [onLiveDragPageChanged] so the pager can jump there instantly and the scrub feels
 * direct rather than laggy; [onPageSelected] fires once, on release, to commit the final page the
 * ordinary way. Letting go (or a page change from anywhere else -- a swipe on the pager, "Next")
 * settles the handle onto its resting spot with a spring, the same one a real slider's thumb would
 * use.
 */
@Suppress("LongParameterList")
@Composable
fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
    reducedMotion: Boolean,
    haptics: LauncherHaptics,
    onPageSelected: (Int) -> Unit,
    onLiveDragPageChanged: (Int) -> Unit = {},
    onDragActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier =
            Modifier
                .heightIn(min = PAGE_INDICATOR_TOUCH_TARGET_HEIGHT_DP.dp)
                .then(modifier)
                .pageIndicatorSemantics(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                    onPageSelected = onPageSelected,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The dot row's own measured width, not the indicator's -- the handle's fixed touch target
        // can be wider than a short row of dots (e.g. two pages), and that must not stretch the
        // track math out past where the dots actually are. Both are re-keyed on pageCount: a changed
        // dot count remeasures to a different width, and the stale one from the old count must not
        // be trusted (or treated as "already measured") for even one frame in between.
        var trackWidthPx by remember(pageCount) { mutableStateOf(0f) }
        // Stays false until the very first real measurement, so that measurement is applied with a
        // snap rather than the settle spring -- otherwise the handle would visibly slide in from x=0
        // once layout catches up, on every single appearance of the indicator (or every page added
        // or removed, since the row's width -- and so the handle's correct resting spot -- changes).
        var hasMeasuredTrack by remember(pageCount) { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var liveDragOffsetPx by remember { mutableStateOf<Float?>(null) }
        val restingOffsetPx =
            pageIndicatorHandleRestOffsetPx(
                index = selectedPageIndex,
                trackWidthPx = trackWidthPx,
                pageCount = pageCount,
                layoutDirection = layoutDirection,
            )
        val handleOffsetPx = remember(pageCount) { Animatable(restingOffsetPx) }

        // The pointer callbacks below aren't a suspend context, so a drag reports its position
        // through this plain state instead of driving the Animatable directly; this effect is what
        // actually moves it, one snapTo per position change.
        LaunchedEffect(liveDragOffsetPx) {
            liveDragOffsetPx?.let { offsetPx -> handleOffsetPx.snapTo(offsetPx) }
        }

        LaunchedEffect(restingOffsetPx, isDragging, reducedMotion, trackWidthPx) {
            if (isDragging) return@LaunchedEffect
            if (reducedMotion || !hasMeasuredTrack) {
                handleOffsetPx.snapTo(restingOffsetPx)
                if (trackWidthPx > 0f) hasMeasuredTrack = true
            } else {
                handleOffsetPx.animateTo(restingOffsetPx, PageIndicatorHandleSettleSpec)
            }
        }

        Row(
            modifier = Modifier.onSizeChanged { size -> trackWidthPx = size.width.toFloat() },
            horizontalArrangement = Arrangement.spacedBy(PAGE_INDICATOR_DOT_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) {
                Box(
                    modifier =
                        Modifier
                            .size(PAGE_INDICATOR_DOT_DIAMETER_DP.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = PAGE_INDICATOR_DOT_ALPHA)),
                )
            }
        }

        PageIndicatorHandleOverlay(
            pageCount = pageCount,
            layoutDirection = layoutDirection,
            trackWidthPx = trackWidthPx,
            reducedMotion = reducedMotion,
            haptics = haptics,
            isDragging = isDragging,
            handleOffsetPx = handleOffsetPx,
            onDraggingChanged = { dragging ->
                isDragging = dragging
                onDragActiveChanged(dragging)
                if (!dragging) liveDragOffsetPx = null
            },
            onLiveOffsetChanged = { offsetPx -> liveDragOffsetPx = offsetPx },
            onLiveDragPageChanged = onLiveDragPageChanged,
            onPageSelected = onPageSelected,
        )
    }
}

/**
 * pageIndicatorHandleRestOffsetPx and pageIndicatorDragTargetIndex already fold RTL into offsetPx
 * themselves (0 is always the track's physical left, however the app mirrors) -- placing the
 * handle through the ambient direction on top of that would mirror it a second time. matchParentSize
 * leaves the outer Box's own placement of this wrapper alone (a child exactly its parent's size has
 * no slack for any alignment to act on, in either direction), and forcing Ltr just for this subtree
 * makes its own TopStart placement of the handle unambiguous physical pixels, matching what offsetPx
 * already means.
 */
@Suppress("LongParameterList")
@Composable
private fun BoxScope.PageIndicatorHandleOverlay(
    pageCount: Int,
    layoutDirection: LayoutDirection,
    trackWidthPx: Float,
    reducedMotion: Boolean,
    haptics: LauncherHaptics,
    isDragging: Boolean,
    handleOffsetPx: Animatable<Float, AnimationVector1D>,
    onDraggingChanged: (Boolean) -> Unit,
    onLiveOffsetChanged: (Float) -> Unit,
    onLiveDragPageChanged: (Int) -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = Modifier.matchParentSize()) {
            PageIndicatorHandle(
                offsetPx = handleOffsetPx.value,
                isLifted = isDragging,
                reducedMotion = reducedMotion,
                modifier =
                    Modifier.pageIndicatorDrag(
                        pageCount = pageCount,
                        layoutDirection = layoutDirection,
                        trackWidthPx = trackWidthPx,
                        haptics = haptics,
                        callbacks =
                            PageIndicatorDragCallbacks(
                                initialOffsetPx = { handleOffsetPx.value },
                                onDragActiveChanged = onDraggingChanged,
                                onLiveOffsetChanged = onLiveOffsetChanged,
                                onLivePageChanged = onLiveDragPageChanged,
                                onPageSelected = onPageSelected,
                            ),
                    ),
            )
        }
    }
}

/** The visible, grabbable roundel riding over the indicator's current page. */
@Composable
private fun PageIndicatorHandle(
    offsetPx: Float,
    isLifted: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val touchTargetPx = remember(density) { with(density) { PAGE_INDICATOR_HANDLE_TOUCH_TARGET_DP.dp.toPx() } }
    val lift = pageIndicatorHandleLift(isLifted = isLifted, reducedMotion = reducedMotion)

    Box(
        modifier =
            Modifier
                .offset { IntOffset(x = (offsetPx - touchTargetPx / 2f).roundToInt(), y = 0) }
                .size(PAGE_INDICATOR_HANDLE_TOUCH_TARGET_DP.dp)
                .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PAGE_INDICATOR_HANDLE_DIAMETER_DP.dp)
                    .scale(lift.scale)
                    .shadow(lift.elevation, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private data class PageIndicatorHandleLift(val scale: Float, val elevation: Dp)

/** Grow-and-lift on grab, the same idea as picking up a dock icon or a slider's thumb. */
@Composable
private fun pageIndicatorHandleLift(
    isLifted: Boolean,
    reducedMotion: Boolean,
): PageIndicatorHandleLift {
    val scaleAnimationSpec: AnimationSpec<Float> =
        if (reducedMotion) snap() else tween(PAGE_INDICATOR_HANDLE_LIFT_ANIMATION_MILLIS)
    val elevationAnimationSpec: AnimationSpec<Dp> =
        if (reducedMotion) snap() else tween(PAGE_INDICATOR_HANDLE_LIFT_ANIMATION_MILLIS)
    val scale by
        animateFloatAsState(
            targetValue = if (isLifted) PAGE_INDICATOR_HANDLE_LIFT_SCALE else 1f,
            animationSpec = scaleAnimationSpec,
            label = "page-indicator-handle-scale",
        )
    val elevation by
        animateDpAsState(
            targetValue = if (isLifted) PAGE_INDICATOR_HANDLE_LIFT_ELEVATION_DP.dp else 0.dp,
            animationSpec = elevationAnimationSpec,
            label = "page-indicator-handle-elevation",
        )
    return PageIndicatorHandleLift(scale, elevation)
}

private fun Modifier.pageIndicatorSemantics(
    pageCount: Int,
    selectedPageIndex: Int,
    onPageSelected: (Int) -> Unit,
): Modifier =
    semantics {
        contentDescription = "Page selector"
        stateDescription = pageIndicatorStateDescription(selectedPageIndex, pageCount)
        progressBarRangeInfo =
            ProgressBarRangeInfo(
                current = selectedPageIndex.toFloat(),
                range = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
            )
        if (pageCount > 1) {
            setProgress { targetValue ->
                onPageSelected(targetValue.roundToInt().coerceIn(0, pageCount - 1))
                true
            }
        }
    }

private data class PageIndicatorDragCallbacks(
    val initialOffsetPx: () -> Float,
    val onDragActiveChanged: (Boolean) -> Unit,
    val onLiveOffsetChanged: (Float) -> Unit,
    val onLivePageChanged: (Int) -> Unit,
    val onPageSelected: (Int) -> Unit,
)

/**
 * The handle's own drag: tracked by accumulated delta rather than the pointer's absolute position,
 * because the handle itself moves out from under that position as it follows the finger -- an
 * absolute reading would be relative to a track origin that keeps sliding away from where the drag
 * actually started.
 *
 * [haptics] and [callbacks] are read through [rememberUpdatedState] rather than keyed into
 * [pointerInput] directly: PageIndicator reads the handle's own live position on every recomposition
 * (it drives where the dot-overlay renders), so a fresh [PageIndicatorDragCallbacks] instance -- and
 * a changed key -- would otherwise arrive on close to every frame of a scrub drag or its settle
 * spring. [pointerInput] restarts [detectHorizontalDragGestures] from scratch whenever any of its
 * keys change, which would drop the in-progress gesture (no new pointer-down is coming) and leave
 * the drag stuck after its first move. Only [pageCount], [layoutDirection] and [trackWidthPx] are
 * kept as keys, since none of them change while a drag is actually in progress.
 */
private fun Modifier.pageIndicatorDrag(
    pageCount: Int,
    layoutDirection: LayoutDirection,
    trackWidthPx: Float,
    haptics: LauncherHaptics,
    callbacks: PageIndicatorDragCallbacks,
): Modifier =
    if (pageCount <= 1 || trackWidthPx <= 0f) {
        this
    } else {
        composed {
            val latestHaptics by rememberUpdatedState(haptics)
            val latestCallbacks by rememberUpdatedState(callbacks)
            pointerInput(pageCount, layoutDirection, trackWidthPx) {
                var currentOffsetPx = 0f
                var startPageIndex = 0
                var targetPageIndex = 0
                var isDragInProgress = false

                // pageCount, layoutDirection or trackWidthPx changing mid-drag (a page added or
                // removed while a finger is still down, say) restarts this whole pointerInput block
                // from scratch, which cancels detectHorizontalDragGestures without giving it the
                // chance to run onDragEnd or onDragCancel itself. Without this, isDragging on the
                // caller's side would never be told the drag is over and would stay stuck lifted.
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragInProgress = true
                            currentOffsetPx = latestCallbacks.initialOffsetPx()
                            startPageIndex =
                                pageIndicatorDragTargetIndex(
                                    dragPositionPx = currentOffsetPx,
                                    trackWidthPx = trackWidthPx,
                                    pageCount = pageCount,
                                    layoutDirection = layoutDirection,
                                )
                            targetPageIndex = startPageIndex
                            latestHaptics.longPress()
                            latestCallbacks.onDragActiveChanged(true)
                            latestCallbacks.onLiveOffsetChanged(currentOffsetPx)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            currentOffsetPx = (currentOffsetPx + dragAmount).coerceIn(0f, trackWidthPx)
                            latestCallbacks.onLiveOffsetChanged(currentOffsetPx)
                            val newTargetPageIndex =
                                pageIndicatorDragTargetIndex(
                                    dragPositionPx = currentOffsetPx,
                                    trackWidthPx = trackWidthPx,
                                    pageCount = pageCount,
                                    layoutDirection = layoutDirection,
                                )
                            if (newTargetPageIndex != targetPageIndex) {
                                targetPageIndex = newTargetPageIndex
                                latestHaptics.longPress()
                                latestCallbacks.onLivePageChanged(targetPageIndex)
                            }
                        },
                        onDragEnd = {
                            isDragInProgress = false
                            latestCallbacks.onDragActiveChanged(false)
                            latestCallbacks.onPageSelected(targetPageIndex)
                        },
                        onDragCancel = {
                            isDragInProgress = false
                            latestCallbacks.onDragActiveChanged(false)
                            // No commit happened, but a live jump mid-drag may already have moved the
                            // pager itself past pages it was never asked to settle on -- put it back
                            // where the drag started rather than leaving it stranded there.
                            if (targetPageIndex != startPageIndex) {
                                latestCallbacks.onLivePageChanged(startPageIndex)
                            }
                        },
                    )
                } finally {
                    if (isDragInProgress) {
                        latestCallbacks.onDragActiveChanged(false)
                    }
                }
            }
        }
    }

internal fun pageIndicatorDragTargetIndex(
    dragPositionPx: Float,
    trackWidthPx: Float,
    pageCount: Int,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Int {
    if (pageCount <= 1 || trackWidthPx <= 0f) return 0

    val trackProgress = dragPositionPx / trackWidthPx
    val logicalProgress = if (layoutDirection == LayoutDirection.Rtl) 1f - trackProgress else trackProgress
    return (logicalProgress * (pageCount - 1))
        .roundToInt()
        .coerceIn(0, pageCount - 1)
}

/** The exact inverse of [pageIndicatorDragTargetIndex]: where a page's dot sits along the track. */
internal fun pageIndicatorHandleRestOffsetPx(
    index: Int,
    trackWidthPx: Float,
    pageCount: Int,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Float {
    if (pageCount <= 1) return 0f

    val progress = index.toFloat() / (pageCount - 1)
    val trackProgress = if (layoutDirection == LayoutDirection.Rtl) 1f - progress else progress
    return (trackProgress * trackWidthPx).coerceIn(0f, trackWidthPx)
}

internal fun pageIndicatorStateDescription(
    selectedPageIndex: Int,
    pageCount: Int,
): String = "Page ${selectedPageIndex + 1} of $pageCount"

private const val PAGE_INDICATOR_TOUCH_TARGET_HEIGHT_DP = 48
private const val PAGE_INDICATOR_DOT_DIAMETER_DP = 6
private const val PAGE_INDICATOR_DOT_SPACING_DP = 6
private const val PAGE_INDICATOR_DOT_ALPHA = 0.28f
private const val PAGE_INDICATOR_HANDLE_DIAMETER_DP = 14
private const val PAGE_INDICATOR_HANDLE_TOUCH_TARGET_DP = 44
private const val PAGE_INDICATOR_HANDLE_LIFT_SCALE = 1.3f
private const val PAGE_INDICATOR_HANDLE_LIFT_ELEVATION_DP = 6
private const val PAGE_INDICATOR_HANDLE_LIFT_ANIMATION_MILLIS = 120
private val PageIndicatorHandleSettleSpec: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

private fun pageOverviewLabel(index: Int): String = "Page ${index + 1}"

internal data class PageTypeOption(
    val label: String,
    val type: LauncherPageType,
)

internal data class PageGridDimensionOption(
    val label: String,
    val dimensions: GridDimensions,
)

internal fun pageOverviewPinActionLabel(
    type: LauncherPageType,
    isPinned: Boolean,
): String? =
    when (type) {
        is LauncherPageType.Generated -> if (isPinned) "Unpin" else "Pin"
        LauncherPageType.AllApps,
        LauncherPageType.Home,
        -> null
    }

internal fun pageGridDimensionOptions(selectedGrid: GridDimensions): List<PageGridDimensionOption> =
    listOf(
        PageGridDimensionOption("${selectedGrid.columns} x ${selectedGrid.rows}", selectedGrid),
        PageGridDimensionOption(
            "${selectedGrid.columns - 1} x ${selectedGrid.rows}",
            selectedGrid.copy(columns = selectedGrid.columns - 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns + 1} x ${selectedGrid.rows}",
            selectedGrid.copy(columns = selectedGrid.columns + 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns} x ${selectedGrid.rows - 1}",
            selectedGrid.copy(rows = selectedGrid.rows - 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns} x ${selectedGrid.rows + 1}",
            selectedGrid.copy(rows = selectedGrid.rows + 1),
        ),
    )
        .filter { option -> option.dimensions.columns >= 1 && option.dimensions.rows >= 1 }
        .distinctBy { option -> option.dimensions }

internal val pageTypeOptions: List<PageTypeOption> =
    listOf(
        PageTypeOption("Classic", LauncherPageType.Home),
        PageTypeOption("All apps", LauncherPageType.AllApps),
        PageTypeOption("Today", LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY)),
        PageTypeOption("Category", LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY)),
        PageTypeOption("App", LauncherPageType.Generated(GeneratedLauncherPageKind.APP)),
        PageTypeOption("Work", LauncherPageType.Generated(GeneratedLauncherPageKind.WORK)),
        PageTypeOption("Personal", LauncherPageType.Generated(GeneratedLauncherPageKind.PERSONAL)),
        PageTypeOption("Favourites", LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES)),
        PageTypeOption("Frequent", LauncherPageType.Generated(GeneratedLauncherPageKind.FREQUENTLY_USED)),
        PageTypeOption("Cards", LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS)),
    )

internal val LauncherPageType.pageOverviewTypeLabel: String
    get() =
        when (this) {
            LauncherPageType.Home -> "Classic"
            LauncherPageType.AllApps -> "All apps"
            is LauncherPageType.Generated -> kind.pageOverviewTypeLabel
        }

private val GeneratedLauncherPageKind.pageOverviewTypeLabel: String
    get() =
        when (this) {
            GeneratedLauncherPageKind.APP -> "App"
            GeneratedLauncherPageKind.CATEGORY -> "Category"
            GeneratedLauncherPageKind.TODAY -> "Today"
            GeneratedLauncherPageKind.WORK -> "Work"
            GeneratedLauncherPageKind.PERSONAL -> "Personal"
            GeneratedLauncherPageKind.FAVOURITES -> "Favourites"
            GeneratedLauncherPageKind.FREQUENTLY_USED -> "Frequent"
            GeneratedLauncherPageKind.NOTIFICATION_CARDS -> "Cards"
        }

internal const val PAGE_OVERVIEW_CARD_WIDTH_DP = 248
internal const val PAGE_OVERVIEW_CARD_SPACING_DP = 8
private const val PAGE_OVERVIEW_CONTENT_PADDING_DP = 16
internal const val PAGE_OVERVIEW_STRIP_TEST_TAG = "page-overview-strip"

internal fun pageOverviewCardTestTag(pageId: String): String = "page-overview-card-$pageId"
