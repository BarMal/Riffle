package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.LauncherPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PageOverviewCardState(
    val index: Int,
    val pageCount: Int,
    val page: LauncherPage,
    val isSelected: Boolean,
    val projectedIndex: Int = index,
)

internal data class PageOverviewDragPreview(
    val sourceIndex: Int,
    val targetIndex: Int,
)

internal data class PageOverviewReorderActions(
    val listState: LazyListState,
    val onDragStarted: (Int) -> Unit,
    val onDragPreviewChanged: (Int, Int) -> Unit,
    val onDragFinished: () -> Unit,
    val onMoveToIndex: (Int, Int) -> Unit,
)

internal fun pageOverviewProjectedVisualIndex(
    pageIndex: Int,
    dragPreview: PageOverviewDragPreview?,
): Int {
    if (dragPreview == null) return pageIndex

    return when {
        pageIndex == dragPreview.sourceIndex -> pageIndex
        dragPreview.sourceIndex < dragPreview.targetIndex &&
            pageIndex in (dragPreview.sourceIndex + 1)..dragPreview.targetIndex ->
            pageIndex - 1
        dragPreview.targetIndex < dragPreview.sourceIndex &&
            pageIndex in dragPreview.targetIndex until dragPreview.sourceIndex ->
            pageIndex + 1
        else -> pageIndex
    }
}

internal fun pageOverviewSourceIndex(
    pointerPositionPx: Float,
    visibleItems: List<LazyListItemInfo>,
): Int? =
    visibleItems
        .firstOrNull { item ->
            pointerPositionPx in item.offset.toFloat()..(item.offset + item.size).toFloat()
        }?.index
        ?: visibleItems.minByOrNull { item -> abs(item.offset + (item.size / 2) - pointerPositionPx) }?.index

@Composable
internal fun Modifier.pageOverviewReflow(
    state: PageOverviewCardState,
    reducedMotion: Boolean,
): Modifier {
    val cardStepPx =
        with(LocalDensity.current) {
            (PAGE_OVERVIEW_CARD_WIDTH_DP.dp + PAGE_OVERVIEW_CARD_SPACING_DP.dp).toPx()
        }
    val reflowOffsetX = remember(state.page.id) { Animatable(0f) }
    var previousIndex by remember(state.page.id) { mutableIntStateOf(state.index) }
    val projectedOffsetX =
        animateFloatAsState(
            targetValue = (state.projectedIndex - state.index) * cardStepPx,
            animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else spring(),
            label = "page-overview-projected-position",
        )

    LaunchedEffect(state.index, cardStepPx, reducedMotion) {
        val startOffsetX =
            pageOverviewReflowInitialOffsetPx(
                previousIndex = previousIndex,
                newIndex = state.index,
                cardStepPx = cardStepPx,
                reducedMotion = reducedMotion,
            )
        previousIndex = state.index
        reflowOffsetX.snapTo(startOffsetX)
        if (startOffsetX != 0f && !reducedMotion) {
            reflowOffsetX.animateTo(targetValue = 0f, animationSpec = spring())
        }
    }

    return graphicsLayer { translationX = reflowOffsetX.value + projectedOffsetX.value }
}

