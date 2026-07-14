package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
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
            composeRule.onAllNodesWithText(cardLabel(entry.cardIndex)).assertCountEquals(1)
        }
        assertTraversalIndex(cardIndex = 2, index = -2f)
    }

    @Test
    fun profilesExposeDeterministicEnterAndExitPoses() {
        assertEquals(
            CardStackMotionMode.ANIMATED,
            cardStackMotionMode(reducedMotion = false),
        )
        assertEquals(
            CardStackMotionMode.SNAP,
            cardStackMotionMode(reducedMotion = true),
        )
        assertEquals(
            CardStackTransitionPose(alpha = 0f, horizontalTravelFraction = 0f, verticalTravelFraction = 0f),
            cardStackTransitionPose(CardStackAnimationProfile.FADE, entering = true),
        )
        assertEquals(
            CardStackTransitionPose(alpha = 1f, horizontalTravelFraction = -1f, verticalTravelFraction = 0f),
            cardStackTransitionPose(CardStackAnimationProfile.SLIDE, entering = false),
        )
        assertEquals(
            CardStackTransitionPose(
                alpha = 0.65f,
                horizontalTravelFraction = 1f,
                verticalTravelFraction = 0.15f,
            ),
            cardStackTransitionPose(CardStackAnimationProfile.CARD_FLIGHT, entering = true),
        )
        assertEquals(
            CardStackTransitionPose(alpha = 0f, horizontalTravelFraction = -1f, verticalTravelFraction = 0f),
            cardStackTransitionPose(CardStackAnimationProfile.SLIDE_AND_FADE, entering = false),
        )
    }

    @Test
    fun cardFlightRenderedEnterPoseUsesDeclaredHorizontalAndVerticalTravel() {
        val entry = CardStackLayoutPolicy().entries(cardCount = 1, activeIndex = 0).single()

        assertEquals(
            CardStackRenderedPose(alpha = 0.65f, offset = 200f, verticalOffset = 45f),
            cardStackRenderedPose(
                entry = entry,
                animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
                entering = true,
                width = 200f,
                height = 300f,
            ),
        )
    }

    @Test
    fun notificationOverviewUsesCardFlightSnapMotionWhenReducedMotionIsEnabled() {
        composeRule.setContent {
            MaterialTheme {
                NotificationOverviewSurface(
                    groups = listOf(notificationGroup()),
                    categoryCounts = mapOf(NotificationCategory.MESSAGE to 2),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    presentation =
                        NotificationOverviewPresentation(
                            apps = emptyList(),
                            appIconLoader = EmptyAppIconLoader,
                            reducedMotion = true,
                        ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("View").performClick()

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    CardStackAnimationProfileKey,
                    CardStackAnimationProfile.CARD_FLIGHT,
                ) and SemanticsMatcher.expectValue(CardStackMotionModeKey, CardStackMotionMode.SNAP),
                useUnmergedTree = true,
            ).assertExists()
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

    private fun notificationGroup(): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName("com.example.messages"),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    notification("message-1", "First"),
                    notification("message-2", "Second"),
                ),
        )

    private fun notification(
        key: String,
        title: String,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName("com.example.messages"),
            category = NotificationCategory.MESSAGE,
            title = title,
            text = "Body",
            postedAtEpochMillis = 1L,
        )
}
