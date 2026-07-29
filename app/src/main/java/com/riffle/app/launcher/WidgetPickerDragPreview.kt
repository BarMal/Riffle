package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun WidgetPickerDragPlaceholder(
    preview: WidgetPickerDragPlacementPreview,
    cellSize: Dp,
) {
    val color =
        if (preview.isValid) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    val columns = preview.span.columns.coerceAtLeast(1)
    val rows = preview.span.rows.coerceAtLeast(1)

    Box(
        modifier =
            Modifier
                .requiredWidth(cellSize * columns)
                .requiredHeight(cellSize * rows)
                .graphicsLayer {
                    translationX = ((columns - 1) * cellSize.toPx()) / 2f
                    translationY = ((rows - 1) * cellSize.toPx()) / 2f
                }
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.18f))
                .border(2.dp, color, RoundedCornerShape(12.dp))
                .testTag(WIDGET_PICKER_DRAG_PREVIEW_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (preview.isValid) "Place ${preview.provider.label}" else "Unavailable",
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal const val WIDGET_PICKER_DRAG_PREVIEW_TEST_TAG = "widget-picker-drag-preview"
