@file:Suppress("TooManyFunctions") // The drag's feedback pieces: ghosts, folder state, lifted icon, arbitration.

package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Where the dragged item would land: an outline only (no fill) of the item's own silhouette. An app shows the outline
 * of its icon baked once ([HomeDragGhosts]); a folder and a widget show a rounded rectangle (a widget: of its span).
 */
@Composable
internal fun HomeDragPlaceholder(
    span: GridSpan,
    cellSize: Dp,
    cellSizePx: Float,
    fillSpan: Boolean,
    iconSizeDp: Int,
    ghostOf: AppIdentity? = null,
    appIconLoader: AppIconLoader? = null,
) {
    if (ghostOf != null && appIconLoader != null && !fillSpan) {
        HomeGhostIcon(
            identity = ghostOf,
            iconLoader = appIconLoader,
            outline = true,
            modifier = Modifier.size(iconSizeDp.dp),
        )
        return
    }
    val outlineColor = MaterialTheme.colorScheme.outline
    val sizeModifier =
        if (fillSpan) {
            Modifier
                .requiredWidth(cellSize * span.columns.coerceAtLeast(1))
                .requiredHeight(cellSize * span.rows.coerceAtLeast(1))
                .graphicsLayer {
                    translationX = ((span.columns.coerceAtLeast(1) - 1) * cellSizePx) / 2f
                    translationY = ((span.rows.coerceAtLeast(1) - 1) * cellSizePx) / 2f
                }
        } else {
            Modifier.size(iconSizeDp.dp)
        }
    Box(
        modifier =
            sizeModifier.drawBehind {
                drawRoundRect(
                    color = outlineColor,
                    cornerRadius = CornerRadius(DRAG_PLACEHOLDER_CORNER_RADIUS_DP.dp.toPx()),
                    style = Stroke(width = DRAG_PLACEHOLDER_STROKE_WIDTH_DP.dp.toPx()),
                )
            },
    )
}

/**
 * The icon of an app as one of the two ghosts of a drag: the desaturated, dimmed icon (where it was) or the outline of
 * its silhouette (where it would land). Both are baked once per icon by [HomeDragGhosts]; this only draws a bitmap.
 */
