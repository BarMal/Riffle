package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.DockContentAlphaKey
import com.riffle.app.launcher.DockEdgeFadeAlphaKey
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG

/** One icon of the Dock at one frame: where it stands (unclipped) and the opacity the Dock's edge fade draws it at. */
internal class DockItem(
    val tag: String,
    /** Where the icon is laid out (its position and size), whatever a clip does to what shows of it. */
    val rect: Rect,
    val alpha: Float?,
)

/** The Dock as it stands at one frame of the test clock. */
internal class DockSample(
    /** The Dock's glass body. */
    val body: Rect,
    /** The pinned icons. */
    val pins: List<DockItem>,
    /** Everything after them: live apps and the contextual tiles. */
    val entries: List<DockItem>,
    /** The Home grid's cells' bounds as drawn (a scale of Home with the trampoline is in them). */
    val cells: List<Rect>,
    /** The Home grid's cells' layout sizes (width, height): what the grid measured. */
    val cellSizes: List<Pair<Int, Int>>,
    /** The opacity the Dock's content is drawn at across a change of mode (reduced motion cross-fades it). */
    val contentAlpha: Float = 1f,
) {
    fun length(horizontal: Boolean) = if (horizontal) body.width else body.height

    fun centre(horizontal: Boolean) = if (horizontal) body.center.x else body.center.y

    fun start(horizontal: Boolean) = if (horizontal) body.left else body.top

    fun firstPin(horizontal: Boolean): DockItem? = pins.minByOrNull { if (horizontal) it.rect.left else it.rect.top }

    override fun toString() = "body=$body pins=${pins.size} entries=${entries.size}"
}

private fun SemanticsNode.tag() = config.getOrNull(SemanticsProperties.TestTag)

private fun SemanticsNode.item(tag: String): DockItem {
    val rect = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))
    return DockItem(tag, rect, config.getOrNull(DockEdgeFadeAlphaKey))
}

/** Reads the Dock now: the body, the pinned icons, the other entries and the grid's cells. */
internal fun ComposeContentTestRule.sampleDock(): DockSample {
    val bodyNode = onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode()
    val body = bodyNode.boundsInRoot

    fun items(prefix: String) =
        onAllNodes(SemanticsMatcher("a Dock item") { it.tag()?.startsWith(prefix) == true })
            .fetchSemanticsNodes()
            .map { node -> node.item(node.tag().orEmpty()) }
    val cellNodes = onAllNodes(hasTestTag(HOME_GRID_CELL_TEST_TAG)).fetchSemanticsNodes()
    val cells = cellNodes.map { it.boundsInRoot }
    val sizes = cellNodes.map { it.size.width to it.size.height }
    return DockSample(
        body,
        items("dock-item:"),
        items("dock-dynamic-entry:"),
        cells,
        sizes,
        bodyNode.config.getOrNull(DockContentAlphaKey) ?: 1f,
    )
}
