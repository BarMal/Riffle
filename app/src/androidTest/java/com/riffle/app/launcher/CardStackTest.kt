package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersEveryVisibleCardAndPutsTheFocusedCardFirstInAccessibilityTraversal() {
        val entries = CardStackLayoutPolicy().entries(cardCount = 3, activeIndex = 1)

        setContent(entries)

        entries.forEach { entry ->
            composeRule.onNodeWithText(cardLabel(entry.cardIndex)).assertExists()
        }
        assertTraversalIndex(cardIndex = 1, index = -2f)
    }

    @Test
    fun reducedMotionStackKeepsFocusedCardFirstAndRetainsAllVisibleCards() {
        val entries = CardStackLayoutPolicy().entries(cardCount = 3, activeIndex = 1, reducedMotion = true)

        setContent(entries)

        entries.forEach { entry ->
            composeRule.onNodeWithText(cardLabel(entry.cardIndex)).assertExists()
        }
        assertTraversalIndex(cardIndex = 1, index = -2f)
    }

    @Test
    fun reflowingFocusKeepsCardIdentityAndUpdatesAccessibilityOrder() {
        val initialEntries = CardStackLayoutPolicy().entries(cardCount = 3, activeIndex = 0)
        val focusedEntries = CardStackLayoutPolicy().entries(cardCount = 3, activeIndex = 2)
        var entries by mutableStateOf(initialEntries)

        composeRule.setContent {
            MaterialTheme {
                CardStack(
                    entries = entries,
                    animationProfile = CardStackAnimationProfile.STACK_REFLOW,
                ) { entry ->
                    Text(cardLabel(entry.cardIndex))
                }
            }
        }
        composeRule.runOnIdle { entries = focusedEntries }

        focusedEntries.forEach { entry ->
            composeRule.onNodeWithText(cardLabel(entry.cardIndex)).assertExists()
        }
        assertTraversalIndex(cardIndex = 2, index = -2f)
    }

    private fun setContent(entries: List<CardStackLayoutEntry>) {
        composeRule.setContent {
            MaterialTheme {
                CardStack(
                    entries = entries,
                    animationProfile = CardStackAnimationProfile.STACK_REFLOW,
                    reducedMotion = false,
                ) { entry ->
                    Text(cardLabel(entry.cardIndex))
                }
            }
        }
    }

    private fun assertTraversalIndex(
        cardIndex: Int,
        index: Float,
    ) {
        composeRule
            .onNode(
                hasAnyDescendant(hasText(cardLabel(cardIndex))) and
                    SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, index),
                useUnmergedTree = true,
            ).assertExists()
    }

    private fun cardLabel(cardIndex: Int): String = "Card $cardIndex"
}
