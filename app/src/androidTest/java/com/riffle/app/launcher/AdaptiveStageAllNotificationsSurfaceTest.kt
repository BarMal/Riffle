package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Rule
import org.junit.Test

/**
 * Covers the "All notifications" page (#1057) end to end through the real
 * [AdaptiveStageAppStageSurface] tree -- the rail's extra tile, selecting it, and the merged
 * content it shows -- as opposed to [AdaptiveStageAllNotificationsPageTest]'s pure-logic coverage
 * of the underlying page-index/settle helpers alone. Uses a wide (TWO_PANE) window since that's
 * where the rail (and its "All notifications" tile) render at all; see
 * [AdaptiveStageAdaptiveLayoutInteractionTest.mediumWindowRendersStageRail] for the same
 * width/posture combination.
 */
class AdaptiveStageAllNotificationsSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allNotificationsTileSelectsTheMergedPageShowingEveryStagesContent() {
        val mail = testApp("mail", "Mail")
        val chat = testApp("chat", "Chat")
        setWideContent(twoStageState(mail, chat))

        composeRule.onNodeWithContentDescription("All notifications. Open").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All notifications. Open").performClick()

        composeRule.onNodeWithContentDescription("Cards stage: All notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Chat message").assertIsDisplayed()
    }

    @Test
    fun selectingARealStageTileAfterAllNotificationsLeavesThePage() {
        val mail = testApp("mail", "Mail")
        val chat = testApp("chat", "Chat")
        setWideContent(twoStageState(mail, chat))

        composeRule.onNodeWithContentDescription("All notifications. Open").performClick()
        composeRule.onNodeWithContentDescription("Cards stage: All notifications").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Mail. Open stage").performClick()

        composeRule.onNodeWithContentDescription("Cards stage: Mail").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All notifications. Open").assertIsDisplayed()
    }

    private fun setWideContent(state: LauncherShellState) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(800.dp).height(800.dp).clipToBounds()) {
                    AdaptiveStageAppStageSurface(
                        state = state,
                        windowLayout =
                            AdaptiveStageWindowLayout(
                                widthDp = 800,
                                heightDp = 800,
                                posture = AdaptiveStagePosture.UNFOLDED,
                            ),
                        onAction = {},
                    )
                }
            }
        }
    }

    private fun testApp(
        packageSuffix: String,
        label: String,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.$packageSuffix"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )

    private fun twoStageState(
        mail: InstalledApp,
        chat: InstalledApp,
    ): LauncherShellState =
        LauncherShellState(
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            installedApps = listOf(mail, chat),
            profileContentVisibility = mapOf(mail.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            notificationGroupsByApp =
                listOf(
                    testNotificationGroup(mail, "mail", "Mail message", "Hello from Mail", postedAtEpochMillis = 10),
                    testNotificationGroup(chat, "chat", "Chat message", "Hello from Chat", postedAtEpochMillis = 20),
                ),
        )

    private fun testNotificationGroup(
        app: InstalledApp,
        keyValue: String,
        title: String,
        text: String,
        postedAtEpochMillis: Long,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey(keyValue),
                        packageName = app.identity.packageName,
                        profileId = app.identity.profile.id,
                        title = title,
                        text = text,
                        postedAtEpochMillis = postedAtEpochMillis,
                    ),
                ),
        )
}
