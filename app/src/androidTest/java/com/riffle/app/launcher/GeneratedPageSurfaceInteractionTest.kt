package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratedPageSurfaceInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nextCardControlChangesTheFocusedGeneratedCard() {
        composeRule.setContent {
            MaterialTheme {
                GeneratedNotificationCardsPage(
                    groups = listOf(notificationGroup("one"), notificationGroup("two")),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    apps = emptyList(),
                    onAction = {},
                    reducedMotion = true,
                )
            }
        }

        composeRule
            .onNodeWithTag(GENERATED_NOTIFICATION_CARD_STACK_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Card 1 of 2"))

        composeRule.onNodeWithContentDescription("Show next card").performClick()

        composeRule
            .onNodeWithTag(GENERATED_NOTIFICATION_CARD_STACK_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Card 2 of 2"))
    }

    @Test
    fun tappingBackgroundCardFocusesItWithoutLaunchingItsApp() {
        val firstGroup = notificationGroup("one")
        val secondGroup = notificationGroup("two")
        val apps = listOf(installedApp("one"), installedApp("two"))
        val secondCard =
            (
                generatedNotificationCardsPageState(
                    groups = listOf(firstGroup, secondGroup),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    apps = apps,
                ) as GeneratedNotificationCardsPageState.Content
            ).cards[1]
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                GeneratedNotificationCardsPage(
                    groups = listOf(firstGroup, secondGroup),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    apps = apps,
                    onAction = actions::add,
                    reducedMotion = true,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(generatedNotificationCardContentDescription(secondCard))
            .performTouchInput { click(Offset(width - 1f, height / 2f)) }

        composeRule
            .onNodeWithTag(GENERATED_NOTIFICATION_CARD_STACK_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Card 2 of 2"))
        composeRule.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    private fun notificationGroup(suffix: String) =
        AppNotificationGroup(
            packageName = AppPackageName("com.example.$suffix"),
            profileId = AppProfileId("personal"),
            latestCategory = NotificationCategory.UNKNOWN,
            latestAgeBucket = NotificationAgeBucket.NOW,
            notifications = emptyList(),
        )

    private fun installedApp(suffix: String) =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.$suffix"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = suffix.replaceFirstChar(Char::uppercase),
        )
}
