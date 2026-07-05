package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.home.LauncherPage
import kotlin.math.roundToInt

internal data class PageOverviewCardState(
    val index: Int,
    val pageCount: Int,
    val page: LauncherPage,
    val isSelected: Boolean,
)

@Composable
internal fun Modifier.pageOverviewReorderDrag(
    state: PageOverviewCardState,
    onMoveToIndex: (Int) -> Unit,
): Modifier {
    var dragOffsetX by remember(state.page.id) { mutableFloatStateOf(0f) }
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
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    dragOffsetX = totalDragX
                },
                onDragCancel = { dragOffsetX = 0f },
                onDragEnd = {
                    val targetIndex =
                        (state.index + (totalDragX / cardStepPx).roundToInt())
                            .coerceIn(0, state.pageCount - 1)

                    dragOffsetX = 0f
                    if (targetIndex != state.index) {
                        onMoveToIndex(targetIndex)
                    }
                },
            )
        }
}

private const val PAGE_OVERVIEW_DRAG_Z_INDEX = 2f
private const val PAGE_OVERVIEW_DRAG_ELEVATION = 16f