@Composable
internal fun Modifier.pageOverviewReorderDrag(
    pageCount: Int,
    actions: PageOverviewReorderActions,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentActions by rememberUpdatedState(actions)
    val cardStepPx =
        with(LocalDensity.current) {
            (PAGE_OVERVIEW_CARD_WIDTH_DP.dp + PAGE_OVERVIEW_CARD_SPACING_DP.dp).toPx()
        }

    return this
        .pointerInput(pageCount, cardStepPx) {
            var sourceIndex: Int? = null
            var dragDistancePx = 0f
            var scrollDistancePx = 0f
            var edgeScrollJob: Job? = null
            var edgeScrollDirection = 0

            fun updatePreview() {
                sourceIndex?.let { index ->
                    currentActions.onDragPreviewChanged(
                        index,
                        pageOverviewDropTargetIndex(
                            index = index,
                            pageCount = pageCount,
                            dragDistancePx = dragDistancePx,
                            scrollDistancePx = scrollDistancePx,
                            cardStepPx = cardStepPx,
                        ),
                    )
                }
            }

            fun updateEdgeScroll(pointerPositionPx: Float) {
                val layoutInfo = currentActions.listState.layoutInfo
                val direction =
                    pageOverviewViewportEdgeScrollDirection(
                        pointerPositionPx = pointerPositionPx,
                        viewportStartPx = layoutInfo.viewportStartOffset.toFloat(),
                        viewportEndPx = layoutInfo.viewportEndOffset.toFloat(),
                    )
                if (direction == edgeScrollDirection) return

                edgeScrollDirection = direction
                edgeScrollJob?.cancel()
                edgeScrollJob =
                    if (direction == 0) {
                        null
                    } else {
                        scope.launch {
                            while (isActive) {
                                val appliedScrollPx =
                                    currentActions.listState.scrollBy(
                                        direction * PAGE_OVERVIEW_EDGE_SCROLL_STEP_PX,
                                    )
                                if (appliedScrollPx == 0f) break

                                scrollDistancePx += appliedScrollPx
                                updatePreview()
                                delay(PAGE_OVERVIEW_EDGE_SCROLL_FRAME_DELAY_MILLIS)
                            }
                        }
                    }
            }

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    sourceIndex =
                        pageOverviewSourceIndex(
                            pointerPositionPx = offset.x,
                            visibleItems = currentActions.listState.layoutInfo.visibleItemsInfo,
                        )
                    if (sourceIndex == null) return@detectDragGesturesAfterLongPress

                    dragDistancePx = 0f
                    scrollDistancePx = 0f
                    currentActions.onDragStarted(sourceIndex!!)
                    updatePreview()
                    updateEdgeScroll(offset.x)
                },
                onDrag = { change, dragAmount ->
                    if (sourceIndex == null) return@detectDragGesturesAfterLongPress

                    change.consume()
                    dragDistancePx += dragAmount.x
                    updatePreview()
                    updateEdgeScroll(change.position.x)
                },
                onDragCancel = {
                    edgeScrollJob?.cancel()
                    edgeScrollDirection = 0
                    sourceIndex = null
                    currentActions.onDragFinished()
                },
                onDragEnd = {
                    val index = sourceIndex ?: return@detectDragGesturesAfterLongPress
                    val targetIndex =
                        pageOverviewDropTargetIndex(
                            index = index,
                            pageCount = pageCount,
                            dragDistancePx = dragDistancePx,
                            scrollDistancePx = scrollDistancePx,
                            cardStepPx = cardStepPx,
                        )

                    edgeScrollJob?.cancel()
                    edgeScrollDirection = 0
                    sourceIndex = null
                    currentActions.onDragFinished()
                    if (targetIndex != index) {
                        currentActions.onMoveToIndex(index, targetIndex)
                    }
                },
            )
        }
}

internal fun pageOverviewViewportEdgeScrollDirection(
    pointerPositionPx: Float,
    viewportStartPx: Float,
    viewportEndPx: Float,
): Int {
    require(viewportEndPx > viewportStartPx) { "Viewport must have positive width." }

    return when {
        pointerPositionPx <= viewportStartPx + PAGE_OVERVIEW_EDGE_SCROLL_ZONE_PX -> -1
        pointerPositionPx >= viewportEndPx - PAGE_OVERVIEW_EDGE_SCROLL_ZONE_PX -> 1
        else -> 0
    }
}

internal fun pageOverviewDropTargetIndex(
    index: Int,
    pageCount: Int,
    dragDistancePx: Float,
    scrollDistancePx: Float = 0f,
    cardStepPx: Float,
): Int {
    require(pageCount > 0) { "Page overview requires at least one page." }
    require(index in 0 until pageCount) { "Page index must be within the overview." }
    require(cardStepPx > 0f) { "Card step must be positive." }

    return (
        index + ((dragDistancePx + scrollDistancePx) / cardStepPx).roundToInt()
    ).coerceIn(0, pageCount - 1)
}

internal fun pageOverviewReflowStartOffsetPx(
    previousIndex: Int,
    newIndex: Int,
    cardStepPx: Float,
): Float {
    require(cardStepPx > 0f) { "Card step must be positive." }

    return (previousIndex - newIndex) * cardStepPx
}

internal fun pageOverviewReflowInitialOffsetPx(
    previousIndex: Int,
    newIndex: Int,
    cardStepPx: Float,
    reducedMotion: Boolean,
): Float =
    if (reducedMotion) {
        0f
    } else {
        pageOverviewReflowStartOffsetPx(
            previousIndex = previousIndex,
            newIndex = newIndex,
            cardStepPx = cardStepPx,
        )
    }

private const val PAGE_OVERVIEW_EDGE_SCROLL_ZONE_PX = 56f
private const val PAGE_OVERVIEW_EDGE_SCROLL_STEP_PX = 20f
private const val PAGE_OVERVIEW_EDGE_SCROLL_FRAME_DELAY_MILLIS = 16L
