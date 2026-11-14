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
}

/** The row an icon scrolls in, or null (the Home mode's static run and everywhere else): nothing fades. */
internal val LocalDockRunViewport = compositionLocalOf<DockRunViewport?> { null }

/**
 * Fades this icon by how much of it the row's viewport shows ([DockEdgeFade]): a clipped end of the contextual row
 * fades rather than showing half an icon at full strength. The icon's place is read after layout and the opacity in the
 * draw layer, so scrolling never recomposes it. A plain no-op outside the contextual row.
 */
@Composable
internal fun Modifier.dockEdgeFade(): Modifier {
    val viewport = LocalDockRunViewport.current ?: return this
    val item = remember { mutableStateOf<Rect?>(null) }
    return this
        .onGloballyPositioned { coordinates ->
            item.value = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
        }
        .graphicsLayer {
            val port = viewport.bounds
            val rect = item.value
            alpha =
                if (port == null || rect == null) {
                    1f
                } else if (viewport.runsHorizontally) {
                    DockEdgeFade.alpha(rect.left, rect.right, port.left, port.right)
                } else {
                    DockEdgeFade.alpha(rect.top, rect.bottom, port.top, port.bottom)
                }
        }
}
