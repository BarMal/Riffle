package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG

/**
 * Changes the Stream's page the way a screen reader does: with the pager's "Next page" or "Previous page" action. A touch
 * drag no longer changes the stack (a sideways swipe on a card only dismisses it); the Dock's tiles and these actions
 * are what do.
 */
internal fun ComposeContentTestRule.streamPage(next: Boolean) {
    val label = if (next) "Next page" else "Previous page"
    val action =
        onNodeWithTag(STREAM_PAGER_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == label }
    runOnUiThread { action.action() }
}
