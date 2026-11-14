package com.riffle.app.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.riffle.core.domain.launcher.dock.DockFan
import com.riffle.core.domain.launcher.dock.FanFrame
import com.riffle.core.domain.launcher.dockpull.DockPullDirection

/**
 * Where the cards (or the one-piece page) of the Stream fan out from and how far along they are (ADR 0003, Amendment 3,
 * Motion). The Home destination provides it while the Dock's trampoline runs; the Stream draws from it.
 */
internal interface DockFanSource {
    /** The frame of card [index] (a one-piece page is card 0), or null at rest. Read it in a draw block. */
    fun frame(index: Int): FanFrame?

    /** The direction the Dock pulls in, away from its edge: the cards rise from the Dock, so they travel against it. */
    val pull: DockPullDirection
}

/** The fan the Stream is drawn from; null (the default) draws every card in place, as in tests and previews. */
internal val LocalDockFan = staticCompositionLocalOf<DockFanSource?> { null }

/**
 * Draws this as card [index] of the fan: it rises out of the Dock, fades in and grows to full size, later the further
 * back it is. Everything is read in the graphics layer, so the fan redraws without recomposing. Identity at rest.
 */
internal fun Modifier.dockFan(
    source: DockFanSource?,
    index: Int = 0,
): Modifier {
    if (source == null) return this
    return graphicsLayer {
        val frame = source.frame(index) ?: return@graphicsLayer
        alpha = frame.alpha
        scaleX = frame.scale
        scaleY = frame.scale
        val distancePx = DockFan.riseDp(index) * density * frame.rise
        translationX = -source.pull.unitX * distancePx
        translationY = -source.pull.unitY * distancePx
    }
}
