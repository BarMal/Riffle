package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
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
    fun notificationScrollReflowKeepsTheSharedNotificationAsOneStableCard() {
        val entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = 0)
        var notifications by mutableStateOf(listOf("A", "B"))

        composeRule.setContent {
            MaterialTheme {
                CardStack(
                    entries = entries,
                    animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
                    itemKey = { entry -> notifications[entry.cardIndex] },
                ) { entry ->
                    Text(notifications[entry.cardIndex])
                }
            }
        }

        composeRule.runOnIdle { notifications = listOf("B", "C") }

        composeRule.onAllNodesWithText("B").assertCountEquals(1)
        composeRule.onAllNodesWithText("C").assertCountEquals(1)
        composeRule.onNodeWithText("A").assertDoesNotExist()
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
            cardStackTransitionPose(CardStackAnimationProfile.FADE),
        )
        assertEquals(
            CardStackTransitionPose(alpha = 1f, horizontalTravelFraction = 1f, verticalTravelFraction = 0f),
            cardStackTransitionPose(CardStackAnimationProfile.SLIDE),
        )
        assertEquals(
            CardStackTransitionPose(
                alpha = 0.65f,
                horizontalTravelFraction = 1f,
                verticalTravelFraction = 0.15f,
            ),
            cardStackTransitionPose(CardStackAnimationProfile.CARD_FLIGHT),
        )
        assertEquals(
            CardStackTransitionPose(alpha = 0f, horizontalTravelFraction = 1f, verticalTravelFraction = 0f),
            cardStackTransitionPose(CardStackAnimationProfile.SLIDE_AND_FADE),
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

        composeRule.onNodeWithText("View card").performClick()

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    CardStackAnimationProfileKey,
                    CardStackAnimationProfile.CARD_FLIGHT,
                ) and SemanticsMatcher.expectValue(CardStackMotionModeKey, CardStackMotionMode.SNAP),
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    fun wideCardsPlaceFocusedStageBesideNotificationContent() {
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
                            deviceClass = HomeLayoutDeviceClass.DESKTOP,
                        ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("View card").performClick()

        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_SIDE_BY_SIDE_TEST_TAG).assertExists()
    }

    @Test
    fun notificationCardFocusControlsExposeInitialPositionAndBoundaries() {
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

        composeRule.onNodeWithText("View card").performClick()
        composeRule
            .onNodeWithTag(NOTIFICATION_PROTOTYPE_FOCUS_POSITION_TEST_TAG)
            .assertTextEquals("Focused notification 1 of 2")
        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_PREVIOUS_FOCUS_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun notificationCardFocusIsIndependentAndRetainedAcrossGroups() {
        composeRule.setContent {
            MaterialTheme {
                NotificationOverviewSurface(
                    groups =
                        listOf(
                            notificationGroup(),
                            notificationGroup(
                                packageName = "com.example.calendar",
                                notificationKeysAndTitles =
                                    listOf(
                                        "calendar-1" to "Today",
                                        "calendar-2" to "Tomorrow",
                                        "calendar-3" to "Next week",
                                    ),
                            ),
                        ),
                    categoryCounts = mapOf(NotificationCategory.MESSAGE to 5),
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

        composeRule.onAllNodesWithText("View card")[0].performClick()
        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG).performClick()
        assertNotificationFocus(position = 2, count = 2, previousEnabled = true, nextEnabled = false)

        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_PAGER_TEST_TAG).performTouchInput { swipeLeft() }
        assertNotificationFocus(position = 1, count = 3, previousEnabled = false, nextEnabled = true)

        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG).performClick()
        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG).performClick()
        assertNotificationFocus(position = 3, count = 3, previousEnabled = true, nextEnabled = false)

        composeRule.onNodeWithTag(NOTIFICATION_PROTOTYPE_PAGER_TEST_TAG).performTouchInput { swipeRight() }
        assertNotificationFocus(position = 2, count = 2, previousEnabled = true, nextEnabled = false)
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

    private fun assertNotificationFocus(
        position: Int,
        count: Int,
        previousEnabled: Boolean,
        nextEnabled: Boolean,
    ) {
        val expectedPosition = "Focused notification $position of $count"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule
                    .onNodeWithTag(NOTIFICATION_PROTOTYPE_FOCUS_POSITION_TEST_TAG)
                    .assertTextEquals(expectedPosition)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule
            .onNodeWithTag(NOTIFICATION_PROTOTYPE_FOCUS_POSITION_TEST_TAG)
            .assertTextEquals(expectedPosition)
        composeRule
            .onNodeWithTag(NOTIFICATION_PROTOTYPE_PREVIOUS_FOCUS_TEST_TAG)
            .run { if (previousEnabled) assertIsEnabled() else assertIsNotEnabled() }
        composeRule
            .onNodeWithTag(NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG)
            .run { if (nextEnabled) assertIsEnabled() else assertIsNotEnabled() }
    }

    private fun notificationGroup(
        packageName: String = "com.example.messages",
        notificationKeysAndTitles: List<Pair<String, String>> =
            listOf(
                "message-1" to "First",
                "message-2" to "Second",
            ),
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications = notificationKeysAndTitles.map { (key, title) -> notification(key, title, packageName) },
        )

    private fun notification(
        key: String,
        title: String,
        packageName: String = "com.example.messages",
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = NotificationCategory.MESSAGE,
            title = title,
            text = "Body",
            postedAtEpochMillis = 1L,
        )
}