@Composable
internal fun HomeGhostIcon(
    identity: AppIdentity,
    iconLoader: AppIconLoader,
    outline: Boolean,
    modifier: Modifier = Modifier,
) {
    var ghost by remember(identity, iconLoader) {
        mutableStateOf(iconLoader.cachedIconFor(identity)?.let { HomeDragGhosts.forIcon(identity, it) })
    }
    LaunchedEffect(identity, iconLoader) {
        if (ghost == null) {
            ghost =
                withContext(Dispatchers.Default) {
                    iconLoader.iconFor(identity)?.let { HomeDragGhosts.forIcon(identity, it) }
                }
        }
    }
    val baked = ghost
    if (baked != null) {
        Image(
            bitmap = if (outline) baked.destination else baked.source,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        val color = MaterialTheme.colorScheme.outline
        Box(
            modifier =
                modifier.drawBehind {
                    drawRoundRect(
                        color = color,
                        cornerRadius = CornerRadius(DRAG_PLACEHOLDER_CORNER_RADIUS_DP.dp.toPx()),
                        style = Stroke(width = DRAG_PLACEHOLDER_STROKE_WIDTH_DP.dp.toPx()),
                    )
                },
        )
    }
}

/** The "will make a folder" state of the app a drag holds in the dead zone of: [scale] shrinks it, [armed] outlines. */
internal class FolderWillForm(val armed: Boolean, val scale: Float)

@Composable
internal fun rememberFolderWillForm(
    active: Boolean,
    holdMillis: Int,
    reducedMotion: Boolean,
): FolderWillForm {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(active, holdMillis) {
        armed = false
        if (active) {
            delay(holdMillis.toLong())
            armed = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (armed) FOLDER_WILL_FORM_SCALE else 1f,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "folder-will-form",
    )
    return FolderWillForm(armed = armed, scale = scale)
}

/** The folder outline around the target icon once [armed]; drawn behind it, in the draw phase. */
@Composable
internal fun Modifier.folderWillFormOutline(
    armed: Boolean,
    iconSizeDp: Int,
): Modifier {
    val color = MaterialTheme.colorScheme.primary
    return if (!armed) {
        this
    } else {
        drawBehind {
            val side = (iconSizeDp.dp * FOLDER_OUTLINE_RATIO).toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                size = androidx.compose.ui.geometry.Size(side, side),
                cornerRadius = CornerRadius(DRAG_PLACEHOLDER_CORNER_RADIUS_DP.dp.toPx()),
                style = Stroke(width = DRAG_PLACEHOLDER_STROKE_WIDTH_DP.dp.toPx()),
            )
        }
    }
}

/**
 * The lifted icon: follows the finger, slightly enlarged, drawn above the pager so it survives a page turn. The
 * position is read in the layout phase from [session], never by recomposing the grid.
 */
@Composable
internal fun HomeLiftedItem(
    session: () -> HomeDragSession?,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
) {
    val current = session() ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier =
                Modifier
                    .onSizeChanged { size = it }
                    .offset {
                        val pointer = session()?.pointerInRoot ?: Offset.Unspecified
                        if (pointer == Offset.Unspecified) {
                            IntOffset(-LIFTED_OFFSCREEN, -LIFTED_OFFSCREEN)
                        } else {
                            IntOffset(
                                (pointer.x - origin.x - size.width / 2f).roundToInt(),
                                (pointer.y - origin.y - size.height / 2f).roundToInt(),
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = LIFTED_SCALE
                        scaleY = LIFTED_SCALE
                        shadowElevation = DRAG_GHOST_ELEVATION
                    },
        ) {
            HomeDraggedItemContent(current.item, presentation, appIconLoader)
        }
    }
}

/**
 * Home's one pick-up arbitration (ADR 0002: the platform's long-press drag detector, nothing competing with it):
 * - a quick move before the long press is not ours (the pager scrolls the page);
 * - at the long press the item lifts and [onStationaryLongPress] opens its menu;
 * - hold still and release: the menu stays; move past the touch slop while pressed: [onMenuDismiss] closes the menu
 *   and the lift becomes a drag, wherever the item is (no edit mode is needed).
 * The latest item, drag state and callbacks are read at the time of each event (never captured at the start), so an
 * edit that moved the item, the page or the grid between two drags cannot leave the detector working on stale data.
 */
internal fun Modifier.homeItemDrag(
    enabled: Boolean,
    item: LauncherItem,
    dragState: HomeItemDragState,
    actions: HomeWorkspaceActions,
    onStationaryLongPress: (() -> Unit)? = null,
    onMenuDismiss: (() -> Unit)? = null,
): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val latestItem by rememberUpdatedState(item)
            val latestState by rememberUpdatedState(dragState)
            val latestActions by rememberUpdatedState(actions)
            val latestMenu by rememberUpdatedState(onStationaryLongPress)
            val latestDismiss by rememberUpdatedState(onMenuDismiss)
            val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

            fun toRoot(local: Offset): Offset = coordinates.value?.takeIf { it.isAttached }?.localToRoot(local) ?: local
            val slop = LocalViewConfiguration.current.touchSlop
            this
                .onGloballyPositioned { coordinates.value = it }
                .pointerInput(item.id) {
                    var dragX = 0f
                    var dragY = 0f
                    var local = Offset.Zero
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { down ->
                            dragX = 0f
                            dragY = 0f
                            local = down
                            moved = false
                            latestActions.haptics.longPress()
                            latestActions.onDragSessionChanged(
                                HomeDragSession(
                                    item = latestItem,
                                    originPageId = latestState.pageId,
                                    originCell = latestState.cell,
                                    projectedCell = latestState.cell,
                                    pointerInRoot = toRoot(down),
                                ),
                            )
                            // Hold still: the menu. Moving past the slop turns it into the drag and closes it.
                            latestMenu?.invoke()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                            local = change.position
                            if (!moved && hypot(dragX, dragY) > slop) {
                                moved = true
                                latestDismiss?.invoke()
                            }
                            latestActions.onDragSessionChanged(
                                homeDragSessionForUpdate(
                                    previousSession = latestActions.currentDragSession(),
                                    item = latestItem,
                                    dragState = latestState,
                                    dragOffsetX = dragX,
                                    dragOffsetY = dragY,
                                ).copy(pointerInRoot = toRoot(local)),
                            )
                        },
                        onDragEnd = {
                            val session = latestActions.currentDragSession()
                            latestActions.onDragSessionChanged(null)
                            if (moved) {
                                homeDropAction(latestItem, latestState, session, dragX, dragY)
                                    ?.let(latestActions.onAction)
                            }
                        },
                        onDragCancel = { latestActions.onDragSessionChanged(null) },
                    )
                }
        }
    }

/**
 * What releasing a Home drag does: over the Dock, pin it; on a planned page, drop on top (a folder) inside a dead zone
 * or land at the planned cell with the pushed neighbours moved aside; nowhere valid (no landing), nothing, so the item
 * returns to where it was. Without a plan (no bounds known yet) the older cell-delta rule applies.
 */
internal fun homeDropAction(
    item: LauncherItem,
    dragState: HomeItemDragState,
    session: HomeDragSession?,
    dragX: Float,
    dragY: Float,
): LauncherShellAction? {
    val plan = session?.plan
    return when {
        session == null || plan == null ->
            homeItemDragDropAction(item, dragState, dragX, dragY, session?.targetPageId ?: dragState.pageId)
        session.isOverDock && item.isDockTransferable ->
            LauncherShellAction.MoveHomeItemToDock(item.id, pageId = dragState.pageId)
        plan.landing == null -> null
        plan.dropOn != null -> LauncherShellAction.MoveHomeShortcutToCell(item.id, plan.landing!!)
        plan.landing == dragState.cell && plan.pushed.isEmpty() && session.targetPageId == dragState.pageId -> null
        else ->
            LauncherShellAction.DropHomeItem(
                itemId = item.id,
                sourcePageId = dragState.pageId,
                targetPageId = session.targetPageId,
                cell = plan.landing!!,
                pushed = plan.pushed,
            )
    }
}

