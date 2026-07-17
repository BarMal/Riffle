package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.home.LauncherPage
import kotlinx.coroutines.launch
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

internal data class PageOverviewCardDragActions(
    val listState: LazyListState,
    val onMoveToIndex: (Int) -> Unit,
    val onDragPreviewChanged: (Int?) -> Unit,
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
    state: PageOverviewCardState,
    dragActions: PageOverviewCardDragActions,
): Modifier {
    var dragOffsetX by remember(state.page.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val cardStepPx =
        with(LocalDensity.current) {
            (PAGE_OVERVIEW_CARD_WIDTH_DP.dp + PAGE_OVERVIEW_CARD_SPACING_DP.dp).toPx()
        }

    return this
        .zIndex(if (dragOffsetX != 0f) PAGE_OVERVIEW_DRAG_Z_INDEX else 0f)
        .graphicsLayer {
            translationX = dragOffsetX
            shadowElevation = if (dragOffsetX != 0f) PAGE_OVERVIEW_DRAG_ELEVATION else 0f
        }
        .pointerInput(state.page.id, state.index, state.pageCount, cardStepPx) {
            var totalDragX = 0f

            detectDragGesturesAfterLongPress(
                onDragStart = {
                    totalDragX = 0f
                    dragOffsetX = 0f
                    dragActions.onDragPreviewChanged(state.index)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    dragOffsetX = totalDragX
                    dragActions.onDragPreviewChanged(
                        pageOverviewDropTargetIndex(
                            index = state.index,
                            pageCount = state.pageCount,
                            dragDistancePx = totalDragX,
                            cardStepPx = cardStepPx,
                        ),
                    )
                    pageOverviewEdgeScrollDistancePx(
                        dragOffsetX = dragOffsetX,
                        cardWidthPx = cardStepPx,
                    )?.let { scrollDistancePx ->
                        scope.launch { dragActions.listState.scrollBy(scrollDistancePx) }
                    }
                },
                onDragCancel = {
                    dragOffsetX = 0f
                    dragActions.onDragPreviewChanged(null)
                },
                onDragEnd = {
                    val targetIndex =
                        pageOverviewDropTargetIndex(
                            index = state.index,
                            pageCount = state.pageCount,
                            dragDistancePx = totalDragX,
                            cardStepPx = cardStepPx,
                        )

                    dragOffsetX = 0f
                    dragActions.onDragPreviewChanged(null)
                    if (targetIndex != state.index) {
                        dragActions.onMoveToIndex(targetIndex)
                    }
                },
            )
        }
}

internal fun pageOverviewEdgeScrollDistancePx(
    dragOffsetX: Float,
    cardWidthPx: Float,
): Float? {
    require(cardWidthPx > 0f) { "Card width must be positive." }

    val edgeThresholdPx = cardWidthPx / PAGE_OVERVIEW_EDGE_SCROLL_TRIGGER_DIVISOR
    return when {
        dragOffsetX >= edgeThresholdPx -> PAGE_OVERVIEW_EDGE_SCROLL_DISTANCE_PX
        dragOffsetX <= -edgeThresholdPx -> -PAGE_OVERVIEW_EDGE_SCROLL_DISTANCE_PX
        else -> null
    }
}

internal fun pageOverviewDropTargetIndex(
    index: Int,
    pageCount: Int,
    dragDistancePx: Float,
    cardStepPx: Float,
): Int {
    require(pageCount > 0) { "Page overview requires at least one page." }
    require(index in 0 until pageCount) { "Page index must be within the overview." }
    require(cardStepPx > 0f) { "Card step must be positive." }

    return (index + (dragDistancePx / cardStepPx).roundToInt()).coerceIn(0, pageCount - 1)
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

private const val PAGE_OVERVIEW_DRAG_Z_INDEX = 2f
private const val PAGE_OVERVIEW_DRAG_ELEVATION = 16f
private const val PAGE_OVERVIEW_EDGE_SCROLL_TRIGGER_DIVISOR = 2f
private const val PAGE_OVERVIEW_EDGE_SCROLL_DISTANCE_PX = 28f
