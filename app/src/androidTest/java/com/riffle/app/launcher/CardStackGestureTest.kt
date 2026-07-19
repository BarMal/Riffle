package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import org.junit.Assert.assertEquals
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
