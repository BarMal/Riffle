package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardStackGestureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun verticalDragSettlesTheFocusedCard() {
        var focusedCard by mutableIntStateOf(0)
        var settleHapticCount by mutableIntStateOf(0)
        composeRule.setContent {
            CardStack(
                entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = focusedCard),
                modifier = Modifier.fillMaxSize().testTag("stack"),
                itemKey = { entry -> entry.cardIndex },
                interaction =
                    CardStackInteraction(
                        focusedItemKey = focusedCard,
                        onFocusRequest = { entry -> focusedCard = entry.cardIndex },
                        onSettle = { drag, _ -> if (drag < -48f) focusedCard = 1 },
                        onSettleHaptic = { settleHapticCount++ },
                    ),
            ) { _, modifier ->
                Box(modifier.fillMaxSize())
            }
        }

        composeRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, focusedCard)
            assertEquals(1, settleHapticCount)
        }
    }

    @Test
    fun slightlyDiagonalVerticalDragStillSettlesTheFocusedCard() {
        // Regression: a genuinely vertical fling whose ballistic phase drifts a couple of pixels
        // sideways would previously latch to the horizontal (pass-through) axis on the first frame
        // touchSlop was crossed -- just because the perpendicular drift momentarily edged the
        // vertical drag by a hair. cardStackPointerInput's `onSettle` was never called after that,
        // but its `isDragging = false`/`onLiveDrag(null)` finalisation still fired, so the stack
        // visibly snapped back to the starting card and the fling read as unresponsive.
        var focusedCard by mutableIntStateOf(0)
        composeRule.setContent {
            CardStack(
                entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = focusedCard),
                modifier = Modifier.fillMaxSize().testTag("stack"),
                itemKey = { entry -> entry.cardIndex },
                interaction =
                    CardStackInteraction(
                        focusedItemKey = focusedCard,
                        onFocusRequest = { entry -> focusedCard = entry.cardIndex },
                        onSettle = { drag, _ -> if (drag < -48f) focusedCard = 1 },
                    ),
            ) { _, modifier ->
                Box(modifier.fillMaxSize())
            }
        }

        composeRule.onNodeWithTag("stack").performTouchInput {
            // Vertical travel dominates but there is a small perpendicular drift, mirroring a
            // real-world diagonal fling.
            swipe(
                start = Offset(centerX, centerY + 200f),
                end = Offset(centerX + 40f, centerY - 200f),
            )
        }

        composeRule.runOnIdle { assertEquals(1, focusedCard) }
    }

    @Test
    fun aQuickShortFlickCommitsThroughTheRealVelocityTracker() {
        // Regression: cardStackPointerInput's VelocityTracker only received samples from
        // inside its own while loop -- the touch-down position/timestamp, captured separately
        // by awaitFirstDown before that loop starts, was never added. Harmless for an ordinary
        // drag with plenty of later samples to establish velocity from, but a genuinely quick,
        // short flick -- few total motion events by nature -- had its velocity systematically
        // understated by omitting the gesture's own starting point/time, falling short of
        // CardStackController.settle's combined drag+velocity commit gate and snapping back to
        // the origin card. Wires the *real* CardStackController (production-matching 64px
        // distance / 500px/s velocity thresholds, the same pair AdaptiveStageAppStageSurface
        // uses for the primary card stack) through onSettle instead of this file's other tests'
        // drag-only stub, so this actually exercises the velocity path the bug lived in.
        val controller = CardStackController()
        val stackKey = CardStackKey("test-stack")
        val cardIds = listOf(LauncherCardId("a"), LauncherCardId("b"))
        var focusState = CardStackFocusState(stackKey, cardIds[0])
        var focusedCard by mutableIntStateOf(0)

        composeRule.setContent {
            CardStack(
                entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = focusedCard),
                modifier = Modifier.fillMaxSize().testTag("stack"),
                itemKey = { entry -> entry.cardIndex },
                interaction =
                    CardStackInteraction(
                        focusedItemKey = focusedCard,
                        onFocusRequest = { entry -> focusedCard = entry.cardIndex },
                        onSettle = { drag, velocity ->
                            val result =
                                controller.settle(
                                    focusState,
                                    cardIds,
                                    CardStackSettleRequest(
                                        focusedCardId = focusState.focusedCardId,
                                        verticalDragPx = drag,
                                        verticalVelocityPxPerSecond = velocity,
                                        distanceThresholdPx = 64f,
                                        flingVelocityThresholdPxPerSecond = 500f,
                                    ),
                                )
                            if (result is CardStackFocusResult.Applied) {
                                focusState = result.state
                                focusedCard = cardIds.indexOf(result.state.focusedCardId)
                            }
                        },
                    ),
            ) { _, modifier ->
                Box(modifier.fillMaxSize())
            }
        }

        composeRule.onNodeWithTag("stack").performTouchInput {
            // Short travel over a very short duration -- few total motion samples, mirroring
            // "barely touches before releasing." 24ms is roughly one to two display frames,
            // about as quick as a real gesture gets.
            swipe(
                start = Offset(centerX, centerY + 150f),
                end = Offset(centerX, centerY - 150f),
                durationMillis = 24,
            )
        }

        composeRule.runOnIdle { assertEquals(1, focusedCard) }
    }

    @Test
    fun aFlingKeepsMovingAfterReleaseAndSettlesOnAnExactCardBoundary() {
        // The continuous-scroll model (see CardStackScroll): release hands the velocity to real
        // fling physics rather than stopping the stack dead and animating to a freshly-decided
        // card. What that guarantees, and what this asserts, is that the distance finally committed
        // is the *magnetized* one -- an exact multiple of the per-card distance, with no velocity
        // left over -- and that the live position is only cleared once the stack is at rest there.
        val stack = ScrollingCardStackHarness(cardCount = 8)
        composeRule.setContent { ScrollingCardStack(stack) }

        composeRule.onNodeWithTag("stack").performTouchInput { swipeUp() }

        composeRule.runOnIdle {
            val committedDragPx = requireNotNull(stack.settleDragPx) { "a fling must settle" }
            assertEquals(0f, committedDragPx % stack.perCardPx, 0.01f)
            assertEquals(0f, stack.settleVelocity)
            assertTrue("a fling upward moves forward through the stack", stack.focusedCard > 0)
            assertNull("the live position is cleared once the stack is at rest", stack.liveScrollPx)
        }
    }

    @Test
    fun aFlingLongerThanTheStackParksOnTheBoundaryCardRatherThanPastIt() {
        // A momentum fling travels as far as its own decay carries it, so on a short stack it will
        // reach the end with velocity to spare. CardStackScroll clamps the position to the first
        // and last card, so what that has to produce is the last card exactly -- not a position
        // out in empty space that the magnetize then has to drag the stack back from.
        val stack = ScrollingCardStackHarness(cardCount = 3)
        composeRule.setContent { ScrollingCardStack(stack) }

        composeRule.onNodeWithTag("stack").performTouchInput { swipeUp() }

        composeRule.runOnIdle {
            assertEquals(2, stack.focusedCard)
            assertEquals(-stack.perCardPx * 2f, stack.settleDragPx)
            assertNull(stack.liveScrollPx)
        }
    }

    @Test
    fun horizontalDragOnBackgroundCardRemainsAvailableToItsParent() {
        var horizontalDragWasUnconsumed by mutableStateOf(false)
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    val change = event.changes.single()
                                    if (
                                        change.position.x != change.previousPosition.x &&
                                        !change.isConsumed
                                    ) {
                                        horizontalDragWasUnconsumed = true
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        },
            ) {
                CardStack(
                    entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = 0),
                    modifier = Modifier.fillMaxSize(),
                    itemKey = { entry -> entry.cardIndex },
                    interaction =
                        CardStackInteraction(
                            focusedItemKey = 0,
                            onFocusRequest = {},
                            onSettle = { _, _ -> },
                        ),
                ) { entry, modifier ->
                    Box(modifier.fillMaxSize().testTag("card-${entry.cardIndex}"))
                }
            }
        }

        composeRule.onNodeWithTag("card-1").performTouchInput {
            swipe(
                start = Offset(width / 2f, height / 2f),
                end = Offset(width - 1f, height / 2f),
            )
        }

        composeRule.runOnIdle { assertTrue(horizontalDragWasUnconsumed) }
    }
}

