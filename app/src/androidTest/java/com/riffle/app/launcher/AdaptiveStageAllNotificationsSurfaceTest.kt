package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
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
 * [AdaptiveStageAppStageSurface] tree -- showing it, and the merged content it draws -- as opposed
 * to [AdaptiveStageAllNotificationsPageTest]'s pure-logic coverage of the underlying
 * page-index/settle helpers alone (including the reverse direction, settling on a real stage from
 * the merged page, which doesn't need a real Compose tree to verify).
 *
 * Reached by setting the interaction context, which is what the dock's own entry for the page does
 * now that the rail that used to carry a tile for it is gone -- see DockDynamicSectionTest for the
 * dock half. Uses a wide window since that is where the surface has no navigation of its own.
 */
class AdaptiveStageAllNotificationsSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theMergedPageShowsEveryStagesContent() {
        val mail = testApp("mail", "Mail")
        val chat = testApp("chat", "Chat")
        setWideContent(twoStageState(mail, chat), AdaptiveStageInteractionContext(allNotificationsSelected = true))

        composeRule.onNodeWithContentDescription("Cards stage: All notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Chat message").assertIsDisplayed()
    }

    @Test
    fun aCallerHoldingTheContextCanPutTheSurfaceOnTheMergedPage() {
        // The dock's merged-page entry sets the context from outside this surface, so the surface
        // has to follow one it did not change itself.
        val mail = testApp("mail", "Mail")
        val chat = testApp("chat", "Chat")
        val state = twoStageState(mail, chat)
        var context by mutableStateOf(AdaptiveStageInteractionContext())
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
                        context = context,
                        onContextChanged = { next -> context = next },
                        onAction = {},
                    )
                }
            }
        }

        composeRule.runOnIdle { context = context.copy(allNotificationsSelected = true) }

        composeRule.onNodeWithContentDescription("Cards stage: All notifications").assertIsDisplayed()
    }

    private fun setWideContent(
        state: LauncherShellState,
        context: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(800.dp).height(800.dp).clipToBounds()) {
                    AdaptiveStageAppStageSurface(
                        state = state,
                        context = context,
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
