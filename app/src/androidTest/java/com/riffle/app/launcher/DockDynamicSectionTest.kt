package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dock's two sections are one dock.
 *
 * What the user pinned and what has just arrived sit in the same strip, on the same edge, inside
 * the same surface -- rather than the pinned items being a dock and everything else being a rail
 * somewhere else on screen.
 */
@RunWith(AndroidJUnit4::class)
class DockDynamicSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val camera = shortcut("camera")
    private val mail = shortcut("mail")

    @Test
    fun theDynamicSectionRunsOnFromTheStaticOneOnABottomDock() {
        setContent(DockPosition.BOTTOM)

        val pinned = boundsOf(dockItemTestTag(mail.id))
        val waiting = boundsOf(dockDynamicSectionTileTestTag(CHAT_LABEL))

        assertTrue("expected $waiting right of $pinned", waiting.left > pinned.right)
        assertEquals(pinned.center.y, waiting.center.y, TOLERANCE_PX)
    }

    @Test
    fun theDynamicSectionRunsOnDownASideDock() {
        setContent(DockPosition.LEADING)

        val pinned = boundsOf(dockItemTestTag(mail.id))
        val waiting = boundsOf(dockDynamicSectionTileTestTag(CHAT_LABEL))

        assertTrue("expected $waiting below $pinned", waiting.top > pinned.bottom)
        assertEquals(pinned.center.x, waiting.center.x, TOLERANCE_PX)
    }

    @Test
    fun theDynamicSectionIsInsideTheDocksOwnSurface() {
        // The whole point of the change: not a second component beside the dock, but part of it.
        setContent(DockPosition.BOTTOM)

        val surface = boundsOf(HOME_DOCK_SURFACE_TEST_TAG)
        val waiting = boundsOf(dockDynamicSectionTileTestTag(CHAT_LABEL))

        assertTrue(
            "expected $waiting within $surface",
            waiting.left >= surface.left - TOLERANCE_PX &&
                waiting.right <= surface.right + TOLERANCE_PX &&
                waiting.top >= surface.top - TOLERANCE_PX &&
                waiting.bottom <= surface.bottom + TOLERANCE_PX,
        )
    }

    @Test
    fun aDockWithNothingWaitingIsJustItsStaticSide() {
        setContent(DockPosition.BOTTOM, entries = emptyList())

        composeRule.onNodeWithTag(dockItemTestTag(mail.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(DOCK_DYNAMIC_SECTION_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingAnEntryOpensTheAppItIsWaitingFor() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(DockPosition.BOTTOM, actions = actions)

        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(CHAT_LABEL)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.LaunchApp(chatApp.identity)), actions)
        }
    }

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun setContent(
        position: DockPosition,
        entries: List<DockNotificationCardState> = listOf(chatEntry),
        actions: MutableList<LauncherShellAction> = mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(420.dp)) {
                    Dock(
                        dock =
                            DockModel(
                                capacity = 2,
                                items = listOf(camera, mail),
                                showNotificationCards = true,
                            ),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        position = position,
                        interactions = DockInteractions(onAction = actions::add),
                        dynamicEntries = entries,
                    )
                }
            }
        }
    }

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

    private companion object {
        private const val CHAT_LABEL = "Chat"
        private const val TOLERANCE_PX = 0.5f

        private val chatApp =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.chat"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = AppProfile.personal(),
                    ),
                label = CHAT_LABEL,
            )

        private val chatEntry =
            DockNotificationCardState(
                app = chatApp,
                group =
                    AppNotificationGroup(
                        packageName = chatApp.identity.packageName,
                        profileId = chatApp.identity.profile.id,
                        latestCategory = NotificationCategory.MESSAGE,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications =
                            listOf(
                                LauncherNotification(
                                    key = LauncherNotificationKey("chat-1"),
                                    packageName = chatApp.identity.packageName,
                                    profileId = chatApp.identity.profile.id,
                                    title = "Chat",
                                    text = "One waiting",
                                    postedAtEpochMillis = 1L,
                                ),
                            ),
                    ),
            )
    }
}
