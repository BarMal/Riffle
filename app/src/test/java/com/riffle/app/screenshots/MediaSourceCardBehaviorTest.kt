package com.riffle.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A recent player's card on Now (ADR 0003, Amendment 4): the front card is the first active session, else the most
 * recent source; its one primary action is Play when the app can be resumed and "Open <app>" when it cannot; Dismiss
 * is a button and an action; the card is one node that says "<app>, last played <time>".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class MediaSourceCardBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()

    private fun show(result: StreamSectionResult) {
        val results =
            StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) } + (StreamSection.NOW to result)
        composeRule.setContent {
            ScreenshotBackdrop {
                StreamSurface(
                    layout = StreamLayout.DEFAULT,
                    results = results,
                    env = StreamFixtures.environment(),
                    callbacks = StreamCallbacks(onAction = { content, action -> actions += content to action }),
                    contentPadding = PaddingValues(),
                    initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOW),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theFrontCardIsTheMostRecentSourceAndItsPrimaryActionIsPlayWhenTheAppCanBeResumed() {
        show(MediaCentreFixtures.onlyRecent)

        composeRule.onNodeWithText("Podcasts").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertIsDisplayed().performClick()
        assertEquals(listOf(StreamFixtures.recentPodcasts to StreamAction.Play), actions)
    }

    @Test
    fun anAppThatCannotBeResumedOffersOpenByNameAndNoPlay() {
        val group = MediaCentreFixtures.onlyRecent.groups.single().copy(items = listOf(StreamFixtures.recentRadio))
        show(MediaCentreFixtures.onlyRecent.copy(groups = listOf(group)))

        composeRule.onNodeWithText("Open Radio").assertIsDisplayed().performClick()
        assertEquals(listOf(StreamFixtures.recentRadio to StreamAction.Open), actions)
        composeRule.onAllNodesWithText("Play").assertCountEquals(0)
    }

    @Test
    fun dismissIsAButtonAndTheCardIsOneNodeWithTheActionsSpoken() {
        show(MediaCentreFixtures.onlyRecent)

        composeRule.onNodeWithText("Dismiss").assertIsDisplayed().performClick()
        assertEquals(listOf(StreamFixtures.recentPodcasts to StreamAction.Dismiss), actions)

        val card =
            SemanticsMatcher("a recent player's card") { node ->
                val said = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                said.any { it.startsWith("Podcasts, last played") }
            }
        val node = composeRule.onNode(card).fetchSemanticsNode()
        val labels = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertTrue("Play" in labels && "Dismiss" in labels)
    }
}
