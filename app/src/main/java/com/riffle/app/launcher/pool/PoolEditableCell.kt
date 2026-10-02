package com.riffle.app.launcher.pool

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeTarget
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import kotlin.math.roundToInt

internal const val POOL_EDIT_ITEM_TEST_TAG_PREFIX = "workspace-preview-pool-edit-item-"

private val SelectionCorner = 12.dp
private val SelectionBorder = 2.dp
private const val DRAG_ALPHA = 0.8f

/**
 * One item of the page in edit mode. A tap selects it (so the panel offers its actions), a long press then drag moves
 * it by whole cells with the placement engine's collision rules (the same rules as the buttons), and TalkBack gets a
 * custom action for every move and removal, so nothing is drag-only. The content underneath is not interactive in edit
 * mode (a tap does not launch), and its own semantics are replaced by one label that says what the item is.
 */
@Composable
internal fun PoolEditableCell(
    item: LauncherItem,
    page: LauncherPage,
    cellPx: Float,
    target: PoolHomeTarget,
    edit: PoolEditUi,
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = edit.controller
    val id = PoolItemId(item.id.value)
    val label = item.displayLabel()
    val origin = item.placement?.cell
    var dragOffset by remember(id) { mutableStateOf(Offset.Zero) }
    val currentOrigin by rememberUpdatedState(origin)
    val currentCell by rememberUpdatedState(cellPx)
    val actions = remember(controller, target, id, label) { itemActions(controller, target, id, label) }
    val description = if (item is WidgetItem) "$label, widget" else label
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(POOL_EDIT_ITEM_TEST_TAG_PREFIX + item.id.value)
                .zIndex(if (dragOffset != Offset.Zero) 1f else 0f)
                .graphicsLayer {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    alpha = if (dragOffset != Offset.Zero) DRAG_ALPHA else 1f
                }
                .then(
                    if (selected) {
                        Modifier.border(
                            SelectionBorder,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(SelectionCorner),
                        )
                    } else {
                        Modifier
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = description
                    this.selected = selected
                    onClick(label = "Select $label") {
                        controller.select(id.takeUnless { selected })
                        true
                    }
                    customActions = actions
                },
    ) {
        content()
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable { controller.select(id.takeUnless { selected }) }
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { controller.select(id) },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                            },
                            onDragEnd = {
                                val cell = currentCell
                                val from = currentOrigin
                                if (from != null && cell > 0f) {
                                    val columns = (dragOffset.x / cell).roundToInt()
                                    val rows = (dragOffset.y / cell).roundToInt()
                                    if (columns != 0 || rows != 0) {
                                        controller.moveToCell(
                                            target,
                                            id,
                                            label,
                                            page.id,
                                            GridCell(from.column + columns, from.row + rows),
                                        )
                                    }
                                }
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = { dragOffset = Offset.Zero },
                        )
                    },
        )
    }
}

/** The TalkBack custom actions of an item in edit mode: every move and both removals. */
private fun itemActions(
    controller: PoolEditController,
    target: PoolHomeTarget,
    id: PoolItemId,
    label: String,
): List<CustomAccessibilityAction> =
    listOf(
        action(PoolEditText.MOVE_LEFT) { controller.move(target, id, label, -1, 0) },
        action(PoolEditText.MOVE_RIGHT) { controller.move(target, id, label, 1, 0) },
        action(PoolEditText.MOVE_UP) { controller.move(target, id, label, 0, -1) },
        action(PoolEditText.MOVE_DOWN) { controller.move(target, id, label, 0, 1) },
        action(PoolEditText.MOVE_PREVIOUS_PAGE) { controller.moveToPage(target, id, label, -1) },
        action(PoolEditText.MOVE_NEXT_PAGE) { controller.moveToPage(target, id, label, 1) },
        action(PoolEditText.REMOVE) { controller.remove(target, id, label) },
        action(PoolEditText.DELETE_EVERYWHERE) { controller.requestDelete(id) },
    )

private fun action(
    label: String,
    block: () -> Unit,
) = CustomAccessibilityAction(label) {
    block()
    true
}

private fun LauncherItem.displayLabel(): String =
    when (this) {
        is AppShortcutItem -> label
        is FolderItem -> label
        is WidgetItem -> label
    }
