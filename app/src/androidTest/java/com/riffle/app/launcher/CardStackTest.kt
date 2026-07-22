package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackAnimationSpec
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
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
    fun selectsReflowAndSettleTimingIndependently() {
        val spec = CardStackAnimationSpec(durationMillis = 310, enterDurationMillis = 180, settleDurationMillis = 470)

        assertEquals(180, cardStackAnimationDuration(spec, CardStackAnimationTiming.ENTER))
        assertEquals(310, cardStackAnimationDuration(spec, CardStackAnimationTiming.REFLOW))
        assertEquals(470, cardStackAnimationDuration(spec, CardStackAnimationTiming.SETTLE))
    }

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
                ) { entry, _ ->
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
    @OptIn(ExperimentalTestApi::class)
    fun focusedCardAcceptsDpadNavigationAndHidesDecorativeCardsFromAccessibility() {
        var focusedCard by mutableStateOf(0)
        var boundaryKeyEvents by mutableStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                CardStack(
                    entries = CardStackLayoutPolicy().entries(cardCount = 2, activeIndex = focusedCard),
                    modifier =
                        Modifier.onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) boundaryKeyEvents++
                            false
                        },
                    interaction =
                        CardStackInteraction(
                            focusedItemKey = focusedCard,
                            onFocusRequest = { entry -> focusedCard = entry.cardIndex },
                            onSettle = { _, _ -> },
                            onNavigate = { direction ->
                                val target =
                                    when (direction) {
                                        com.riffle.core.domain.launcher.cards.CardStackNavigationDirection.PREVIOUS ->
                                            focusedCard - 1
                                        com.riffle.core.domain.launcher.cards.CardStackNavigationDirection.NEXT ->
                                            focusedCard + 1
                                    }
                                if (target !in 0..1) {
                                    false
                                } else {
                                    focusedCard = target
                                    true
                                }
                            },
                        ),
                ) { entry, modifier ->
                    Text(
                        text = cardLabel(entry.cardIndex),
                        modifier = modifier.testTag("card-${entry.cardIndex}"),
                    )
                }
            }
        }

        composeRule.onNode(SemanticsMatcher.expectValue(CardStackItemKey, 0)).requestFocus()
        composeRule.onRoot().performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        composeRule.runOnIdle {
            assertEquals(0, focusedCard)
            assertEquals(1, boundaryKeyEvents)
        }
        composeRule.onRoot().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }

        composeRule.runOnIdle { assertEquals(1, focusedCard) }
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(CardStackItemKey, 1) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
            ).assertExists()
        composeRule.onRoot().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.runOnIdle {
            assertEquals(1, focusedCard)
            assertEquals(2, boundaryKeyEvents)
        }
        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.InvisibleToUser, Unit),
                useUnmergedTree = true,
            ).assertCountEquals(1)
        composeRule.onNodeWithText(cardLabel(1)).assertExists()
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
                ) { entry, _ ->
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
        val group = notificationGroup()
        composeRule.setContent {
            MaterialTheme {
                NotificationOverviewSurface(
                    groups = listOf(group),
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
            .onNodeWithTag(focusPositionTestTag(group.key))
            .assertTextEquals("Focused notification 1 of 2")
        composeRule
            .onNodeWithTag(previousFocusTestTag(group.key))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(nextFocusTestTag(group.key)).assertIsEnabled()
    }

    @Test
    fun notificationFocusControlsUpdateAndRetainFocusForEachGroup() {
        val messagesGroup = notificationGroup()
        val calendarGroup =
            notificationGroup(
                packageName = "com.example.calendar",
                notificationKeysAndTitles =
                    listOf(
                        "calendar-1" to "Today",
                        "calendar-2" to "Tomorrow",
                        "calendar-3" to "Next week",
                    ),
            )
        var displayedGroup by mutableStateOf(messagesGroup)
        val focusedNotificationKeys = mutableStateMapOf<AppNotificationGroupKey, LauncherNotificationKey>()

        composeRule.setContent {
            MaterialTheme {
                NotificationGroupPrototype(
                    groups = listOf(displayedGroup),
                    selectedGroupKey = displayedGroup.key,
                    presentation =
                        NotificationOverviewPresentation(
                            apps = emptyList(),
                            appIconLoader = EmptyAppIconLoader,
                            reducedMotion = true,
                        ),
                    focusedNotificationKeys = focusedNotificationKeys,
                    onFocusChanged = { groupKey, notificationKey ->
                        focusedNotificationKeys[groupKey] = notificationKey
                    },
                    onBack = {},
                    onGroupChanged = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(nextFocusTestTag(messagesGroup.key)).performClick()
        assertNotificationFocusPosition(messagesGroup.key, position = 2, count = 2)
        composeRule.onNodeWithTag(previousFocusTestTag(messagesGroup.key)).performClick()
        assertNotificationFocusPosition(messagesGroup.key, position = 1, count = 2)
        composeRule.onNodeWithTag(nextFocusTestTag(messagesGroup.key)).performClick()
        assertNotificationFocusPosition(messagesGroup.key, position = 2, count = 2)

        composeRule.runOnIdle { displayedGroup = calendarGroup }
        assertNotificationFocusPosition(calendarGroup.key, position = 1, count = 3)
        composeRule.onNodeWithTag(nextFocusTestTag(calendarGroup.key)).performClick()
        assertNotificationFocusPosition(calendarGroup.key, position = 2, count = 3)
        composeRule.onNodeWithTag(previousFocusTestTag(calendarGroup.key)).performClick()
        assertNotificationFocusPosition(calendarGroup.key, position = 1, count = 3)

        composeRule.runOnIdle { displayedGroup = messagesGroup }
        assertNotificationFocusPosition(messagesGroup.key, position = 2, count = 2)
    }

    @Test
    fun notificationGroupRefreshKeepsPagerAndSelectionOnTheSameGroup() {
        val messagesGroup = notificationGroup()
        val calendarGroup =
            notificationGroup(
                packageName = "com.example.calendar",
                notificationKeysAndTitles =
                    listOf(
                        "calendar-1" to "Today",
                        "calendar-2" to "Tomorrow",
                        "calendar-3" to "Next week",
                    ),
            )
        var visibleGroups by mutableStateOf(listOf(messagesGroup, calendarGroup))
        var selectedGroupKey by mutableStateOf(messagesGroup.key)
        val focusedNotificationKeys = mutableStateMapOf<AppNotificationGroupKey, LauncherNotificationKey>()

        composeRule.setContent {
            MaterialTheme {
                NotificationGroupPrototype(
                    groups = visibleGroups,
                    selectedGroupKey = selectedGroupKey,
                    presentation =
                        NotificationOverviewPresentation(
                            apps = emptyList(),
                            appIconLoader = EmptyAppIconLoader,
                            reducedMotion = true,
                        ),
                    focusedNotificationKeys = focusedNotificationKeys,
                    onFocusChanged = { groupKey, notificationKey ->
                        focusedNotificationKeys[groupKey] = notificationKey
                    },
                    onBack = {},
                    onGroupChanged = { groupKey -> selectedGroupKey = groupKey },
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(nextFocusTestTag(messagesGroup.key)).performClick()
        assertNotificationFocusPosition(messagesGroup.key, position = 2, count = 2)

        composeRule.runOnIdle {
            visibleGroups = listOf(calendarGroup, messagesGroup)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(messagesGroup.key, selectedGroupKey)
        }
        composeRule
            .onNodeWithTag(focusPositionTestTag(messagesGroup.key))
            .assertIsDisplayed()
            .assertTextEquals("Focused notification 2 of 2")
    }

    @Test
    fun notificationRefreshRetainsFocusOnTheSameNotificationAfterReordering() {
        val initialGroup = notificationGroup()
        val refreshedGroup =
            notificationGroup(
                notificationKeysAndTitles =
                    listOf(
                        "message-2" to "Second",
                        "message-1" to "First",
                    ),
            )
        var visibleGroups by mutableStateOf(listOf(initialGroup))
        val focusedNotificationKeys = mutableStateMapOf<AppNotificationGroupKey, LauncherNotificationKey>()

        composeRule.setContent {
            MaterialTheme {
                NotificationGroupPrototype(
                    groups = visibleGroups,
                    selectedGroupKey = initialGroup.key,
                    presentation =
                        NotificationOverviewPresentation(
                            apps = emptyList(),
                            appIconLoader = EmptyAppIconLoader,
                            reducedMotion = true,
                        ),
                    focusedNotificationKeys = focusedNotificationKeys,
                    onFocusChanged = { groupKey, notificationKey ->
                        focusedNotificationKeys[groupKey] = notificationKey
                    },
                    onBack = {},
                    onGroupChanged = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(nextFocusTestTag(initialGroup.key)).performClick()
        composeRule.runOnIdle {
            assertEquals(LauncherNotificationKey("message-2"), focusedNotificationKeys[initialGroup.key])
            visibleGroups = listOf(refreshedGroup)
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(focusedNotificationTitleTestTag(initialGroup.key))
            .assertTextEquals("Second")
        assertNotificationFocusPosition(initialGroup.key, position = 1, count = 2)
    }

    private fun setContent(entries: List<CardStackLayoutEntry>) {
        composeRule.setContent {
            MaterialTheme {
                CardStack(
                    entries = entries,
                    animationProfile = CardStackAnimationProfile.STACK_REFLOW,
                    reducedMotion = false,
                ) { entry, _ ->
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

    private fun focusPositionTestTag(groupKey: AppNotificationGroupKey): String =
        "$NOTIFICATION_PROTOTYPE_FOCUS_POSITION_TEST_TAG-${groupKey.profileId.value}-${groupKey.packageName.value}"

    private fun focusedNotificationTitleTestTag(groupKey: AppNotificationGroupKey): String =
        "$NOTIFICATION_PROTOTYPE_FOCUSED_TITLE_TEST_TAG-${groupKey.profileId.value}-${groupKey.packageName.value}"

    private fun previousFocusTestTag(groupKey: AppNotificationGroupKey): String =
        "$NOTIFICATION_PROTOTYPE_PREVIOUS_FOCUS_TEST_TAG-${groupKey.profileId.value}-${groupKey.packageName.value}"

    private fun nextFocusTestTag(groupKey: AppNotificationGroupKey): String =
        "$NOTIFICATION_PROTOTYPE_NEXT_FOCUS_TEST_TAG-${groupKey.profileId.value}-${groupKey.packageName.value}"

    private fun assertNotificationFocusPosition(
        groupKey: AppNotificationGroupKey,
        position: Int,
        count: Int,
    ) {
        composeRule
            .onNodeWithTag(focusPositionTestTag(groupKey))
            .assertTextEquals("Focused notification $position of $count")
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
