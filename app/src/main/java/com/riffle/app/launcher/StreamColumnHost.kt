package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.stream.STREAM_PAGES_TEST_TAG
import com.riffle.app.launcher.stream.StreamDestination
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.stream.StreamColumn
import com.riffle.core.domain.launcher.stream.StreamColumnRect
import com.riffle.core.domain.launcher.stream.StreamColumnWindow

/** The test tag of the Stream's column on an unfolded posture: its pages' rectangle, as wide as a resting stack. */
internal const val STREAM_COLUMN_TEST_TAG = STREAM_PAGES_TEST_TAG

/** The test tag of the column's host: the room an open card grows in. */
internal const val STREAM_COLUMN_HOST_TEST_TAG = "stream-column-host"

/**
 * The Stream on an unfolded posture (ADR 0003, Amendment 2): the same pager of upright card stacks as folded,
 * standing in a column attached to the Dock's inner side, with Home's grid beneath and beside it (it fades under the
 * column, see [GridFadeField]). The column is [bounds] (dp, in the root's coordinates, read in layout so the column
 * widening as a card opens relays out without recomposing). It sits under the Dock in the draw order, so the Dock
 * keeps its touches, and takes the touches of its own rectangle, so a tap on its empty part never reaches an app
 * faded out beneath.
 */
@Composable
@Suppress("LongParameterList")
internal fun StreamColumnSurface(
    stream: StreamHost,
    state: LauncherShellState,
    bounds: () -> StreamColumnRect?,
    restBounds: () -> StreamColumnRect?,
    onCloseStream: () -> Unit,
    appIconLoader: AppIconLoader,
    onConnectTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    // The column's pages stand where the column does at rest, in the host's own coordinates.
    val pageBounds: () -> StreamColumnRect? =
        remember(bounds, restBounds) {
            {
                val host = bounds()
                val rest = restBounds()
                if (host == null || rest == null) {
                    null
                } else {
                    StreamColumnRect(rest.left - host.left, 0f, rest.right - host.left, host.height)
                }
            }
        }
    Box(modifier = Modifier.fillMaxSize().zIndex(HOME_CONTENT_Z_INDEX).then(modifier)) {
        // The host is as wide and tall as an open card may be and never changes size while a card opens or closes:
        // nothing is relaid out per frame and nothing can clip the card. Its pages stand in the column's own
        // rectangle ([pageBounds]); the rest of it takes no touches.
        Box(modifier = Modifier.dpBounds(bounds).testTag(STREAM_COLUMN_HOST_TEST_TAG)) {
            StreamDestination(
                host = stream,
                deviceClass = state.homeLayoutSet.activeKey.deviceClass,
                installedApps = state.installedApps,
                appIconLoader = appIconLoader,
                reducedMotion = state.launcherSettings.motion.reducedMotion,
                // The column is already inside the system bars and beside the Dock.
                contentPadding = PaddingValues(),
                liveAppSlots = state.homeLayout.dock.liveAppSlots,
                onConnectTasks = onConnectTasks,
                pinnedApps = visibleLayout.pinnedApps(),
                appShortcuts = state.appShortcutsByApp,
                launcherRole = state.homeRoleStatus != HomeRoleStatus.NOT_DEFAULT_HOME,
                // An open card grows from where its browse card stands, toward the screen's middle (Amendment 2): it
                // does not travel to the centre of the column.
                streamSettings = state.launcherSettings.stream.copy(openCardPlacement = OpenCardPlacement.ABOVE_DOCK),
                // The actions sit on the card: a side Dock holds no row, and a bottom Dock's would stand under the
                // column.
                dockCanHoldActions = false,
                pageBounds = pageBounds,
                onEmptyTap = onCloseStream,
            )
        }
    }
}

/**
 * Sizes and places the node at [bounds] (dp, in the parent's coordinates) each layout pass: the node's own bounds,
 * its hit area and its semantics, are the column's. Without bounds (the window is not measured yet) it takes no room.
 */
