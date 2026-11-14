package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * What the open card looks like at one frame of its opening or closing, read from the semantics tree: the bounds the
 * node is drawn in once every ancestor's clip is applied (`boundsInRoot`) against the bounds it was laid out at
 * (`positionInRoot` and `size`, which no clip touches). They are the same only when nothing clipped the card, the
 * window's own edge included.
 */
internal data class OpenCardFrameBounds(val laidOut: Rect, val drawn: Rect, val window: Rect)

internal fun ComposeContentTestRule.openCardFrame(): OpenCardFrameBounds? {
    val nodes = onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes()
    val node = nodes.firstOrNull() ?: return null
    val laidOut = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    return OpenCardFrameBounds(laidOut, node.boundsInRoot, onRoot().fetchSemanticsNode().boundsInRoot)
}

/** The card is laid out and drawn at the same place and size, inside the window: nothing clipped it at this [frame]. */
internal fun OpenCardFrameBounds.assertWhole(frame: String) {
    assertEquals("$frame: left", laidOut.left, drawn.left, 0.75f)
    assertEquals("$frame: top", laidOut.top, drawn.top, 0.75f)
    assertEquals("$frame: right", laidOut.right, drawn.right, 0.75f)
    assertEquals("$frame: bottom", laidOut.bottom, drawn.bottom, 0.75f)
    assertTrue("$frame: the card stands inside the window: $laidOut in $window", window.contains(laidOut))
}

private fun Rect.contains(other: Rect) =
    other.left >= left - 0.75f && other.top >= top - 0.75f && other.right <= right + 0.75f &&
        other.bottom <= bottom + 0.75f
