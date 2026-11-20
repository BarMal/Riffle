package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import kotlin.math.roundToInt

internal const val DOCK_STREAM_HANDLE_TEST_TAG = "dock-stream-handle"

/** The handle's touch target (48 dp) and the glass disc drawn in it. */
private val HandleTarget = 48.dp
private val HandleDisc = 32.dp
private const val HANDLE_GAP_DP = 4
private const val HANDLE_MARGIN_DP = 8

/**
 * Where the Stream handle's top-left lies: beyond the Dock ([dock], root coordinates) toward the screen's interior
 * ([pull] is the way out), at the Dock's far end along it, held inside a root of [root] with [margin] px to spare.
 * It stands in the strip the Home handle stands in, level with it, so a hint above that strip never covers it.
 */
internal fun dockStreamHandleOffset(
    dock: Rect,
    size: IntSize,
    pull: DockPullDirection,
    root: IntSize,
    gap: Int,
    margin: Int,
): IntOffset {
    val beyond = gap
    val dockOnHorizontalEdge = pull.unitY != 0f
    val x =
        if (dockOnHorizontalEdge) {
            dock.right - size.width
        } else {
            dock.center.x + pull.unitX * (dock.width / 2f + beyond + size.width / 2f) - size.width / 2f
        }
    val y =
        if (dockOnHorizontalEdge) {
            dock.center.y + pull.unitY * (dock.height / 2f + beyond + size.height / 2f) - size.height / 2f
        } else {
            dock.bottom - size.height
        }
    return IntOffset(
        x = x.roundToInt().coerceIn(margin, maxOf(margin, root.width - size.width - margin)),
        y = y.roundToInt().coerceIn(margin, maxOf(margin, root.height - size.height - margin)),
    )
}

/** The chevron's turn so that it points the way the Dock is pulled (up for a bottom Dock). */
internal fun DockPullDirection.chevronDegrees(): Float =
    when (this) {
        DockPullDirection.UP -> 0f
        DockPullDirection.RIGHT -> 90f
        DockPullDirection.DOWN -> 180f
        DockPullDirection.LEFT -> 270f
    }

/** What the Stream handle does: a tap opens the Stream; the long-press offers the Library and hiding the handle. */
internal class StreamHandleActions(
    val onOpenStream: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onHide: () -> Unit,
)

/**
 * The Stream handle (ADR 0003, Amendments 12 and 21): a small chevron on Thin glass at the far end of the Dock, on the
 * side the Dock is pulled toward. A tap opens the Stream (what pressing the Dock does), and a touch and hold offers the
 * Library and hiding the handle. It is off unless Settings > Dock > Show Stream handle turns it on; otherwise it is
 * drawn once, as a teaching aid, while the press hint stands. It does not move.
 */
@Composable
internal fun DockStreamHandle(
    dockBounds: () -> Rect?,
    pull: DockPullDirection,
    libraryAvailable: Boolean,
    actions: StreamHandleActions,
    modifier: Modifier = Modifier,
) {
    val gap = HANDLE_GAP_DP.dp
    val margin = HANDLE_MARGIN_DP.dp
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Handle(pull = pull, libraryAvailable = libraryAvailable, actions = actions)
        },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        val dock = dockBounds()
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (dock != null) {
                placeable.place(
                    dockStreamHandleOffset(
                        dock = dock,
                        size = IntSize(placeable.width, placeable.height),
                        pull = pull,
                        root = IntSize(constraints.maxWidth, constraints.maxHeight),
                        gap = gap.roundToPx(),
                        margin = margin.roundToPx(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Handle(
    pull: DockPullDirection,
    libraryAvailable: Boolean,
    actions: StreamHandleActions,
) {
    var menu by remember { mutableStateOf(false) }
    val latestOpen by rememberUpdatedState(actions.onOpenStream)
    val label = stringResource(R.string.dock_stream_handle_open)
    val optionsLabel = stringResource(R.string.dock_stream_handle_options)
    val spokenActions = handleCustomActions(libraryAvailable, actions)
    Box(
        modifier =
            Modifier
                .size(HandleTarget)
                .testTag(DOCK_STREAM_HANDLE_TEST_TAG)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { latestOpen() }, onLongPress = { menu = true })
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                    onClick(label = label) {
                        latestOpen()
                        true
                    }
                    onLongClick(label = optionsLabel) {
                        menu = true
                        true
                    }
                    customActions = spokenActions
                },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.size(HandleDisc),
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = pull.chevronDegrees() },
                )
            }
        }
        HandleMenu(expanded = menu, libraryAvailable = libraryAvailable, actions = actions) { menu = false }
    }
}

/** "Open Library" (while the Library is on) and "Hide this handle": the handle's long-press, as custom actions. */
@Composable
private fun handleCustomActions(
    libraryAvailable: Boolean,
    actions: StreamHandleActions,
): List<CustomAccessibilityAction> {
    val libraryLabel = stringResource(R.string.dock_open_library_action)
    val hideLabel = stringResource(R.string.dock_stream_handle_hide)
    return buildList {
        if (libraryAvailable) {
            add(
                CustomAccessibilityAction(libraryLabel) {
                    actions.onOpenLibrary()
                    true
                },
            )
        }
        add(
            CustomAccessibilityAction(hideLabel) {
                actions.onHide()
                true
            },
        )
    }
}

@Composable
private fun HandleMenu(
    expanded: Boolean,
    libraryAvailable: Boolean,
    actions: StreamHandleActions,
    onDismiss: () -> Unit,
) {
    RiffleContextMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (libraryAvailable) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dock_open_library_action)) },
                onClick = {
                    onDismiss()
                    actions.onOpenLibrary()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dock_stream_handle_hide)) },
            onClick = {
                onDismiss()
                actions.onHide()
            },
        )
    }
}

/**
 * The handle where Home shows it ([enabled]: opted in, or the press hint stands): it stands while Home is the front
 * (no Stream, Library or search over it) and nothing moves or is dragged ([busy], read here so Home does not
 * recompose when a drag or a press starts and ends).
 */
@Composable
internal fun DockStreamHandleLayer(
    enabled: () -> Boolean,
    busy: () -> Boolean,
    dockBounds: () -> Rect?,
    pull: DockPullDirection,
    libraryAvailable: Boolean,
    actions: StreamHandleActions,
) {
    if (!enabled() || busy()) return
    DockStreamHandle(
        dockBounds = dockBounds,
        pull = pull,
        libraryAvailable = libraryAvailable,
        actions = actions,
    )
}