internal fun Modifier.dpBounds(bounds: () -> StreamColumnRect?): Modifier =
    this
        .offset {
            val rect = bounds()
            if (rect == null) IntOffset.Zero else IntOffset(rect.left.dp.roundToPx(), rect.top.dp.roundToPx())
        }
        .layout { measurable, constraints ->
            val rect = bounds()
            if (rect == null) {
                layout(0, 0) {}
            } else {
                val left = rect.left.dp.roundToPx()
                val top = rect.top.dp.roundToPx()
                val width = (rect.right.dp.roundToPx() - left).coerceAtLeast(0)
                val height = (rect.bottom.dp.roundToPx() - top).coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
        }

/**
 * A value that eases between 0 and 1 with [target], on the smooth spring (a snap under reduced motion). The animation
 * runs in the frame clock and is read where it is drawn, so a change never recomposes its reader.
 */
@Composable
internal fun rememberEasedFraction(
    target: Boolean,
    reducedMotion: Boolean,
): Animatable<Float, AnimationVector1D> {
    val fraction = remember { Animatable(if (target) 1f else 0f) }
    LaunchedEffect(target, reducedMotion) {
        fraction.animateTo(if (target) 1f else 0f, RiffleMotion.smooth(reducedMotion, visibilityThreshold = 0.001f))
    }
    return fraction
}

/**
 * Where the Stream's column stands ([bounds], read in layout and draw) and the fade of Home's grid beneath it
 * ([fade]). The column widens and grows as a card opens and the fade comes in with the Stream; both ease on the frame
 * clock and are read where they are drawn.
 */
@Stable
internal class StreamColumnGeometry(
    val fade: GridFadeField,
    /** The column's host: the room an open card grows in (constant while a card opens or closes). */
    val bounds: () -> StreamColumnRect?,
    /** The column as it stands at rest (no card open): where unfolded search stands, the same rectangle. */
    val restBounds: () -> StreamColumnRect?,
)

private fun Rect.toDp(density: Float) =
    StreamColumnRect(
        left / density,
        top / density,
        right / density,
        bottom / density,
    )

@Composable
@Suppress("LongParameterList")
internal fun rememberStreamColumnGeometry(
    /** Whether Home stays behind the Stream, so its grid fades under the stack (unfolded, or folded with apps on). */
    columnMode: Boolean,
    streamShown: Boolean,
    closing: Boolean,
    /** How far the open card has grown, 0 to 1: the one motion the column's fade follows. Read in draw only. */
    openProgress: () -> Float,
    peekThrough: Boolean,
    reducedMotion: Boolean,
    dockEdge: DockPosition,
    dockExtent: Dp,
    insets: PaddingValues,
    dockBounds: Rect?,
    dockRestBounds: Rect?,
    hinges: List<StreamColumnRect>,
    rootSize: IntSize,
): StreamColumnGeometry {
    val density = LocalDensity.current.density
    val present = rememberEasedFraction(columnMode && streamShown && !closing, reducedMotion)
    val window =
        if (rootSize == IntSize.Zero) {
            null
        } else {
            StreamColumnWindow(
                widthDp = rootSize.width / density,
                heightDp = rootSize.height / density,
                dockEdge = dockEdge,
                dockExtentDp = dockExtent.value,
                safeLeftDp = insets.calculateLeftPadding(LayoutDirection.Ltr).value,
                safeTopDp = insets.calculateTopPadding().value,
                safeRightDp = insets.calculateRightPadding(LayoutDirection.Ltr).value,
                safeBottomDp = insets.calculateBottomPadding().value,
                dockBounds = dockBounds?.toDp(density),
                dockRestBounds = dockRestBounds?.toDp(density),
                verticalHingesDp = hinges,
            )
        }
    // The host stands at the open size for good; the column the grid fades under grows with the card's own motion.
    val bounds: () -> StreamColumnRect? = remember(window) { { window?.let { StreamColumn.rect(it, 1f) } } }
    val restBounds: () -> StreamColumnRect? = remember(window) { { window?.let { StreamColumn.rect(it, 0f) } } }
    val latestProgress by rememberUpdatedState(openProgress)
    val latestColumnMode by rememberUpdatedState(columnMode)
    val visible: () -> StreamColumnRect? =
        remember(window) {
            {
                val progress = if (latestColumnMode) latestProgress() else 0f
                window?.let { StreamColumn.rect(it, progress) }
            }
        }
    val fade = remember { GridFadeField() }
    val presence: () -> Float = remember(present) { { present.value } }
    val open: () -> Float = remember { { if (latestColumnMode) latestProgress() else 0f } }
    SideEffect {
        fade.column = visible
        fade.presence = presence
        fade.open = open
        fade.peekThrough = peekThrough
        fade.density = density
    }
    return remember(fade, bounds, restBounds) { StreamColumnGeometry(fade, bounds, restBounds) }
}
