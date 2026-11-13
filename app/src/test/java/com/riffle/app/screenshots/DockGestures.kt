package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition

/** Which way pressing a Dock on [this] edge moves: toward the edge it stands on (the opposite of its pull). */
internal fun DockPosition.pressVector(): Offset =
    when (this) {
        DockPosition.BOTTOM -> Offset(0f, 1f)
        DockPosition.TOP -> Offset(0f, -1f)
        DockPosition.LEFT -> Offset(-1f, 0f)
        DockPosition.RIGHT -> Offset(1f, 0f)
    }

/**
 * Presses a Dock down (toward its edge) by [distancePx] in [steps], starting at [start] (in the node's own coordinates;
 * its centre by default), and lifts the finger when [release] is set. The Dock's gesture is on the Dock body
 * ([HOME_DOCK_PULL_TEST_TAG]) unless [node] says otherwise (a tile inside it). A test that steps the clock by hand
 * passes
 * `settle = false`.
 */
internal fun ComposeContentTestRule.pressDock(
    distancePx: Float,
    edge: DockPosition = DockPosition.BOTTOM,
    release: Boolean = true,
    steps: Int = 8,
    settle: Boolean = true,
    node: SemanticsNodeInteraction = onNodeWithTag(HOME_DOCK_PULL_TEST_TAG),
    start: TouchInjectionScope.() -> Offset = { center },
) {
    val vector = edge.pressVector()
    node.performTouchInput {
        down(start())
        repeat(steps) { moveBy(vector * (distancePx / steps), delayMillis = 16) }
        if (release) up()
    }
    if (settle) waitForIdle()
}
