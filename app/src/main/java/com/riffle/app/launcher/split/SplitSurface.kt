package com.riffle.app.launcher.split

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.app.R
import com.riffle.app.launcher.DockPullState
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.HOME_CONTENT_Z_INDEX
import com.riffle.app.launcher.SplitPullSetup
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.split.PaneRect
import com.riffle.core.domain.launcher.split.SplitGeometry
import com.riffle.core.domain.launcher.split.SplitLayout
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.split.SplitModes
import com.riffle.core.domain.launcher.split.SplitOrientation
import com.riffle.core.domain.launcher.split.SplitReveal
import com.riffle.core.domain.launcher.split.SplitSettle
import com.riffle.core.domain.launcher.split.SplitTarget
import com.riffle.core.domain.launcher.split.SplitWindow
import com.riffle.core.domain.launcher.split.StreamPaneSide
import com.riffle.core.domain.launcher.stream.StreamSplit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val SPLIT_DIVIDER_TEST_TAG = "split-divider"
internal const val SPLIT_HOME_PANE_TEST_TAG = "split-home-pane"
internal const val SPLIT_STREAM_PANE_TEST_TAG = "split-stream-pane"

/** The divider's own layer, just above the panes and below the Dock (the panes sit at [HOME_CONTENT_Z_INDEX]). */
private const val DIVIDER_Z_INDEX = HOME_CONTENT_Z_INDEX + 0.5f

/**
 * Where Home and the Stream are on an expanded posture (ADR 0003, Folded and unfolded), as the UI holds it.
 *
 * The rules are the domain's (`launcher/split/`): this holds only what changes while the user is at it: the divider
 * being dragged ([dragShare]), a settle to one of the three states ([settling]) and the remembered [split]. A pull is
 * the Dock pull's own state ([DockPullState]); [layoutFor] reads whichever is live and returns the panes of this
 * frame, so panes move in the layout phase and nothing recomposes per frame.
 */
@Stable
@Suppress("TooManyFunctions")
internal class SplitPaneState(private val scope: CoroutineScope) {
    /** The window, insets, Dock and hinges, in dp; null until measured. */
    var window: SplitWindow? by mutableStateOf(null)

    /** The remembered split of this posture. A change is held here at once and written through [onSplitChanged]. */
    var split: StreamSplit by mutableStateOf(StreamSplit())

    /** Whether the Stream shows, counting a committed switch the shell has not shown yet. */
    var streamShown: () -> Boolean = { false }
    var reducedMotion: Boolean = false
    var onSplitChanged: (StreamSplit) -> Unit = {}

    /** Returns to Home only: the shell action, with the stand-in for its answer. */
    var onGoHome: () -> Unit = {}

    /** The Stream's share while the divider is held, unclamped; null otherwise. */
    var dragShare: Float? by mutableStateOf(null)
        private set

    /** A settle of the divider (after a drag or an action) is running. */
    var settling: Boolean by mutableStateOf(false)
        private set

    private val settleShare = Animatable(0f)
    private var settleJob: Job? = null

    private val splitAvailable: Boolean
        get() = window?.let { SplitGeometry.limits(it).splitAvailable } ?: false

    /** The mode at rest: what the shell shows, as the last-used shown mode reads on this window. */
    val restMode: SplitMode
        get() = SplitModes.modeOf(streamShown(), split.lastShown, splitAvailable)

    /** The Stream's share of a split of this posture, after the hinge's magnet. */
    val splitShare: Float
        get() = window?.let { SplitGeometry.resolveShare(it, split.fraction) } ?: split.fraction

    /** Whether something other than rest is moving the panes. */
    fun isMoving(pull: DockPullState): Boolean = (pull.running && pull.splitting) || dragShare != null || settling

    /** Where a pull starts: the mode now, the mode a press of the Dock returns to, and whether a split fits. */
    fun pullSetup(): SplitPullSetup = SplitPullSetup(restMode, split.lastShown, splitAvailable)

    /** Whether the divider is composed: in a split at rest, or being moved by the user. */
    fun showsDivider(pull: DockPullState): Boolean =
        (restMode == SplitMode.SPLIT && !pull.running) || dragShare != null || settling

    /** Whether Home is composed: showing, or on its way in or out. */
    fun composesHome(pull: DockPullState): Boolean = restMode.showsHome || isMoving(pull)

