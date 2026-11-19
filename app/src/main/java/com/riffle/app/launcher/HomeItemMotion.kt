package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.home.LauncherItemId
import kotlinx.coroutines.launch

/**
 * Where Home's items were, so an item that lands in another cell (dropped, pushed aside, or returned to where it was
 * lifted from) settles there on the existing spring instead of jumping. Positions are centres in the root. Plain maps,
 * not state: nothing recomposes for them, and the settle is a draw-phase translation ([homeItemSettle]).
 */
internal class HomeItemMotion {
    private val centres = HashMap<LauncherItemId, Offset>()
    private val lifted = HashMap<LauncherItemId, Pair<Offset, Long>>()

    /** How far each item is from its cell now while it settles (zero at rest); for tests to read. */
    val settleOffsets = HashMap<LauncherItemId, Offset>()

    /** The furthest each item has been from its cell while settling, in px; for tests to read. */
    val settlePeaks = HashMap<LauncherItemId, Float>()

    /** The last place the item was laid out, in the root. */
    fun centreOf(id: LauncherItemId): Offset? = centres[id]

    fun laidOutAt(
        id: LauncherItemId,
        centre: Offset,
    ) {
        centres[id] = centre
    }

    /** The lifted item was let go with the finger at [pointer]: it settles from there. */
    fun letGo(
        id: LauncherItemId,
        pointer: Offset,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (pointer != Offset.Unspecified) lifted[id] = pointer to nowMillis
    }

    /** Where the item was let go, once (and only while it is recent). */
    fun takeLetGo(
        id: LauncherItemId,
        nowMillis: Long = System.currentTimeMillis(),
    ): Offset? = lifted.remove(id)?.takeIf { nowMillis - it.second <= LET_GO_VALID_MILLIS }?.first

    private companion object {
        const val LET_GO_VALID_MILLIS = 1_500L
    }
}

internal val LocalHomeItemMotion = compositionLocalOf<HomeItemMotion?> { null }

/**
 * Settles the item on the existing spring ([RiffleMotion.snappy]) when it arrives in a cell from elsewhere: where it
 * was last laid out (a pushed item, or one moved by a drop) or where the finger let it go (a drop, or a cancelled drag
 * returning to where it was lifted from, which is when [isDragSource] turns false). Further than [maxDistancePx] it
 * just lands (another page). Under reduced motion it jumps. A translation read in the draw phase: nothing recomposes.
 */
@Composable
internal fun Modifier.homeItemSettle(
    id: LauncherItemId,
    isDragSource: Boolean,
    reducedMotion: Boolean,
    maxDistancePx: Float,
): Modifier {
    val motion = LocalHomeItemMotion.current ?: return this
    return homeItemSettleWith(motion, id, isDragSource, reducedMotion, maxDistancePx)
}

@Composable
private fun Modifier.homeItemSettleWith(
    motion: HomeItemMotion,
    id: LauncherItemId,
    isDragSource: Boolean,
    reducedMotion: Boolean,
    maxDistancePx: Float,
): Modifier =
    composed {
        val translation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val scope = rememberCoroutineScope()
        val latestReduced = rememberUpdatedState(reducedMotion)
        val state = remember { SettleState() }

        fun settle(
            from: Offset,
            to: Offset,
        ) {
            val distance = (from - to).getDistance()
            if (latestReduced.value || distance < MIN_SETTLE_PX || distance > maxDistancePx) return
            scope.launch {
                translation.snapTo(from - to)
                translation.animateTo(Offset.Zero, RiffleMotion.snappy(false)) {
                    motion.settleOffsets[id] = value
                    motion.settlePeaks[id] = maxOf(motion.settlePeaks[id] ?: 0f, value.getDistance())
                }
                motion.settleOffsets[id] = Offset.Zero
            }
        }
        // Lifted, then let go where it was lifted from (a cancel, or a drop that changed nothing): back it comes.
        LaunchedEffect(isDragSource) {
            if (state.wasSource && !isDragSource) {
                val from = motion.takeLetGo(id)
                if (from != null && state.centre != Offset.Unspecified) settle(from, state.centre)
            }
            state.wasSource = isDragSource
        }
        onGloballyPositioned { coordinates ->
            val half = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            val centre = coordinates.positionInRoot() + half
            if (!state.laidOut) {
                state.laidOut = true
                // Arrived in this cell from another: from where the finger let it go, else where it was last laid out.
                val from = motion.takeLetGo(id) ?: motion.centreOf(id)
                if (from != null) settle(from, centre)
            }
            state.centre = centre
            motion.laidOutAt(id, centre)
        }.graphicsLayer {
            translationX = translation.value.x
            translationY = translation.value.y
        }
    }

private class SettleState {
    var laidOut = false
    var wasSource = false
    var centre: Offset = Offset.Unspecified
}

private const val MIN_SETTLE_PX = 2f
