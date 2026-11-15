package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.toSize
import com.riffle.core.domain.launcher.dock.DockEdgeFade

/**
 * The viewport of the contextual Dock's scrolling row, for the icons in it to fade where it cuts them: where it is on
 * screen (in the root's coordinates) and which way it runs.
 */
@Stable
internal class DockRunViewport(
    val runsHorizontally: Boolean,
) {
    var bounds: Rect? by mutableStateOf(null)

    /** Whether icons fade where this viewport cuts them now (a Home-mode body does only while it is moving). */
    var enabled: () -> Boolean = { true }
}

/** The row an icon scrolls in, or null (the Home mode's static run and everywhere else): nothing fades. */
internal val LocalDockRunViewport = compositionLocalOf<DockRunViewport?> { null }

/** The semantics property of the opacity [dockEdgeFade] draws an icon at (a test hook, read with the tree). */
internal val DockEdgeFadeAlphaKey = SemanticsPropertyKey<Float>("DockEdgeFadeAlpha")

/**
 * Fades this icon by how much of it the row's viewport shows ([DockEdgeFade]): a clipped end of the contextual row (or
 * of a body that is still growing, [DockBodyFrame]) fades rather than showing half an icon at full strength. The icon's
 * place is read after layout and the opacity in the draw layer, so scrolling never recomposes it. A plain no-op
 * outside a viewport.
 */
@Composable
internal fun Modifier.dockEdgeFade(): Modifier {
    val viewport = LocalDockRunViewport.current ?: return this
    val item = remember { mutableStateOf<Rect?>(null) }

    fun alphaNow(): Float {
        val port = viewport.bounds
        val rect = item.value
        return if (port == null || rect == null || !viewport.enabled()) {
            1f
        } else if (viewport.runsHorizontally) {
            DockEdgeFade.alpha(rect.left, rect.right, port.left, port.right)
        } else {
            DockEdgeFade.alpha(rect.top, rect.bottom, port.top, port.bottom)
        }
    }
    return this
        .onGloballyPositioned { coordinates ->
            item.value = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
        }
        .graphicsLayer { alpha = alphaNow() }
        .semantics { this[DockEdgeFadeAlphaKey] = alphaNow() }
}
