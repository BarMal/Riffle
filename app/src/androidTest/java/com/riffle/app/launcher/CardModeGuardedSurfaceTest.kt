package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardModeGuardedSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guardedCardsSurfaceExplainsEmptyGrantedAndRevokedStatesThenRecoversToStandard() {
        var state by mutableStateOf(cardsState(NotificationAccessStatus.GRANTED))

        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("No active notifications").assertExists()

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.REVOKED)
        }

        composeRule.onNodeWithText("Notification access was revoked").assertExists()
        composeRule.onNodeWithText("Open notification access").assertExists()

        composeRule.runOnIdle {
            state = standardState()
        }

        composeRule.onNodeWithText("Notification access was revoked").assertDoesNotExist()
        composeRule.onNodeWithText("Open notification access").assertDoesNotExist()
    }

    @Test
    fun cardsSurfaceShowsAVisibleCardThatExpandsIntoTheCardStackDetail() {
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup())),
                    appIconLoader = EmptyAppIconLoader,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Messages").assertExists()
        composeRule.onNodeWithText("View card").performClick()

        composeRule.onNodeWithText("All apps").assertExists()
    }

    private fun cardsState(
        notificationAccessStatus: NotificationAccessStatus,
        groups: List<AppNotificationGroup> = emptyList(),
    ): LauncherShellState {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        return LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
            notificationAccessStatus = notificationAccessStatus,
            notificationGroupsByApp = groups,
            notificationCountsByCategory = mapOf(NotificationCategory.MESSAGE to groups.sumOf { group -> group.count }),
        )
    }

    private fun standardState(): LauncherShellState {
        val layout = HomeLayoutDefaults.standard()
        return LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
        )
    }

    private fun notificationGroup(): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName("com.example.messages"),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.NOW,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("messages:welcome"),
                        packageName = AppPackageName("com.example.messages"),
                        category = NotificationCategory.MESSAGE,
                        title = "Welcome",
                        text = "Your first card is ready",
                        postedAtEpochMillis = 1L,
                    ),
                ),
        )
}