    fun composesStream(pull: DockPullState): Boolean = restMode.showsStream || isMoving(pull)

    /** The panes of this frame, or null before the window is known (the panes then fill their parent). */
    fun layoutFor(pull: DockPullState): SplitLayout? {
        val w = window ?: return null
        val drag = dragShare
        return when {
            pull.running && pull.splitting -> {
                val frame = SplitReveal.frame(pull.reveal, splitShare, pull.opening, reducedMotion)
                SplitGeometry.layout(w, SplitMode.SPLIT, frame.streamShare, transient = true)
            }
            drag != null -> {
                val limits = SplitGeometry.limits(w)
                val outside = drag < limits.minShare || drag > limits.maxShare
                SplitGeometry.layout(w, SplitMode.SPLIT, drag, transient = outside)
            }
            settling -> SplitGeometry.layout(w, SplitMode.SPLIT, settleShare.value, transient = true)
            else -> SplitGeometry.layout(w, restMode, splitShare)
        }
    }

    /** The share on screen now, for a drag or a settle to start from. */
    private fun shownShare(layout: SplitLayout?): Float =
        if (settling) settleShare.value else layout?.share ?: splitShare

    fun beginDrag(pull: DockPullState) {
        val from = shownShare(layoutFor(pull))
        settleJob?.cancel()
        settling = false
        dragShare = from
    }

    fun dragBy(deltaDp: Float) {
        val w = window ?: return
        val current = dragShare ?: return
        dragShare = (current + SplitGeometry.shareDeltaFor(w, deltaDp)).coerceIn(0f, 1f)
    }

    fun endDrag(velocityDpPerSecond: Float) {
        val w = window
        val current = dragShare
        if (w == null || current == null) {
            dragShare = null
            return
        }
        val velocity = SplitGeometry.shareDeltaFor(w, velocityDpPerSecond)
        settleTo(SplitSettle.settleDivider(w, current, velocity), from = current)
    }

    /** One keyboard step along the axis: toward the start (-1) or the end (+1) of the window. */
    fun nudge(
        pull: DockPullState,
        towardEnd: Boolean,
    ) {
        val w = window ?: return
        val towardBiggerStream = SplitGeometry.shareDeltaFor(w, if (towardEnd) 1f else -1f) > 0f
        val layout = layoutFor(pull)
        val target = SplitSettle.step(w, restMode, shownShare(layout), if (towardBiggerStream) 1 else -1)
        settleTo(target, from = shownShare(layout))
    }

    /** The accessibility actions and the divider's own end states. */
    fun goTo(
        mode: SplitMode,
        pull: DockPullState,
    ) {
        val w = window ?: return
        val target =
            when (mode) {
                SplitMode.HOME_ONLY -> SplitTarget(SplitMode.HOME_ONLY, 0f)
                SplitMode.STREAM_ONLY -> SplitTarget(SplitMode.STREAM_ONLY, 1f)
                SplitMode.SPLIT ->
                    if (SplitGeometry.limits(w).splitAvailable) {
                        SplitTarget(SplitMode.SPLIT, SplitGeometry.resolveShare(w, StreamSplit.DEFAULT_FRACTION))
                    } else {
                        SplitTarget(SplitMode.STREAM_ONLY, 1f)
                    }
            }
        goToTarget(target, pull)
    }

    /** Settles to [target] from wherever the panes are now. */
    fun goToTarget(
        target: SplitTarget,
        pull: DockPullState,
    ) = settleTo(target, from = shownShare(layoutFor(pull)))

    private fun settleTo(
        target: SplitTarget,
        from: Float,
    ) {
        settleJob?.cancel()
        settleJob =
            scope.launch {
                settleShare.snapTo(from.coerceIn(0f, 1f))
                settling = true
                dragShare = null
                // Reduced motion snaps instead of animating (RiffleMotion returns snap()).
                settleShare.animateTo(
                    target.share,
                    RiffleMotion.smooth(reducedMotion, visibilityThreshold = SHARE_THRESHOLD),
                )
                commit(target)
                settling = false
            }
    }

    private fun commit(target: SplitTarget) {
        when (target.mode) {
            SplitMode.SPLIT -> store(StreamSplit.of(target.share, SplitMode.SPLIT))
            SplitMode.STREAM_ONLY -> store(StreamSplit.of(split.fraction, SplitMode.STREAM_ONLY))
            SplitMode.HOME_ONLY -> onGoHome()
        }
    }

