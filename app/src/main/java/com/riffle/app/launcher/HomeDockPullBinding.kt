package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullFrame
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState
import com.riffle.core.domain.launcher.dockpull.dockPullReorientEdgeBandFraction
import com.riffle.core.domain.launcher.dockpull.dockPullReorientFrostStrength
import com.riffle.core.domain.launcher.dockpull.dockPullReorientTiltDegrees
import com.riffle.core.domain.launcher.dockpull.frame
import com.riffle.core.domain.launcher.dockpull.pullDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.modeSurface

/** The modes and edges a dock pull runs between, as [HomeDestination] resolved them. */
internal class HomeDockPullPlan(
    val currentMode: LauncherViewMode,
    val counterpartMode: LauncherViewMode,
    val shownMode: LauncherViewMode,
    val edges: ModeDockEdges,
)

/**
 * How [HomeDestination] wires the dock pull (#1206, #1207) into the dock and the two surfaces.
 *
 * Every per-frame value (the dock's translation and background alpha, each surface's slide or
 * crossfade) is read inside a graphics layer or draw block from [frame], so a pull redraws without
 * recomposing; composition only reads what changes at a pull's start, release and end ([dockEdge],
 * [isTransitioning]).
 */
@Suppress("LongParameterList")
internal class HomeDockPullBinding(
    /** The edge the dock is drawn on now: the shown surface's at rest, the frame's during a pull. */
    val dockEdge: DockPosition,
    /** For the destination's root: measures the window and carries the keyboard shortcut. */
    val rootModifier: Modifier,
    /** For the dock body: the pull gesture, the accessibility action and the dock's translation. */
    val dockModifier: Modifier,
    /**
     * For the Box wrapping both mode surfaces (never the dock itself): the screen-wide reorient
     * frost (extension to the "iPhone Duo" reference, follow-up to #1279) -- edge bands that grow in
     * from the two screen edges perpendicular to the pull, proportional to the same progress the
     * dock's own frost uses. See [screenReorientFrost].
     */
    val screenFrostModifier: Modifier,
    val dockBackgroundAlpha: () -> Float,
    /**
     * The freshly-committed dock's reveal hold (dock-reorient decisions, follow-up to #1278): 1 at
     * rest, snapped low then eased back to 1 once a COMMIT swaps the shown mode, read at draw time
     * like [dockBackgroundAlpha] so it animates without recomposing the dock's content.
     */
    val dockContentRevealAlpha: () -> Float,
    private val frame: State<DockPullFrame>,
    private val transitioning: State<Boolean>,
    private val pullDirection: DockPullDirection,
) {
    /** Whether a pull or its settle is running; changes only at a pull's start and end. */
    val isTransitioning: Boolean
        get() = transitioning.value

    /**
     * For a mode surface: the outgoing one moves with the dock, the incoming one follows in from the
     * side the dock came from; under reduced motion they crossfade in place. Identity at rest.
     */
    fun surfaceModifier(isOutgoing: Boolean): Modifier =
        Modifier.graphicsLayer {
            if (transitioning.value) {
                val current = frame.value
                val surfaceFrame = if (isOutgoing) current.outgoing else current.incoming
                translationX = pullDirection.unitX * surfaceFrame.offsetFraction * size.width
                translationY = pullDirection.unitY * surfaceFrame.offsetFraction * size.height
                alpha = surfaceFrame.alpha
            }
        }
}

