package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.SystemStreamFormats
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
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
 * The Stream's card stack (dock modes slice 4a; ADR 0003, Amendments 1 and 3): bottom-anchored with older cards peeking
 * above the front one as the real card clipped, following the finger, flinging with light inertia, settling on a whole
 * card, a tap on a peeking card bringing it to the front, Next and Previous card as accessibility actions, a tick when
 * the stack lands on another card, and reduced motion jumping to the nearest card. Outcomes (which card is in front),
 * not pixels; positions come from `positionInRoot` and size (a node's bounds are clipped by its parents).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamStackBehaviorTest {
    /** The front card says where it is in its stack ("Card 2 of 3") as its state description; there is no pill. */
    private fun position(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private val anyPosition =
        SemanticsMatcher("a card position") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card ") == true
        }

    @get:Rule
    val composeRule = createComposeRule()

    private val chat = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    private val dock = StreamDockLink()
    private val ticks = mutableListOf<LauncherHapticEvent>()
    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()

    private val haptics =
        object : LauncherHaptics {
            override fun longPress() = Unit

            override fun perform(event: LauncherHapticEvent) {
                ticks += event
            }
        }

    private fun note(
        id: Int,
        title: String,
    ) = NotificationContent(
        id = StreamContentId("n:chat:$id"),
        app = StreamAppRef("chat", "personal"),
        appLabel = "Chat",
        title = title,
        text = "Body of $title",
        icon = null,
        image = null,
        postedAtEpochMillis = NOW - id * MINUTE,
        category = null,
        priority = NotificationPriority.DEFAULT,
        target = ItemTarget.None,
        actions = setOf(NotificationAction.OPEN, NotificationAction.DISMISS),
        privacy = StreamPrivacy.VISIBLE,
        notificationKey = "k:chat:$id",
    )

    private val titles = listOf("First chat", "Second chat", "Third chat", "Fourth chat", "Fifth chat", "Sixth chat")

    private fun show(
        cards: Int = 6,
        reduced: Boolean = false,
    ) {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(
                            listOf(
                                StreamGroup(
                                    "chat:personal",
                                    "Chat",
                                    titles.take(cards).mapIndexed { i, title -> note(i + 1, title) },
                                ),
                            ),
                        )
                )
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherHaptics provides haptics) {
                RiffleLauncherTheme {
                    StreamSurface(
                        layout = StreamLayout.DEFAULT,
                        results = results,
                        env =
                            StreamEnvironment(
                                imageLoader = NoExpressionImageLoader,
                                appIconLoader = NoIcons,
                                installedApps = emptyList(),
                                reducedMotion = reduced,
                                formats = SystemStreamFormats(),
                                nowEpochMillis = NOW,
                            ),
                        callbacks = StreamCallbacks(onAction = { content, action -> actions += content to action }),
                        initialPageKey = chat,
                        wideLayout = false,
                        dockLink = dock,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun slot(index: Int) = composeRule.onNodeWithTag(streamStackCardTag("n:chat:${index + 1}"))

    private fun slotNode(index: Int): SemanticsNode = slot(index).fetchSemanticsNode()

    private fun topOf(index: Int): Float = slotNode(index).positionInRoot.y

    private fun frontIs(index: Int) {
        composeRule.waitForIdle()
        composeRule.onNode(position("Card ${index + 1} of ${titles.size}")).assertExists()
        composeRule.onNodeWithText(titles[index]).assertIsDisplayed()
    }

    private fun framesOf(vararg indices: Int): List<Float> = indices.map { topOf(it) }

    /** Where every composed card is, summed: it changes whenever the stack moves and not otherwise. */
    private fun whereTheStackIs(): Float =
        composeRule.onAllNodes(
            SemanticsMatcher("a card slot") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true
            },
        ).fetchSemanticsNodes().sumOf { it.positionInRoot.y.toDouble() }.toFloat()

    /**
     * A drag of [dyDp] down from low in the pager, in small steps, then still for [holdMillis] before the finger lifts.
     */
    private fun drag(
        dyDp: Float,
        holdMillis: Long = 300,
        stepMillis: Long = 16,
    ) {
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(Offset(centerX, height * START_Y))
            repeat(STEPS) { moveBy(Offset(0f, dyDp.dp.toPx() / STEPS), delayMillis = stepMillis) }
            if (holdMillis > 0) advanceEventTime(holdMillis)
            up()
        }
        composeRule.waitForIdle()
    }

    // Where the cards are ------------------------------------------------------------------------------------------

    @Test
    fun theFrontCardSitsAtTheBottomAndOlderCardsPeekAboveItInOrder() {
        show()
        frontIs(0)
        // The front card first, then each older card higher up, each a strip of its own.
        val tops = framesOf(0, 1, 2, 3)
        assertEquals("front first from the bottom, older ones above: $tops", tops.sortedDescending(), tops)
        assertTrue(tops.zipWithNext().all { (lower, higher) -> higher < lower })
        // Only the front card and three peeks are composed: the fifth and sixth are not there yet.
        slot(4).assertDoesNotExist()
        slot(5).assertDoesNotExist()
    }

    @Test
    fun aPeekingCardIsTheRealCardClippedToItsStripAndTheStripsNeverOverlap() {
        show()
        val front = slotNode(0)
        val peeks = (1..3).map { slotNode(it) }
        peeks.forEach { assertTrue("a strip is shorter than the card", it.size.height < front.size.height / 2) }
        // No card overlaps the one in front of it: each strip ends above the top of the one below it. (Bounds, which
        // follow the card's scale; all of these are in view, so no parent clips them.)
        val cards = listOf(front) + peeks
        cards.zipWithNext().forEach { (inFront, behind) ->
            assertTrue(
                "overlap: ${behind.boundsInRoot.bottom} > ${inFront.boundsInRoot.top}",
                behind.boundsInRoot.bottom <= inFront.boundsInRoot.top + 1f,
            )
        }
    }

    @Test
    fun aPeekingCardReadsItsHiddenBodyAndOffersToBringItToTheFront() {
        show()
        val peek = slotNode(1)
        val description = peek.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue(description, "Second chat" in description && "Body of Second chat" in description)
        assertTrue(peek.config.contains(SemanticsActions.OnClick))
        assertEquals("Bring to front", peek.config[SemanticsActions.OnClick].label)
    }

    // The finger -----------------------------------------------------------------------------------------------------

    @Test
    fun theStackFollowsTheFingerWhileItIsDown() {
        show()
        val before = topOf(1)
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(Offset(centerX, height * START_Y))
            repeat(STEPS) { moveBy(Offset(0f, 45.dp.toPx() / STEPS), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        // Half a card of drag: the second card has come down toward the front, and the stack has not yet settled.
        val during = topOf(1)
        assertTrue("the next card followed the finger down ($before -> $during)", during > before)
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun aSlowDragPastHalfACardSettlesOnTheNextOneAndAShorterOneGoesBack() {
        show()
        drag(dyDp = 30f)
        frontIs(0)
        drag(dyDp = 70f)
        frontIs(1)
        // Two cards' worth, slowly: card three.
        drag(dyDp = 170f)
        frontIs(3)
        // And back up, one card.
        drag(dyDp = -95f)
        frontIs(2)
    }

    @Test
    fun aSettleLandsExactlyOnACard() {
        show()
        drag(dyDp = 100f)
        frontIs(1)
        // The front card is where the first one was at rest: nothing is left half way.
        val rest = topOf(1)
        drag(dyDp = 30f)
        drag(dyDp = -30f)
        frontIs(1)
        assertEquals(rest, topOf(1), 1f)
    }

    @Test
    fun aFastFlickCarriesFurtherThanTheSameDragSlowly() {
        show()
        // 100 dp in 80 ms: 12 cards a second for 0.18 s is two more cards than the position alone.
        drag(dyDp = 100f, holdMillis = 0, stepMillis = 8)
        composeRule.waitForIdle()
        composeRule.onAllNodes(position("Card 1 of 6")).assertCountEquals(0)
        composeRule.onAllNodes(position("Card 2 of 6")).assertCountEquals(0)
        composeRule.onNode(
            SemanticsMatcher("a later card") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)
                    ?.let { it in setOf("Card 3 of 6", "Card 4 of 6", "Card 5 of 6", "Card 6 of 6") } == true
            },
        ).assertExists()
    }

    @Test
    fun aHardFlingNeverGoesPastTheLastCard() {
        show()
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.95f, durationMillis = 120)
        }
        frontIs(5)
        // Past the end the stack rubber-bands and comes back: dragging on does not leave it between cards.
        drag(dyDp = 200f)
        frontIs(5)
    }

    @Test
    fun draggingUpFromTheFirstCardRubberBandsAndComesBack() {
        show()
        drag(dyDp = -150f)
        frontIs(0)
        assertTrue("nothing happened to the haptics: ${ticks.size}", ticks.isEmpty())
    }

    @Test
    fun aFingerLandingOnACoastingStackCatchesItWhereItIs() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.95f, durationMillis = 120)
        }
        composeRule.mainClock.advanceTimeBy(48)
        val coasting = whereTheStackIs()
        composeRule.mainClock.advanceTimeBy(64)
        assertTrue("it is still coasting", whereTheStackIs() != coasting)
        // Touch it: it stops where it is, and stays there for as long as the finger rests.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { down(Offset(centerX, height * START_Y)) }
        composeRule.mainClock.advanceTimeBy(32)
        val caught = whereTheStackIs()
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals("the caught stack moved on under the finger", caught, whereTheStackIs(), 0.5f)
        // Let go: it settles on a whole card (the clock runs again).
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNode(anyPosition).assertExists()
    }

    // Tap -----------------------------------------------------------------------------------------------------------

    @Test
    fun tappingAPeekingCardBringsItToTheFrontAndTappingFrontOpensIt() {
        show()
        slot(2).performClick()
        frontIs(2)
        // The card before the front one has sunk into the Dock: it is not a strip to tap, Previous card brings it back.
        slot(1).assertDoesNotExist()
        slot(4).performClick()
        frontIs(4)
        slot(5).performClick()
        frontIs(5)
        assertTrue("the taps opened nothing", actions.isEmpty())
        // A tap on the front card of a conversation opens it in place (it no longer launches the app, which the open
        // card's own "Open" does): the card grows, the cards behind stay as strips and nothing was sent to the app.
        composeRule.onNodeWithText("Sixth chat").performClick()
        composeRule.waitForIdle()
        assertTrue("the tap launched something: $actions", actions.isEmpty())
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).assertExists()
    }

    // Accessibility -------------------------------------------------------------------------------------------------

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

    @Test
    fun nextAndPreviousCardAreActionsOfTheStackAndStopAtTheEnds() {
        show(cards = 3)
        val next = stackAction("Next card")
        val previous = stackAction("Previous card")
        assertEquals(false, previous())
        assertEquals(true, next())
        composeRule.onAllNodes(position("Card 2 of 6")).assertCountEquals(0)
        composeRule.onNode(position("Card 2 of 3")).assertExists()
        assertEquals(true, next())
        composeRule.onNode(position("Card 3 of 3")).assertExists()
        assertEquals(false, next())
        assertEquals(true, previous())
        composeRule.onNode(position("Card 2 of 3")).assertExists()
    }

    @Test
    fun theFrontCardAlsoOffersOpenAndEveryCardIsOneNodeInOrder() {
        show(cards = 3)
        val labels =
            composeRule.onNodeWithTag(streamStackCardTag("n:chat:1")).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Open card"), labels)
        // Every card is a node of its own, in order: the front first (traversal index 0), then each peek.
        val order =
            (0..2).map { slotNode(it).config.getOrNull(SemanticsProperties.TraversalIndex) }
        assertEquals(listOf(0f, 1f, 2f), order)
    }

    // Haptics -------------------------------------------------------------------------------------------------------

    @Test
    fun aLightTickWhenTheStackSettlesOnAnotherCardAndNotWhenItComesBackToTheSame() {
        show()
        drag(dyDp = 30f)
        frontIs(0)
        assertEquals(emptyList<LauncherHapticEvent>(), ticks)
        drag(dyDp = 70f)
        frontIs(1)
        assertEquals(listOf(LauncherHapticEvent.DETENT), ticks)
        slot(2).performClick()
        frontIs(2)
        assertEquals(listOf(LauncherHapticEvent.DETENT, LauncherHapticEvent.DETENT), ticks)
    }

    // Reduced motion ------------------------------------------------------------------------------------------------

    @Test
    fun underReducedMotionADragStillFollowsTheFingerAndAReleaseJumpsToTheNearestCard() {
        show(reduced = true)
        val before = topOf(1)
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(Offset(centerX, height * START_Y))
            repeat(STEPS) { moveBy(Offset(0f, 45.dp.toPx() / STEPS), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        assertTrue("the drag tracks the finger", topOf(1) > before)
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        // No coast: a flick that would carry the stack on stays with the card nearest to where the finger left it.
        drag(dyDp = 30f, holdMillis = 0, stepMillis = 8)
        composeRule.waitForIdle()
        assertTrue(topOf(1) > 0f)
    }

    @Test
    fun underReducedMotionAFlickDoesNotCoastWhereTheSameFlickDoesOtherwise() {
        show(reduced = true)
        drag(dyDp = 100f, holdMillis = 0, stepMillis = 8)
        // 100 dp is a card and a bit: the nearest card, whatever the speed was.
        frontIs(1)
    }

    @Test
    fun underReducedMotionTheTapAndTheActionsJumpAtOnce() {
        show(reduced = true)
        slot(2).performClick()
        frontIs(2)
        stackAction("Previous card")()
        frontIs(1)
    }

    private object NoIcons : AppIconLoader {
        override fun iconFor(identity: AppIdentity): ImageBitmap? = null

        override fun colorFor(identity: AppIdentity): Color? = null
    }

    private companion object {
        const val START_Y = 0.85f
        const val STEPS = 10
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
    }
}
