package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Reports the centre of this icon's visible part and its [color] to the enclosing surface's spotlight
 * -- null [color] for an icon with none, which still marks its place on the run as unlit. A no-op
 * outside a spotlit surface.
 */
@Composable
internal fun Modifier.glassSpotlightSource(
    key: Any,
    color: Color?,
): Modifier {
    return glassSpotlightSourceFor(LocalGlassSpotlight.current, key, color)
}

/** [glassSpotlightSource] for a [spotlight] the caller holds (inside a nested surface with none of its own). */
@Composable
internal fun Modifier.glassSpotlightSourceFor(
    spotlight: GlassSpotlightState?,
    key: Any,
    color: Color?,
): Modifier {
    if (spotlight == null) return this
    DisposableEffect(spotlight, key, color) {
        onDispose { spotlight.remove(key) }
    }
    return onGloballyPositioned { coordinates ->
        // Only what is actually on screen lights the edge: bounds clipped by the strip's own
        // scroll viewport, so an icon scrolled out of its section stops glowing -- rather than
        // casting its colour under the other section, which it still sits beneath, unclipped.
        val visible = coordinates.boundsInRoot()
        if (visible.width <= 0f || visible.height <= 0f) {
            spotlight.remove(key)
        } else {
            spotlight.report(key = key, centerInRoot = visible.center, color = color)
        }
    }
}