/**
 * What a surface owning a continuously-scrolling stack has to hold: the durable focused card, and
 * the live scroll position the stack reports so that surface can render the motion. Mirrors what
 * AdaptiveStageNotificationStack and its siblings do, in the smallest form that still exercises the
 * real loop -- position out through onLiveDrag, back in as the fractional index entries are built
 * from.
 */
private class ScrollingCardStackHarness(
    val cardCount: Int,
    val perCardPx: Float = 64f,
) {
    var focusedCard by mutableIntStateOf(0)
    var liveScrollPx by mutableStateOf<Float?>(null)
    var settleDragPx by mutableStateOf<Float?>(null)
    var settleVelocity by mutableStateOf<Float?>(null)
}

@Composable
private fun ScrollingCardStack(harness: ScrollingCardStackHarness) {
    CardStack(
        entries =
            CardStackLayoutPolicy().entries(
                cardCount = harness.cardCount,
                activeIndex =
                    cardStackLiveActiveCardIndex(
                        activeCardIndex = harness.focusedCard,
                        cardCount = harness.cardCount,
                        liveDragPx = harness.liveScrollPx,
                        distancePerCardPx = harness.perCardPx,
                    ),
            ),
        modifier = Modifier.fillMaxSize().testTag("stack"),
        itemKey = { entry -> entry.cardIndex },
        interaction =
            CardStackInteraction(
                focusedItemKey = harness.focusedCard,
                onFocusRequest = { entry -> harness.focusedCard = entry.cardIndex },
                onSettle = { drag, velocity ->
                    harness.settleDragPx = drag
                    harness.settleVelocity = velocity
                    // The committed distance is magnetized, so rounding it recovers exactly the
                    // card the scroll came to rest on -- the same resolution CardStackController
                    // .settle performs for the production surfaces.
                    harness.focusedCard =
                        (harness.focusedCard - (drag / harness.perCardPx).roundToInt())
                            .coerceIn(0, harness.cardCount - 1)
                },
                onLiveDrag = { scrollPx -> harness.liveScrollPx = scrollPx },
                scroll =
                    CardStackScroll(
                        cardCount = harness.cardCount,
                        activeCardIndex = harness.focusedCard,
                        distancePerCardPx = harness.perCardPx,
                    ),
            ),
    ) { _, modifier ->
        Box(modifier.fillMaxSize())
    }
}
