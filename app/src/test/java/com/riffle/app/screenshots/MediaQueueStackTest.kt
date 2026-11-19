package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.MediaQueueItem
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One stack per player (ADR 0003, Amendment 16), folded: the player's card in front and its queue as cards behind it,
 * in queue order. A queue card says its track, its artist and its app, and Play skips the session to it. The stack's
 * own gestures (here its Next card action) browse them; a player with no queue is a single card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class MediaQueueStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()

    @Test
    fun playerStackWithQueueCardsAtRestLight() = show(MediaQueueFixtures.withQueue)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun playerStackWithQueueCardsAtRestDark() = show(MediaQueueFixtures.withQueue)

    @Test
    fun playerWithoutAQueueIsASingleCard() = show(listOf(MediaQueueFixtures.player))

    @Test
    fun queueCardInFrontLight() {
        show(MediaQueueFixtures.withQueue, capture = false)
        stackAction("Next card")()
        composeRule.onNodeWithText("Second Track").assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun theStackBrowsesFromThePlayerThroughItsQueueInOrder() {
        show(MediaQueueFixtures.withQueue, capture = false)
        composeRule.onNode(position("Card 1 of 4")).assertExists()
        stackAction("Next card")()
        composeRule.onNode(position("Card 2 of 4")).assertExists()
        composeRule.onNodeWithText("Second Track").assertIsDisplayed()
        stackAction("Next card")()
        composeRule.onNodeWithText("Third Track").assertIsDisplayed()
    }

    @Test
    fun playOnAQueueCardSkipsToItsTrackThroughTheTypedAction_neverOpen() {
        show(MediaQueueFixtures.withQueue, capture = false)
        stackAction("Next card")()
        composeRule.onNodeWithText("Play").assertIsDisplayed().performClick()
        assertEquals(
            listOf(MediaQueueFixtures.withQueue[1] to StreamAction.PlayQueueItem(102L)),
            actions,
        )
    }

    @Test
    fun aQueueCardIsOneNodeWithPlayAndItsTrackSpoken() {
        show(MediaQueueFixtures.withQueue, capture = false)
        stackAction("Next card")()
        val card =
            SemanticsMatcher("the queue card") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    .any { it == "Up next, Second Track by Example Band" }
            }
        val labels =
            composeRule.onNode(card).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue("Play" in labels)
    }

    @Test
    fun theOpenPlayerCardNoLongerHoldsAnUpNextList() {
        show(MediaQueueFixtures.withQueue, capture = false)
        composeRule.onNodeWithText("Morning Light").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Up next").assertCountEquals(0)
    }

    private fun position(text: String) =
        SemanticsMatcher("the stack position $text") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription) == text
        }

    private fun stackAction(label: String): () -> Boolean {
        val node =
            composeRule.onNode(
                SemanticsMatcher("the stack") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true &&
                        node.config.getOrNull(SemanticsProperties.TestTag) == null
                },
            ).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        return {
            var result = false
            composeRule.runOnUiThread { result = action.action() }
            composeRule.waitForIdle()
            result
        }
    }

    private fun show(
        cards: List<MediaContent>,
        capture: Boolean = true,
    ) {
        val group = StreamGroup(MediaQueueFixtures.groupKey, "Music", cards)
        val results =
            StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) } +
                (StreamSection.NOW to StreamSectionResult.Ready(listOf(group)))
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.fillMaxSize()) {
                    StreamSurface(
                        layout = StreamLayout.DEFAULT,
                        results = results,
                        env = StreamFixtures.environment(reducedMotion = true),
                        callbacks = StreamCallbacks(onAction = { content, action -> actions += content to action }),
                        contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                        initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOW, MediaQueueFixtures.groupKey),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        if (capture) composeRule.captureScreen()
    }
}

/** A player and its queue, with neutral names: an app of the screenshots' fixtures playing made-up tracks. */
internal object MediaQueueFixtures {
    private val app = StreamAppRef(ScreenshotFixtures.music.identity.packageName.value, "personal")
    val groupKey = StreamGroupKeys.player(app)

    val player =
        MediaContent(
            id = StreamContentId("media:personal:player"),
            app = app,
            appLabel = "Music",
            title = "Morning Light",
            artist = "Example Band",
            artwork = ItemImageHandle("media-art:example"),
            icon = ItemImageHandle("icon"),
            isPlaying = true,
            positionMillis = 83_000L,
            durationMillis = 244_000L,
            target = ItemTarget.App(app.packageName, "personal"),
            actions = MediaAction.entries.toSet() - MediaAction.DISMISS - MediaAction.PLAY_QUEUE_ITEM,
            postedAtEpochMillis = StreamFixtures.NOW,
            privacy = StreamPrivacy.VISIBLE,
        )

    private fun queueCard(
        queueId: Long,
        position: Int,
        title: String,
        artist: String,
    ) = player.copy(
        id = StreamContentId("media:personal:player:q:$queueId"),
        title = title,
        artist = artist,
        artwork = null,
        isPlaying = null,
        positionMillis = null,
        durationMillis = null,
        actions = setOf(MediaAction.PLAY_QUEUE_ITEM),
        queueItem = MediaQueueItem(queueId, position),
    )

    val withQueue =
        listOf(
            player,
            queueCard(102L, 0, "Second Track", "Example Band"),
            queueCard(103L, 1, "Third Track", "Sample Duo"),
            queueCard(104L, 2, "Fourth Track", "Sample Duo"),
        )
}
