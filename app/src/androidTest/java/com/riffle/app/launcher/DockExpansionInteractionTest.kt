package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Rule
import org.junit.Test

/**
 * The dock's per-layout expansion settings, from the surface's side: whether the shelf can be
 * opened at all, and which affordance opens it.
 *
 * The shelf's content is the notification section, so these fixtures grant notification access and
 * supply a group -- without one there is nothing to expand into and the affordance is correctly
 * hidden, which is its own case below.
 */
class DockExpansionInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = shortcut("docked")
    private val chat =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.chat"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = "Chat",
        )

    @Test
    fun aButtonExpandedDockOpensFromItsButtonAndLeavesTheSwipeAlone() {
        setContent(dockLayout(expandAffordance = DockExpandAffordance.BUTTON))

        composeRule.onAllNodesWithContentDescription(EXPAND_LABEL).assertCountEquals(1)
        assertCollapsed()

        // The swipe belongs to the dock's own gesture action now, so it must not open the shelf.
        swipeUpOnTheDock()
        composeRule.onAllNodesWithContentDescription(EXPAND_LABEL).assertCountEquals(1)
        assertCollapsed()

        composeRule.onNodeWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription(COLLAPSE_LABEL).assertCountEquals(1)
        assertExpanded()
    }

    @Test
    fun aDockThatIsNotExpandableOffersNeitherAffordance() {
        setContent(dockLayout(isExpandable = false))

        composeRule.onAllNodesWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).assertCountEquals(0)

        swipeUpOnTheDock()

        assertCollapsed()
    }

    @Test
    fun theSwipeStillOpensADockLeftOnItsDefaults() {
        // The companion to the two above: nothing here changes for an install that never touches
        // these settings.
        setContent(dockLayout())

        composeRule.onAllNodesWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).assertCountEquals(0)

        swipeUpOnTheDock()

        assertExpanded()
    }

    @Test
    fun aDockWithNothingToExpandIntoHidesTheButtonEntirely() {
        // Expandable and set to the button, but this dock does not want notification cards, so the
        // shelf has nothing at all to show and the affordance must not advertise it. Opting the
        // section out is the genuinely-empty case: merely lacking notification access still yields
        // a permission prompt, which is content.
        setContent(dockLayout(expandAffordance = DockExpandAffordance.BUTTON, wantsNotifications = false))

        composeRule.onAllNodesWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).assertCountEquals(0)
    }

    private fun swipeUpOnTheDock() {
        composeRule.onNodeWithTag(dockItemTestTag(docked.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()
    }

    /** The shelf holds the notification section, so its card is what "expanded" looks like. */
    private fun assertExpanded() = composeRule.onAllNodesWithText(chat.label).assertCountEquals(1)

    private fun assertCollapsed() = composeRule.onAllNodesWithText(chat.label).assertCountEquals(0)

    private fun dockLayout(
        isExpandable: Boolean = true,
        expandAffordance: DockExpandAffordance = DockExpandAffordance.GESTURE,
        wantsNotifications: Boolean = true,
    ): HomeLayout =
        HomeLayoutDefaults.standard().let { standardLayout ->
            standardLayout.copy(
                dock =
                    standardLayout.dock.copy(
                        items = listOf(docked),
                        showNotificationCards = wantsNotifications,
                        isExpandable = isExpandable,
                        expandAffordance = expandAffordance,
                    ),
            )
        }

    private fun setContent(layout: HomeLayout) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = listOf(docked.installedApp(), chat),
                        interactions = StandardHomeInteractions(),
                        presentation =
                            StandardHomePresentation(
                                notificationGroupsByApp = listOf(chatNotificationGroup()),
                                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                                installedApps = listOf(docked.installedApp(), chat),
                                appShortcutsByApp = emptyMap(),
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }
    }

    private fun chatNotificationGroup(): AppNotificationGroup =
        AppNotificationGroup(
            packageName = chat.identity.packageName,
            profileId = chat.identity.profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("chat:1"),
                        packageName = chat.identity.packageName,
                        profileId = chat.identity.profile.id,
                        category = NotificationCategory.MESSAGE,
                        canDismiss = true,
                        postedAtEpochMillis = 1L,
                    ),
                ),
        )

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private fun AppShortcutItem.installedApp(): InstalledApp =
        InstalledApp(
            identity = appIdentity,
            label = label,
        )

    private companion object {
        const val EXPAND_LABEL = "Expand dock"
        const val COLLAPSE_LABEL = "Collapse dock"
    }
}
