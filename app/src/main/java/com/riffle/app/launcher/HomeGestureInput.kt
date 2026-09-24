package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import com.riffle.core.domain.launcher.gestures.GestureThresholdsPx
import com.riffle.core.domain.launcher.gestures.HomeGestureArbiter
import com.riffle.core.domain.launcher.gestures.HomeGestureDisposition
import com.riffle.core.domain.launcher.gestures.OverscrollHandOffTracker
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import kotlin.math.hypot

/**
 * The home surface's own 1/2/3-finger swipe and pinch recognizer. Which region owns which gesture,
 * and how this layer arbitrates with its children, is documented in docs/product/gestures.md; the
 * decisions themselves live in [HomeGestureArbiter] and [OverscrollHandOffTracker].
 *
 * Kept as a hand-rolled pointer loop on purpose (ADR 0002): Foundation has no N-finger swipe
 * recognizer, and pinch must stay mutually exclusive with a two-finger swipe inside one decision.
 * Its threshold is resolved from dp ([GestureThresholdsPx]) for the current display.
 *
 * @param overscrollHandOff also listen for drags a child scroller left unconsumed (nested scroll),
 *   so a vertical swipe that runs past the end of a card stack still reaches this layer. Opt-in:
 *   only surfaces whose scrolling children should hand their edges back (Cards mode) enable it.
 */
internal fun Modifier.homeGestureInput(
    enabled: Boolean,
    settings: HomeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
    actionFilter: (LauncherShellAction) -> Boolean = { true },
    overscrollHandOff: Boolean = false,
): Modifier =
    if (!enabled) {
        this
    } else {
        this.composed {
            val handOffTracker = remember { OverscrollHandOffTracker() }
            val handOffModifier =
                if (overscrollHandOff) {
                    Modifier.homeOverscrollHandOff(
                        tracker = handOffTracker,
                        settings = settings,
                        onAction = onAction,
                        actionFilter = actionFilter,
                    )
                } else {
                    Modifier
                }
            handOffModifier.pointerInput(settings) {
                val thresholds =
                    GestureThresholdsPx.resolve(density = density, touchSlopPx = viewConfiguration.touchSlop)
                val interpreter = HomeSwipeGestureInterpreter(thresholdPx = thresholds.homeSwipePx)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    handOffTracker.reset()
                    val arbiter = HomeGestureArbiter()
                    var start = HomeGestureStart.from(listOf(down))
                    var handled = false

                    while (!handled) {
                        // Initial pass runs ancestor-first, before any child sees this event: a
                        // multi-finger claim consumed here cancels a card-stack or pager drag that
                        // had already started under the first finger.
                        val earlyEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (arbiter.shouldClaim(earlyEvent.changes.count { change -> change.pressed })) {
                            earlyEvent.changes.forEach { change -> change.consume() }
                        }
                        // The same event again on the Final pass, after children had their turn.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val activeChanges = event.changes.filter { change -> change.pressed }
                        // Child gesture layers, such as the home pager or a card stack, own the
                        // single- and two-finger touches they have already consumed.
                        val disposition =
                            arbiter.dispositionFor(
                                activePointerCount = activeChanges.size,
                                hasConsumedActivePointer = activeChanges.any { change -> change.isConsumed },
                            )
                        if (disposition == HomeGestureDisposition.END) {
                            return@awaitEachGesture
                        }
                        if (activeChanges.size != start.pointerCount) {
                            start = HomeGestureStart.from(activeChanges)
                        }
                        if (disposition == HomeGestureDisposition.YIELDED) {
                            continue
                        }

                        val drag = activeChanges.centroid() - start.centroid
                        val action =
                            homeSwipeActionForDrag(
                                pointerCount = activeChanges.size,
                                horizontalDragPx = drag.x,
                                verticalDragPx = drag.y,
                                scaleDelta = start.scaleDeltaFor(activeChanges),
                                settings = settings,
                                interpreter = interpreter,
                            )

                        if (action != null && actionFilter(action)) {
                            handled = true
                            activeChanges.forEach { change -> change.consume() }
                            onAction(action)
                        }
                    }
                }
            }
        }
    }

/**
 * Hands a child scroller's leftover vertical drag to the home gesture bindings as a one-finger
 * swipe -- see [OverscrollHandOffTracker]. Consumes nothing itself, so the leftover still reaches
 * any ancestor above this one.
 */
private fun Modifier.homeOverscrollHandOff(
    tracker: OverscrollHandOffTracker,
    settings: HomeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
    actionFilter: (LauncherShellAction) -> Boolean,
): Modifier =
    this.composed {
        val currentSettings by rememberUpdatedState(settings)
        val currentOnAction by rememberUpdatedState(onAction)
        val currentActionFilter by rememberUpdatedState(actionFilter)
        val density = LocalDensity.current.density
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val connection =
            remember(tracker, density, touchSlop) {
                val thresholds = GestureThresholdsPx.resolve(density = density, touchSlopPx = touchSlop)
                val interpreter = HomeSwipeGestureInterpreter(thresholdPx = thresholds.homeSwipePx)
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        val dragPx =
                            if (source == NestedScrollSource.UserInput) {
                                tracker.onPostScroll(
                                    consumedPx = consumed.y,
                                    availablePx = available.y,
                                    thresholdPx = thresholds.homeSwipePx,
                                    touchSlopPx = touchSlop,
                                )
                            } else {
                                null
                            }
                        dragPx
                            ?.let { drag ->
                                homeSwipeActionForDrag(
                                    pointerCount = 1,
                                    horizontalDragPx = 0f,
                                    verticalDragPx = drag,
                                    settings = currentSettings,
                                    interpreter = interpreter,
                                )
                            }?.takeIf { action -> currentActionFilter(action) }
                            ?.let { action -> currentOnAction(action) }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        tracker.reset()
                        return Velocity.Zero
                    }
                }
            }
        nestedScroll(connection)
    }

private data class HomeGestureStart(
    val pointerCount: Int,
    val centroid: Offset,
    val distance: Float,
) {
    fun scaleDeltaFor(changes: List<PointerInputChange>): Float {
        if (pointerCount < 2 || distance <= 0f) {
            return 0f
        }

        return (changes.averageDistanceFromCentroid() - distance) / distance
    }

    companion object {
        fun from(changes: List<PointerInputChange>): HomeGestureStart =
            HomeGestureStart(
                pointerCount = changes.size,
                centroid = changes.centroid(),
                distance = changes.averageDistanceFromCentroid(),
            )
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    val activeChanges = filter { change -> change.pressed }.takeIf { changes -> changes.isNotEmpty() } ?: this
    val x = activeChanges.sumOf { change -> change.position.x.toDouble() }.toFloat() / activeChanges.size
    val y = activeChanges.sumOf { change -> change.position.y.toDouble() }.toFloat() / activeChanges.size
    return Offset(x, y)
}

private fun List<PointerInputChange>.averageDistanceFromCentroid(): Float {
    if (size < 2) {
        return 0f
    }

    val centroid = centroid()
    return sumOf { change ->
        hypot(
            x = (change.position.x - centroid.x).toDouble(),
            y = (change.position.y - centroid.y).toDouble(),
        )
    }.toFloat() / size
}
