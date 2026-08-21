package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Rule
import org.junit.Test

/**
 * The Cards dock's expanded shelf ([StandardHomeDockOnlySurface] with showExpandedNotificationShelf
 * = false): the panel stays -- the shelf is a mini-home surface -- but the notification card row is
 * dropped, because the stages already are the notifications and the row would show them twice.
 *
 * Both fixtures grant notification access and supply a group, so the notification row *would* render
 * were it not suppressed; the difference between the two tests is the flag alone. The collapsed
 * strip's dynamic chips are a separate section, passed via dynamicEntries (empty here to isolate the
 * shelf), so a chat label in the tree can only come from the expanded notification row.
 */
class CardsDockShelfContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = shortcut("docked")
    private val panelled = shortcut("clock")
    private val chat =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.chat"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "Chat",
        )

    @Test
    fun cardsExpandedShelfShowsThePanelButNotTheNotificationRow() {
        setContent(showExpandedNotificationShelf = false)

        swipeUpOnTheDock()

        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText(panelled.label).assertCountEquals(1)
        // The row that would carry the chat notification is gone; the panel is all that expanded.
        composeRule.onAllNodesWithText(chat.label).assertCountEquals(0)
    }

    @Test
    fun theDockOnlyShelfStillShowsTheNotificationRowWhenNotSuppressed() {
        setContent(showExpandedNotificationShelf = true)

        swipeUpOnTheDock()

        // Same fixture, flag flipped: the notification row renders, proving the flag is the cause.
        composeRule.onAllNodesWithText(chat.label).assertCountEquals(1)
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

    private fun setContent(showExpandedNotificationShelf: Boolean) {
        val layout = cardsDockLayout()
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    StandardHomeDockOnlySurface(
                        layout = layout,
                        installedApps = listOf(docked.installedApp(), panelled.installedApp(), chat),
                        interactions = StandardHomeInteractions(),
                        presentation =
                            StandardHomePresentation(
                                notificationGroupsByApp = listOf(chatNotificationGroup()),
                                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                                installedApps = listOf(docked.installedApp(), panelled.installedApp(), chat),
                                appShortcutsByApp = emptyMap(),
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                        // Isolate the shelf: no collapsed-strip chips, so a chat label can only be
                        // the expanded notification row.
                        dynamicEntries = emptyList(),
                        showExpandedNotificationShelf = showExpandedNotificationShelf,
                    )
                }
            }
        }
    }

    private fun cardsDockLayout(): HomeLayout =
        HomeLayoutDefaults.standard().let { standardLayout ->
            standardLayout.copy(
                dock =
                    standardLayout.dock.copy(
                        items = listOf(docked),
                        showNotificationCards = true,
                        isExpandable = true,
                        panel = panelWith(panelled),
                    ),
            )
        }

    private fun panelWith(item: AppShortcutItem): LauncherPage =
        LauncherPage(
            id = LauncherPageId("dock-panel"),
            grid = GridDimensions(columns = 4, rows = 2),
            items = listOf(item.copy(placement = GridPlacement(cell = GridCell(column = 0, row = 0)))),
        )

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
}
