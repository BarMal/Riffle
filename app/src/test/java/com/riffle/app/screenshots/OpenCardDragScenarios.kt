package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * With a card open, a drag moves to the next card in the same stack and it opens (device fixes, round 5, item 1),
 * through the real shell content with only the platform services faked, folded and unfolded (the two subclasses).
 * Four chat notifications make the stack: "First" in front, "Second" (a long text, so its body scrolls), "Third" and
 * "Fourth".
 * A drag that starts on the open card's header or on the room beside it moves the stack at once; one that starts in the
 * scrolling body scrolls it, and only what it cannot take at its edge, past a threshold, moves the stack; a fling
 * in the body never turns a card by momentum alone; Previous and Next card are accessibility actions of the open card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class OpenCardDragScenarios {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "open-card-drag").apply { isDaemon = true } }
    protected val sent = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf<LauncherShellState?>(null)
    private val probe = RecompositionProbe()

    /** The device class the Stream's page memory is kept for. */
    protected abstract val deviceClass: HomeLayoutDeviceClass

    /** The shell state with the Stream open on a page of this posture. */
    protected abstract fun streamState(reducedMotion: Boolean): LauncherShellState

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    protected val dp get() = composeRule.density.density

    private val longText = (1..90).joinToString(" ") { "Line $it of a long message that goes on and on." }

    private fun note(
        index: Int,
        title: String,
        text: String,
    ) = LauncherNotification(
        key = LauncherNotificationKey("0|chat|$index"),
        packageName = AppPackageName(chatPackage),
        canDismiss = true,
        title = title,
        text = text,
        messages = emptyList(),
        postedAtEpochMillis = StreamFixtures.NOW - index * MINUTE,
    )

    private fun host(): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications =
                listOf(
                    note(1, "First", "The first message."),
                    note(2, "Second", longText),
                    note(3, "Third", "The third message."),
                    note(4, "Fourth", "The fourth message."),
                ),
            installedApps = ScreenshotFixtures.installedApps,
            canReply = { true },
            canSnooze = { true },
        ).also { it.pages.remember(deviceClass, chatPage) }

    protected fun LauncherShellState.withAFullFirstPage(): LauncherShellState {
        val first = homeLayout.pages.first()
        val columns = first.grid.columns
        val items =
            installedApps.take(columns * first.grid.rows).mapIndexed { index, app ->
                ScreenshotFixtures.shortcut(
                    app,
                    GridPlacement(cell = GridCell(column = index % columns, row = index / columns)),
                )
            }
        val layout =
            homeLayout.copy(
                pages = homeLayout.pages.map { if (it.id == first.id) it.copy(items = items) else it },
            )
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    protected fun LauncherShellState.withMotion(reducedMotion: Boolean): LauncherShellState {
        val settings = launcherSettings
        return copy(
            launcherSettings =
                settings.copy(
                    motion =
                        settings.motion.copy(
                            reducedMotionPreference =
                                if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                        ),
                ),
        )
    }

    private fun show(reducedMotion: Boolean) {
        shell = streamState(reducedMotion)
        val stream = host()
        composeRule.setContent {
            CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                ScreenshotBackdrop {
                    LauncherShellContent(
                        state = checkNotNull(shell),
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = stream,
                        onAction = { action ->
                            sent += action
                            shell = checkNotNull(shell).answering(action)
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText("First").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    // Helpers ------------------------------------------------------------------------------------------------------

    private fun openCard() = composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG)

    private fun openCards() = composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes()

    private fun inTheOpenCard(text: String) =
        hasText(text, substring = true) and hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG))

    /** The title of the open card, or null when no card is open. */
    private fun openTitle(): String? =
        listOf("First", "Second", "Third", "Fourth").firstOrNull { title ->
            composeRule.onAllNodes(inTheOpenCard(title)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun assertOpen(title: String) {
        composeRule.waitForIdle()
        assertEquals("the open card", title, openTitle())
        assertEquals("one card is open", 1, openCards().size)
    }

    private fun assertNoneOpen() {
        composeRule.waitForIdle()
        assertEquals("no card is open", 0, openCards().size)
    }

    private fun openFirst() {
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitForIdle()
        assertOpen("First")
    }

    /** The strip of the card titled [title], behind the open card: one node that reads the whole card. */
    private fun strip(title: String) =
        composeRule.onNode(hasContentDescription(title, substring = true) and !hasAnyAncestor(hasTestTag(OPEN_TAG)))

    /**
     * A drag of [dyDp] (positive: down) from [from], in small steps, then still for [holdMillis] before the finger
     * lifts, so it carries no velocity; [fast] is the opposite: a flick that lifts at speed.
     */
    private fun SemanticsNodeInteraction.drag(
        dyDp: Float,
        from: (androidx.compose.ui.test.TouchInjectionScope) -> Offset,
        holdMillis: Long = 300,
        steps: Int = STEPS,
        stepMillis: Long = 16,
    ) = performTouchInput {
        down(from(this))
        repeat(steps) { moveBy(Offset(0f, dyDp.dp.toPx() / steps), delayMillis = stepMillis) }
        if (holdMillis > 0) advanceEventTime(holdMillis)
        up()
    }

    /** A drag that starts on the open card's pinned header (its avatar and title, at the top of the card). */
    private fun dragFromTheHeader(dyDp: Float) {
        val card = openCard().fetchSemanticsNode()
        composeRule.onRoot().drag(
            dyDp,
            { Offset(card.positionInRoot.x + card.size.width / 2f, card.positionInRoot.y + HEADER_DP * dp) },
        )
        composeRule.waitForIdle()
    }

    /** A drag that starts in the room above the open card (a strip, or the empty column). */
    private fun dragFromTheStrips(dyDp: Float) {
        val top = openCard().fetchSemanticsNode().positionInRoot.y
        composeRule.onRoot().drag(dyDp, { Offset(it.centerX, (top - 20f * dp).coerceAtLeast(2f)) })
        composeRule.waitForIdle()
    }

    /** A drag that starts in the middle of the open card's body. */
    private fun dragInTheBody(
        dyDp: Float,
        holdMillis: Long = 300,
        steps: Int = STEPS,
        stepMillis: Long = 16,
    ) {
        val card = openCard().fetchSemanticsNode()
        composeRule.onRoot().drag(
            dyDp,
            { Offset(card.positionInRoot.x + card.size.width / 2f, card.positionInRoot.y + card.size.height * 0.45f) },
            holdMillis,
            steps,
            stepMillis,
        )
        composeRule.waitForIdle()
    }

    /** How far the open card's body has scrolled, in px (0 at the top), and how far it can. */
    private fun bodyScroll(): Pair<Float, Float> {
        val node =
            composeRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                    hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)),
            ).fetchSemanticsNodes().first()
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() to range.maxValue()
    }

    /** Scrolls the body to its end with the platform's scroll action, so no drag runs past the edge on the way. */
    private fun scrollTheBodyToItsEnd() {
        val node =
            composeRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                    hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)),
            ).fetchSemanticsNodes().first()
        val (value, max) = bodyScroll()
        composeRule.runOnUiThread { node.config[SemanticsActions.ScrollBy].action?.invoke(0f, max - value) }
        composeRule.waitForIdle()
    }

    private fun invokeStepAction(label: String) {
        // The actions are on the card's own (merged) node, under the tagged glass.
        val nodes =
            composeRule.onAllNodes(hasAnyAncestor(hasTestTag(OPEN_TAG)) or hasTestTag(OPEN_TAG)).fetchSemanticsNodes()
        val action =
            nodes.firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
            } ?: error("no '$label' action on the open card")
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    // The scenarios ------------------------------------------------------------------------------------------------

    @Test
    fun aDragDownOnTheHeaderMovesToTheNextCardAndItIsOpenAndTheFirstIsNot() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        assertFalse(composeRule.onAllNodes(inTheOpenCard("First")).fetchSemanticsNodes().isNotEmpty())
        // The one before it is the card before: a drag the other way on the header goes back.
        dragFromTheHeader(-DRAG_ONE_CARD_DP)
        assertOpen("First")
    }

    @Test
    fun aDragOnTheStripsAboveTheCardMovesTheStackToo() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheStrips(DRAG_ONE_CARD_DP)
        assertOpen("Second")
    }

    @Test
    fun theNextCardOpensWithTheMotionOnToo() {
        show(reducedMotion = false)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        // The card that arrived is a whole card, in the window, undistorted by what was moving.
        composeRule.openCardFrame()!!.assertWhole("after the drag, motion on")
    }

    @Test
    fun aDragThatLetsGoBeforeHalfACardSpringsBackToTheSameCard() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(25f)
        assertOpen("First")
    }

    @Test
    fun aDragInsideALongTextScrollsItAndDoesNotChangeTheCard() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        val (before, max) = bodyScroll()
        assertTrue("the body is long enough to scroll: $max", max > 400f * dp)
        dragInTheBody(-250f)
        val (after, _) = bodyScroll()
        assertTrue("the body scrolled: $before -> $after", after > before + 100f * dp)
        assertOpen("Second")
    }

    @Test
    fun atTheTopOfTheBodyAShortOverscrollSpringsBackAndALongOneMovesToTheNextCard() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        // 40 dp past the top edge is below the threshold: nothing moves, nothing changes.
        dragInTheBody(40f)
        assertOpen("Second")
        // 200 dp past it is a deliberate drag: the next (older) card is open.
        dragInTheBody(150f)
        assertOpen("Third")
    }

    @Test
    fun atTheBottomOfTheBodyAShortOverscrollSpringsBackAndALongOneMovesToThePreviousCard() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        scrollTheBodyToItsEnd()
        val (value, max) = bodyScroll()
        assertTrue("the body is at its end: $value of $max", value >= max - 1f)
        assertOpen("Second")
        dragInTheBody(-40f)
        assertOpen("Second")
        dragInTheBody(-150f)
        assertOpen("First")
    }

    @Test
    fun aFlingInTheBodyNeverChangesTheCardByMomentumAlone() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        // A flick down at the top edge: little travel, a lot of speed. It would turn a card if momentum did.
        dragInTheBody(30f, holdMillis = 0, steps = 3, stepMillis = 4)
        assertOpen("Second")
        // And a flick up that scrolls the body, then the same at its end.
        dragInTheBody(-60f, holdMillis = 0, steps = 3, stepMillis = 4)
        assertOpen("Second")
        scrollTheBodyToItsEnd()
        dragInTheBody(-30f, holdMillis = 0, steps = 3, stepMillis = 4)
        assertOpen("Second")
    }

    @Test
    fun theOpenCardOffersPreviousAndNextCardAndTheNeighbourIsOpen() {
        show(reducedMotion = true)
        openFirst()
        invokeStepAction("Next card")
        assertOpen("Second")
        invokeStepAction("Next card")
        assertOpen("Third")
        invokeStepAction("Previous card")
        assertOpen("Second")
        invokeStepAction("Previous card")
        assertOpen("First")
    }

    @Test
    fun aTapOutsideStillClosesTheCardAfterADrag() {
        show(reducedMotion = true)
        openFirst()
        dragFromTheHeader(DRAG_ONE_CARD_DP)
        assertOpen("Second")
        // Beside the card, in the page's side margin, level with the card's middle (the room above it is its strips').
        val card = openCard().fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(Offset(8f * dp, card.boundsInRoot.center.y)) }
        composeRule.waitForIdle()
        assertNoneOpen()
    }

    @Test
    fun aTapOnAStripOpensThatCardAndTheStackFollows() {
        show(reducedMotion = true)
        openFirst()
        strip("Second").performClick()
        composeRule.waitForIdle()
        assertOpen("Second")
    }

    @Test
    fun movingFromOneCardToTheNextIsOneContinuousMotionOfWholeCards() {
        show(reducedMotion = false)
        openFirst()
        composeRule.mainClock.autoAdvance = false
        val card = openCard().fetchSemanticsNode()
        val start = Offset(card.positionInRoot.x + card.size.width / 2f, card.positionInRoot.y + 20f * dp)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(6) { moveBy(Offset(0f, 10f * dp), delayMillis = 16) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        // Part way: the card that is leaving and the one that is arriving are both drawn, each whole.
        val during = openCards()
        assertEquals("the leaving and the arriving card", 2, during.size)
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        during.forEach { node ->
            assertTrue("a card is inside the window: ${node.boundsInRoot}", node.boundsInRoot.top >= window.top - 1f)
            assertEquals(
                "a card is laid out where it is drawn",
                node.positionInRoot.y,
                node.boundsInRoot.top,
                1f,
            )
        }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("one card is open once it settles", 1, openCards().size)
    }

    @Test
    fun underReducedMotionOneCardIsDrawnAtATimeAndTheNextOneCrossFadesIn() {
        show(reducedMotion = true)
        openFirst()
        composeRule.mainClock.autoAdvance = false
        val card = openCard().fetchSemanticsNode()
        val start = Offset(card.positionInRoot.x + card.size.width / 2f, card.positionInRoot.y + HEADER_DP * dp)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(8) { moveBy(Offset(0f, 10f * dp), delayMillis = 16) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        // No growth or blend: the card nearest the front is the open card, whole, and there is only that one.
        assertEquals("one open card at a time", 1, openCards().size)
        composeRule.openCardFrame()!!.assertWhole("reduced motion, mid drag")
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("one card is open once it settles", 1, openCards().size)
        assertTrue("and it is not the first", openTitle() != "First")
    }

    @Test
    fun theDragRecomposesNothingBetweenFramesNotTheStacksCardsNorTheOpenCards() {
        show(reducedMotion = false)
        openFirst()
        composeRule.mainClock.autoAdvance = false
        val card = openCard().fetchSemanticsNode()
        val start = Offset(card.positionInRoot.x + card.size.width / 2f, card.positionInRoot.y + 20f * dp)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(3) { moveBy(Offset(0f, 6f * dp), delayMillis = 16) }
        }
        // Past the touch slop: the stack is moving (the card that arrives is composed now) and the baseline is taken.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val slots = probe.total("stack-slot:")
        val cards = probe.total("open-card:")
        // Still short of half a card in all: no card crosses the middle, none is composed or dropped.
        composeRule.onRoot().performTouchInput {
            moveBy(Offset(0f, 5f * dp), delayMillis = 16)
            moveBy(Offset(0f, 5f * dp), delayMillis = 16)
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("the stack's cards recomposed while dragging", slots, probe.total("stack-slot:"))
        assertEquals("the open cards recomposed while dragging", cards, probe.total("open-card:"))
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    protected companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
        const val STEPS = 10

        /** Inside the header of an open conversation: its padding and then the 56 dp avatar. */
        const val HEADER_DP = 40f
        const val OPEN_TAG = STREAM_OPEN_CARD_TEST_TAG

        /** A drag that moves the stack by a card (90 dp), a little over so it settles on the next. */
        const val DRAG_ONE_CARD_DP = 100f
    }
}