internal fun homeDragSessionForUpdate(
    previousSession: HomeDragSession?,
    item: LauncherItem,
    dragState: HomeItemDragState,
    dragOffsetX: Float,
    dragOffsetY: Float,
): HomeDragSession =
    HomeDragSession(
        item = item,
        originPageId = dragState.pageId,
        originCell = dragState.cell,
        targetPageId =
            previousSession
                ?.takeIf { session -> session.item.id == item.id && session.originPageId == dragState.pageId }
                ?.targetPageId
                ?: dragState.pageId,
        dragOffsetX = dragOffsetX,
        dragOffsetY = dragOffsetY,
        projectedCell = dragState.projectedCell(dragX = dragOffsetX, dragY = dragOffsetY),
        isOverDock = item.isDockTransferable && dragState.isDraggedBelowGrid(dragOffsetY),
        plan = previousSession?.takeIf { it.item.id == item.id }?.plan,
    )

@Composable
internal fun HomeDraggedItemContent(
    item: LauncherItem,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (item) {
            is AppShortcutItem ->
                LauncherAppIcon(
                    identity = item.appIdentity,
                    label = item.label,
                    iconLoader = appIconLoader,
                    modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp),
                )

            is FolderItem ->
                FolderPreviewIcon(
                    folder = item,
                    appIconLoader = appIconLoader,
                    iconSizeDp = presentation.labelSettings.iconSizeDp,
                )

            is WidgetItem ->
                HomeWidgetPlaceholder(
                    widget = item,
                    isEditing = false,
                    onAction = {},
                )
        }
        WallpaperReadableLabel(
            text = item.dragLabel,
            settings = presentation.labelSettings,
        )
    }
}

private fun HomeItemDragState.projectedCell(
    dragX: Float,
    dragY: Float,
): GridCell =
    GridCell(
        column = cell.column + (dragX / cellSizePx).roundToInt(),
        row = cell.row + (dragY / cellSizePx).roundToInt(),
    ).coerceIn(grid)

private fun GridCell.coerceIn(grid: GridDimensions): GridCell =
    GridCell(
        column = column.coerceIn(0, grid.columns - 1),
        row = row.coerceIn(0, grid.rows - 1),
    )

private fun HomeItemDragState.dropCell(
    dragX: Float,
    dragY: Float,
): GridCell? {
    val columnDelta = (dragX / cellSizePx).roundToInt()
    val rowDelta = (dragY / cellSizePx).roundToInt()

    return when {
        columnDelta == 0 && rowDelta == 0 -> null
        else ->
            GridCell(
                column = cell.column + columnDelta,
                row = cell.row + rowDelta,
            ).coerceIn(grid)
    }
}

internal fun homeItemDragDropAction(
    item: LauncherItem,
    dragState: HomeItemDragState,
    dragX: Float,
    dragY: Float,
    targetPageId: com.riffle.core.domain.launcher.home.LauncherPageId = dragState.pageId,
): LauncherShellAction? =
    when {
        item.isDockTransferable && dragState.isDraggedBelowGrid(dragY) ->
            LauncherShellAction.MoveHomeItemToDock(item.id, pageId = dragState.pageId)

        else ->
            dragState.dropCell(dragX = dragX, dragY = dragY)?.let { targetCell ->
                if (targetPageId == dragState.pageId) {
                    LauncherShellAction.MoveHomeShortcutToCell(itemId = item.id, cell = targetCell)
                } else {
                    LauncherShellAction.MoveHomeItemToPage(
                        itemId = item.id,
                        sourcePageId = dragState.pageId,
                        targetPageId = targetPageId,
                        cell = targetCell,
                    )
                }
            }
    }

private val LauncherItem.isDockTransferable: Boolean
    get() = this is AppShortcutItem || this is FolderItem

private fun HomeItemDragState.isDraggedBelowGrid(dragY: Float): Boolean = dragY >= (grid.rows - cell.row) * cellSizePx

private val LauncherItem.dragLabel: String
    get() =
        when (this) {
            is AppShortcutItem -> label
            is FolderItem -> label
            is WidgetItem -> label
        }

private const val DRAG_PLACEHOLDER_STROKE_WIDTH_DP = 2
private const val DRAG_PLACEHOLDER_CORNER_RADIUS_DP = 12
private const val DRAG_GHOST_ELEVATION = 12f
private const val FOLDER_WILL_FORM_SCALE = 0.8f
private const val FOLDER_OUTLINE_RATIO = 1.15f
private const val LIFTED_SCALE = 1.1f
private const val LIFTED_OFFSCREEN = 10_000
