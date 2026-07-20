package com.riffle.app.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardModeGuardedSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherShellRoutesCardModeToTimeScapeAndRecoversToStandard() {
        var state by mutableStateOf(cardsState(NotificationAccessStatus.GRANTED))

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("No active stages yet. New notifications will appear here.").assertExists()

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.REVOKED)
        }

        composeRule.onNodeWithText("Notification access was revoked. Restore access to update stages.").assertExists()
        composeRule.onNodeWithText("Allow access").assertExists()

        composeRule.runOnIdle {
            state = standardState()
        }

        composeRule.onNodeWithText("Notification access was revoked. Restore access to update stages.").assertDoesNotExist()
        composeRule.onNodeWithText("Allow access").assertDoesNotExist()
    }

    @Test
    fun launcherShellCardModeShowsTheFocusedTimeScapeCardStack() {
        composeRule.setContent {
            LauncherShellContent(
                state = cardsState(NotificationAccessStatus.GRANTED, groups = listOf(notificationGroup())),
                appIconLoader = EmptyAppIconLoader,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Welcome").assertExists()
    }

    @Test
    fun cardsSurfaceUsesEffectiveSystemBarInsetPolicy() {
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = false, reserveNavigationBar = false),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    fullscreenHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = false, reserveNavigationBar = true),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    hideStatusBarOnHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = true, reserveNavigationBar = false),
            cardsPanelInsetPolicy(
                cardsState(
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    hideNavigationBarOnHome = true,
                ),
            ),
        )
        assertEquals(
            HomeInsetPolicy(reserveStatusBar = true, reserveNavigationBar = true),
            cardsPanelInsetPolicy(cardsState(NotificationAccessStatus.GRANTED)),
        )
    }

    private fun cardsState(
        notificationAccessStatus: NotificationAccessStatus,
        groups: List<AppNotificationGroup> = emptyList(),
        fullscreenHome: Boolean = false,
        hideStatusBarOnHome: Boolean = false,
        hideNavigationBarOnHome: Boolean = false,
    ): LauncherShellState {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        return LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
            notificationAccessStatus = notificationAccessStatus,
            installedApps =
                groups.map { group ->
                    InstalledApp(
                        identity =
                            AppIdentity(
                                packageName = group.packageName,
                                activityName = AppActivityName(".Main"),
                                profile = AppProfile.personal(),
                            ),
                        label = "Messages",
                    )
                },
            profileContentVisibility =
                groups.associate { group ->
                    group.profileId to AppProfileContentVisibility.VISIBLE
                },
            notificationGroupsByApp = groups,
            notificationCountsByCategory = mapOf(NotificationCategory.MESSAGE to groups.sumOf { group -> group.count }),
            launcherSettings =
                LauncherSettings(
                    appearance =
                        AppearanceSettings(
                            fullscreenHome = fullscreenHome,
                            hideStatusBarOnHome = hideStatusBarOnHome,
                            hideNavigationBarOnHome = hideNavigationBarOnHome,
                        ),
                ),
        )
    }

    private fun standardState(): LauncherShellState {
        val layout = HomeLayoutDefaults.standard()
        return LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
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
