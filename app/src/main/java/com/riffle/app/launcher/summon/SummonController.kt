package com.riffle.app.launcher.summon

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonEffect
import com.riffle.core.domain.launcher.summon.SummonEvent
import com.riffle.core.domain.launcher.summon.SummonGesture
import com.riffle.core.domain.launcher.summon.SummonGestureConfig
import com.riffle.core.domain.launcher.summon.SummonPhase

/**
 * Summon's live state on one Home: the reducer's phase, whether the menu is open, and where the Dock's icons sit (for
 * telling which one is under the finger). It feeds events to [SummonGesture] and acts on the effects; the providers
 * are read at each event, so it always uses the current model, config, haptics and action handler.
 */
@Stable
internal class SummonController(
    private val haptics: () -> LauncherHaptics,
    private val onAction: () -> (LauncherShellAction) -> Unit,
    private val model: () -> SummonRingModel,
    private val config: () -> SummonGestureConfig,
) {
    var phase: SummonPhase by mutableStateOf(SummonPhase.Idle)
        private set
    var menuOpen: Boolean by mutableStateOf(false)

    /** A card kept by pushing through its slot and letting go; null when none is open. */
    var keptCard: SummonEntry? by mutableStateOf(null)
    var root: LayoutCoordinates? = null
    private val dockIcons = mutableStateMapOf<Int, Rect>()
    private var readoutBounds: Rect? = null

    fun dispatch(event: SummonEvent) {
        val step = SummonGesture.reduce(phase, event, config(), model()::roles)
        phase = step.phase
        if (phase !is SummonPhase.Open) readoutBounds = null
        step.effects.forEach(::handle)
    }

    fun nextDeadline(): Long? = SummonGesture.nextDeadline(phase, config(), model()::roles)

    fun placeDockIcon(
        index: Int,
        coordinates: LayoutCoordinates,
    ) {
        val origin = root?.takeIf { it.isAttached && coordinates.isAttached } ?: return
        dockIcons[index] = origin.localBoundingBoxOf(coordinates)
    }

    fun placeReadout(coordinates: LayoutCoordinates) {
        val origin = root?.takeIf { it.isAttached && coordinates.isAttached } ?: return
        readoutBounds = origin.localBoundingBoxOf(coordinates)
    }

    fun isOverReadout(position: Offset): Boolean = readoutBounds?.contains(position) == true

    fun dockIndexAt(position: Offset): Int? = dockIcons.entries.firstOrNull { it.value.contains(position) }?.key

    private fun handle(effect: SummonEffect) {
        summonHapticFor(effect)?.let(haptics()::perform)
        when (effect) {
            SummonEffect.MenuRequested, is SummonEffect.Tapped -> menuOpen = true
            SummonEffect.FavouritesHidden -> dockIcons.clear()
            is SummonEffect.KeepCard -> keptCard = model().entry(effect.path, effect.index)
            else -> summonActionFor(effect, model())?.let(onAction())
        }
    }
}

/**
 * Turns one pointer's press, moves and release into [SummonEvent]s. Moves are consumed only while the ring is open, so
 * a swipe that starts before it arms is still a swipe; a gesture cut short (another surface took it) cancels.
 */
internal fun Modifier.summonInput(controller: SummonController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            controller.dispatch(SummonEvent.Down(down.position.toRingPoint(), down.uptimeMillis))
            var finished = false
            try {
                while (!finished) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        controller.dispatch(SummonEvent.Up(change?.uptimeMillis ?: SystemClock.uptimeMillis()))
                        finished = true
                    } else {
                        val dockIndex = controller.dockIndexAt(change.position)
                        controller.dispatch(
                            SummonEvent.Move(
                                point = change.position.toRingPoint(),
                                atMillis = change.uptimeMillis,
                                dockIndex = dockIndex,
                                overReadout = controller.isOverReadout(change.position),
                            ),
                        )
                        if (controller.phase is SummonPhase.Open) change.consume()
                    }
                }
            } finally {
                if (!finished && controller.phase != SummonPhase.Idle) controller.dispatch(SummonEvent.Cancel)
            }
        }
    }

private fun Offset.toRingPoint(): RingPoint = RingPoint(x, y)