@Composable
internal fun rememberHomeDockPullBinding(
    state: LauncherShellState,
    plan: HomeDockPullPlan,
    dockPull: DockPullState,
    onAction: (LauncherShellAction) -> Unit,
): HomeDockPullBinding {
    val rootSize = remember { mutableStateOf(IntSize.Zero) }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val currentSurface = plan.currentMode.modeSurface
    val pullDirection = plan.edges.edgeFor(currentSurface).pullDirection
    // No pull while the layout is being edited (long-press + drag moves dock items then) or a
    // widget is being placed.
    val canPull = state.homeLayout.editMode == HomeEditMode.Browsing && !state.isWidgetPickerOpen
    val edges = plan.edges
    val frameState = remember(dockPull, edges) { derivedStateOf { dockPull.transition.frame(edges) } }
    val transitioning = remember(dockPull) { derivedStateOf { dockPull.transition !is DockPullTransitionState.Idle } }
    val runningEdge =
        remember(frameState, transitioning) {
            derivedStateOf { frameState.value.dockEdge.takeIf { transitioning.value } }
        }
    val committedFrom = state.homeLayoutSet
    val counterpartMode = plan.counterpartMode
    SideEffect {
        dockPull.onCommitted = {
            dockPull.pending = DockPullPendingSwitch(mode = counterpartMode, fromSet = committedFrom)
            onAction(LauncherShellAction.SelectLauncherViewMode(counterpartMode))
        }
    }
    val host = rememberDockPullGestureHost(dockPull, canPull, currentSurface, reducedMotion, rootSize)
    val switchNow = { dockPull.switchNow(currentSurface, pullDirection, reducedMotion) }
    val actionLabel = dockPullActionLabel(currentSurface)
    val dockModifier =
        Modifier
            .testTag(HOME_DOCK_PULL_TEST_TAG)
            .then(
                if (canPull) {
                    Modifier.semantics {
                        customActions = listOf(CustomAccessibilityAction(actionLabel) { switchNow() })
                    }
                } else {
                    Modifier
                },
            )
            // Before the translation, so the finger is measured against the dock's resting place.
            .dockPullInput(pullDirection, host)
            .graphicsLayer {
                val current = frameState.value
                val direction = current.dockEdge.pullDirection
                val window = rootSize.value
                translationX = direction.unitX * current.dockOffsetFraction * window.width
                translationY = direction.unitY * current.dockOffsetFraction * window.height
                // Perspective tilt (dock-reorient decisions): a fold-around-a-corner read rather than
                // a flat translate, proportional to the same frost strength and easing back to flat as
                // it clears. Skipped entirely under reduced motion.
                if (!reducedMotion) {
                    val tiltDegrees = dockPullReorientTiltDegrees(current.dockBackgroundAlpha)
                    cameraDistance = DOCK_REORIENT_CAMERA_DISTANCE * density
                    rotationX = direction.unitY * tiltDegrees
                    rotationY = -direction.unitX * tiltDegrees
                }
            }
            .dockReorientFrost(
                isActive = transitioning.value && !reducedMotion,
                strengthProvider = { dockPullReorientFrostStrength(frameState.value.dockBackgroundAlpha) },
            )
    val screenFrostModifier = screenFrostModifier(transitioning.value && !reducedMotion, pullDirection, frameState)
    val rootModifier =
        Modifier
            .onSizeChanged { size -> rootSize.value = size }
            .onPreviewKeyEvent { event ->
                val isShortcut =
                    event.type == KeyEventType.KeyDown &&
                        event.isCtrlPressed &&
                        event.key == pullDirection.shortcutKey
                canPull && isShortcut && switchNow()
            }
    val dockBackgroundAlpha = remember(frameState) { { frameState.value.dockBackgroundAlpha } }
    val dockContentRevealAlpha = remember(dockPull) { { dockPull.revealAlpha } }
    return HomeDockPullBinding(
        dockEdge = runningEdge.value ?: edges.edgeFor(plan.shownMode.modeSurface),
        rootModifier = rootModifier,
        dockModifier = dockModifier,
        screenFrostModifier = screenFrostModifier,
        dockBackgroundAlpha = dockBackgroundAlpha,
        dockContentRevealAlpha = dockContentRevealAlpha,
        frame = frameState,
        transitioning = transitioning,
        pullDirection = pullDirection,
    )
}

/**
 * How far the dock's own [androidx.compose.ui.graphics.GraphicsLayerScope.cameraDistance] is pushed
 * out while it tilts (dock-reorient decisions): large enough that the small rotation reads as
 * perspective rather than a pure shear, scaled by density like the platform default.
 */
private const val DOCK_REORIENT_CAMERA_DISTANCE = 12f

/**
 * [Modifier.screenReorientFrost] for the two mode surfaces, given the same progress and direction
 * [dockModifier]'s own [Modifier.dockReorientFrost] already reads -- kept a separate small function
 * rather than inlined into [rememberHomeDockPullBinding] to keep that composable under the repo's
 * length limit; not itself `@Composable`, exactly like [Modifier.screenReorientFrost].
 */
private fun screenFrostModifier(
    isActive: Boolean,
    pullDirection: DockPullDirection,
    frame: State<DockPullFrame>,
): Modifier =
    Modifier.screenReorientFrost(
        isActive = isActive,
        isVerticalPull = pullDirection.unitY != 0f,
        strengthProvider = { dockPullReorientFrostStrength(frame.value.dockBackgroundAlpha) },
        edgeBandFractionProvider = { dockPullReorientEdgeBandFraction(frame.value.dockBackgroundAlpha) },
    )

@Composable
private fun rememberDockPullGestureHost(
    dockPull: DockPullState,
    canPull: Boolean,
    surface: ModeSurface,
    reducedMotion: Boolean,
    rootSize: State<IntSize>,
): DockPullGestureHost {
    val latestCanPull = rememberUpdatedState(canPull)
    val latestSurface = rememberUpdatedState(surface)
    val latestReducedMotion = rememberUpdatedState(reducedMotion)
    val latestDensity = rememberUpdatedState(LocalDensity.current.density)
    return remember(dockPull, rootSize) {
        object : DockPullGestureHost {
            override val state: DockPullState = dockPull

            override val canPull: Boolean
                get() = latestCanPull.value && dockPull.pending == null && rootSize.value != IntSize.Zero

            override val surface: ModeSurface
                get() = latestSurface.value

            override val reducedMotion: Boolean
                get() = latestReducedMotion.value

            override fun travelDp(direction: DockPullDirection): Float {
                val window = rootSize.value
                val extentPx = if (direction.unitY != 0f) window.height else window.width
                return extentPx / latestDensity.value
            }
        }
    }
}