    /** A Dock pull ended in [mode]: it is now the last-used shown mode (Home only keeps the one remembered). */
    fun onPullSettled(mode: SplitMode) {
        store(StreamSplit.of(split.fraction, SplitModes.lastShownAfter(mode, split.lastShown)))
    }

    private fun store(next: StreamSplit) {
        if (next == split) return
        split = next
        onSplitChanged(next)
    }

    private companion object {
        const val SHARE_THRESHOLD = 0.001f
    }
}

@Composable
internal fun rememberSplitPaneState(): SplitPaneState {
    val scope = rememberCoroutineScope()
    return remember(scope) { SplitPaneState(scope) }
}

/**
 * Sizes and places the node at [bounds] (dp, in the parent's coordinates) each layout pass, so a pane that moves or
 * resizes relays out without recomposing, and the node's own bounds (its hit area and semantics) are the pane's.
 * Without bounds (the window is not known yet) it fills its parent.
 */
internal fun Modifier.splitPaneBounds(bounds: () -> PaneRect?): Modifier =
    this
        .offset {
            val rect = bounds()
            if (rect == null) IntOffset.Zero else IntOffset(rect.left.dp.roundToPx(), rect.top.dp.roundToPx())
        }
        .layout { measurable, constraints ->
            val rect = bounds()
            if (rect == null) {
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
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
 * Home and the Stream in their panes (ADR 0003, Folded and unfolded), with the divider between them when split.
 *
 * Emits the panes and the divider as the parent's own children, so their z-order against the Dock holds as it does for
 * the phone's full surfaces: panes at [HOME_CONTENT_Z_INDEX], the divider just above, the Dock over both. A splitting
 * pull slides the Stream in over the first half of its travel, widening it as it goes.
 */
@Composable
internal fun SplitPanes(
    pane: SplitPaneState,
    dockPull: DockPullState,
    homeContent: @Composable () -> Unit,
    streamContent: @Composable () -> Unit,
) {
    if (pane.composesHome(dockPull)) {
        Box(
            Modifier
                .zIndex(HOME_CONTENT_Z_INDEX)
                .splitPaneBounds { pane.layoutFor(dockPull)?.home }
                .testTag(SPLIT_HOME_PANE_TEST_TAG),
        ) { homeContent() }
    }
    if (pane.composesStream(dockPull)) {
        Box(
            Modifier
                .zIndex(HOME_CONTENT_Z_INDEX)
                .graphicsLayer {
                    if (dockPull.running && dockPull.splitting) {
                        val frame =
                            SplitReveal.frame(dockPull.reveal, pane.splitShare, dockPull.opening, pane.reducedMotion)
                        translationX = dockPull.direction.unitX * frame.slideFraction * size.width
                        translationY = dockPull.direction.unitY * frame.slideFraction * size.height
                        alpha = frame.streamAlpha
                    }
                }
                .splitPaneBounds { pane.layoutFor(dockPull)?.stream }
                .testTag(SPLIT_STREAM_PANE_TEST_TAG),
        ) { streamContent() }
    }
    if (pane.showsDivider(dockPull)) {
        SplitDivider(pane, dockPull)
    }
}

/**
 * The movable divider: a 48 dp target with a thin glass handle, dragged with the platform `draggable` (ADR 0002),
 * focusable, stepped by the arrow keys along the axis, and adjustable by accessibility actions (Home only, Split,
 * Stream only) and by a progress value. Released, it settles to the nearest of the three states.
 */
@Composable
private fun SplitDivider(
    pane: SplitPaneState,
    dockPull: DockPullState,
) {
    val density = LocalDensity.current.density
    val label = stringResource(R.string.split_divider_label)
    val percent = (pane.split.fraction * PERCENT).roundToInt()
    val splitState = stringResource(R.string.split_state_split, percent)
    val homeOnlyLabel = stringResource(R.string.split_action_home_only)
    val splitLabel = stringResource(R.string.split_action_split)
    val streamOnlyLabel = stringResource(R.string.split_action_stream_only)
    val orientation =
        if (pane.window?.let { SplitGeometry.orientationFor(it.hinges) } == SplitOrientation.STACKED) {
            Orientation.Vertical
        } else {
            Orientation.Horizontal
        }
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    val held = pane.dragShare != null
    val dragState = rememberDraggableState { deltaPx -> pane.dragBy(deltaPx / density) }
    Box(
        modifier =
            Modifier
                .zIndex(DIVIDER_Z_INDEX)
                .splitPaneBounds { pane.layoutFor(dockPull)?.divider ?: PaneRect.EMPTY }
                .testTag(SPLIT_DIVIDER_TEST_TAG)
                .draggable(
                    state = dragState,
                    orientation = orientation,
                    onDragStarted = { pane.beginDrag(dockPull) },
                    onDragStopped = { velocityPx -> pane.endDrag(velocityPx / density) },
                )
                .onKeyEvent { event ->
                    val towardEnd = keyTowardEnd(event.key, orientation)
                    if (event.type == KeyEventType.KeyDown && towardEnd != null) {
                        pane.nudge(dockPull, towardEnd)
                        true
                    } else {
                        false
                    }
                }
                .focusable(interactionSource = focus)
                .semantics {
                    contentDescription = label
                    stateDescription = splitState
                    progressBarRangeInfo = ProgressBarRangeInfo(pane.split.fraction, 0f..1f)
                    setProgress { value ->
                        pane.window?.let { window ->
                            pane.goToTarget(SplitSettle.settleDivider(window, value.coerceIn(0f, 1f), 0f), dockPull)
                        }
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(homeOnlyLabel) {
                                pane.goTo(SplitMode.HOME_ONLY, dockPull)
                                true
                            },
                            CustomAccessibilityAction(splitLabel) {
                                pane.goTo(SplitMode.SPLIT, dockPull)
                                true
                            },
                            CustomAccessibilityAction(streamOnlyLabel) {
                                pane.goTo(SplitMode.STREAM_ONLY, dockPull)
                                true
                            },
                        )
                },
        contentAlignment = Alignment.Center,
    ) {
        val thick = if (held || focused) RiffleSpacing.s else RiffleSpacing.xs
        val handle =
            if (orientation == Orientation.Horizontal) {
                Modifier.width(thick).height(HANDLE_LENGTH)
            } else {
                Modifier.height(thick).width(HANDLE_LENGTH)
            }
        GlassSurface(modifier = handle, weight = GlassWeight.Thin, shape = RiffleShapes.full)
    }
}

/** An arrow along the axis: toward the window's end (true), its start (false), or not a divider key (null). */
private fun keyTowardEnd(
    key: Key,
    orientation: Orientation,
): Boolean? =
    when (orientation) {
        Orientation.Horizontal ->
            when (key) {
                Key.DirectionRight -> true
                Key.DirectionLeft -> false
                else -> null
            }
        Orientation.Vertical ->
            when (key) {
                Key.DirectionDown -> true
                Key.DirectionUp -> false
                else -> null
            }
    }

private val HANDLE_LENGTH = 48.dp
private const val PERCENT = 100f

/**
 * The system-bar insets the Stream's content keeps clear of in its pane: all of them alone, and in a [split] none on
 * the edge that faces the divider, which is the window's interior and has no bar.
 */
internal fun streamPaneInsets(
    insets: PaddingValues,
    window: SplitWindow?,
    split: Boolean,
): PaddingValues {
    val left = insets.calculateLeftPadding(LayoutDirection.Ltr)
    val right = insets.calculateRightPadding(LayoutDirection.Ltr)
    val top = insets.calculateTopPadding()
    val bottom = insets.calculateBottomPadding()
    if (!split || window == null) return PaddingValues.Absolute(left, top, right, bottom)
    val atEnd = SplitGeometry.streamSideFor(window) == StreamPaneSide.END
    return when (SplitGeometry.orientationFor(window.hinges)) {
        SplitOrientation.SIDE_BY_SIDE ->
            PaddingValues.Absolute(
                left = if (atEnd) 0.dp else left,
                top = top,
                right = if (atEnd) right else 0.dp,
                bottom = bottom,
            )
        SplitOrientation.STACKED ->
            PaddingValues.Absolute(
                left = left,
                top = if (atEnd) 0.dp else top,
                right = right,
                bottom = if (atEnd) bottom else 0.dp,
            )
    }
}
