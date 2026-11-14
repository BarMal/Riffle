package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * An app is drawn once in the Dock, through the real [HomeDestination] over the real Stream wiring. Chat is pinned and
 * has a notification (so is Mail), Maps is live and not pinned: Maps has a live entry, Chat has only its pinned icon,
 * which stands for its live page in both modes (it is ringed when that page is open, a tap in the contextual mode lands
 * the Stream on it, and a press that starts on it opens it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPinnedLiveAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "pinned-live").apply { isDaemon = true } }
    private val actions = mutableListOf<LauncherShellAction>()
    private var shellShown by mutableStateOf(false)
    private lateinit var host: StreamHost

    private val chat = ScreenshotFixtures.chat
    private val maps = ScreenshotFixtures.maps
    private val personal = AppProfile.personal().id.value
    private val state = ScreenshotFixtures.homeState().copy(appShortcutsByApp = StreamFixtures.shortcutsOf(chat))

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun notification(
        app: InstalledApp,
        minutesAgo: Long,
    ) = LauncherNotification(
        key = LauncherNotificationKey("${app.identity.packageName.value}-1"),
        packageName = app.identity.packageName,
        profileId = app.identity.profile.id,
        category = NotificationCategory.MESSAGE,
        title = "From ${app.label}",
        text = "Something new in ${app.label}",
        postedAtEpochMillis = StreamFixtures.NOW - minutesAgo * 60_000L,
    )

    private fun pinnedId(app: InstalledApp) =
        state.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().first { it.appIdentity == app.identity }.id

    private fun show(startShown: Boolean) {
        shellShown = startShown
        host =
            realWiringStreamHost(
                executor = executor,
                activeNotifications = listOf(notification(chat, 1), notification(maps, 3)),
                installedApps = ScreenshotFixtures.installedApps,
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = shellShown,
                    onAction = { action ->
                        actions += action
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
        // Maps, which is live and not pinned, arrives from the notification source on its own thread.
        composeRule.waitUntil(timeoutMillis = 5_000) { count(dockDynamicSectionTileTestTag(maps.label)) > 0 }
        composeRule.waitForIdle()
    }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private val chatPage get() = livePage(chat)

    private fun livePage(app: InstalledApp) =
        StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "${app.identity.packageName.value}:$personal")

    private fun awaitPage(key: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == key }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val at = "${host.dock.currentKey} of ${host.dock.pages.map { it.key }}"
            throw AssertionError("wanted $key, at $at; $actions", e)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aPinnedAppWithANotificationIsDrawnOnceInTheHomeMode() {
        show(startShown = false)
        assertEquals("one pinned icon", 1, count(dockItemTestTag(pinnedId(chat))))
        assertEquals("no live entry for a pinned app", 0, count(dockDynamicSectionTileTestTag(chat.label)))
        assertEquals("an unpinned live app keeps its entry", 1, count(dockDynamicSectionTileTestTag(maps.label)))
    }

    @Test
    fun aPinnedAppWithANotificationIsDrawnOnceInTheContextualModeAndTheIconCarriesItsPage() {
        show(startShown = true)
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey != null }
        assertEquals(1, count(dockItemTestTag(pinnedId(chat))))
        assertEquals(0, count(dockDynamicSectionTileTestTag(chat.label)))

        composeRule.onNodeWithTag(dockItemTestTag(pinnedId(chat))).performClick()
        awaitPage(chatPage)
        // The pinned icon is the ringed one, and a tap in the contextual mode launched nothing.
        composeRule.onNode(isSelected() and hasAnyAncestor(hasTestTag(dockItemTestTag(pinnedId(chat))))).assertExists()
        val launched = actions.filterIsInstance<LauncherShellAction.LaunchApp>()
        assertTrue("a tap in the contextual mode launched the app: $launched", launched.isEmpty())

        // The live app that is not pinned is still its own entry and takes the ring in turn.
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(maps.label)).performClick()
        awaitPage(livePage(maps))
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(maps.label)).assertIsSelected()
    }

    @Test
    fun aPressThatStartsOnAPinnedLiveAppOpensItsLivePage() {
        show(startShown = false)
        composeRule.pressDock(
            distancePx = 150f,
            settle = false,
            node = composeRule.onNodeWithTag(dockItemTestTag(pinnedId(chat))),
        )
        composeRule.mainClock.advanceTimeBy(1_000L)
        assertTrue("the press did not open the Stream: $actions", LauncherShellAction.OpenStream in actions)
        // The Stream's own data arrives after it opens: the request waits for the live page, not the shortcuts one.
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
        awaitPage(chatPage)
    }

    @Test
    fun aPinnedAppWithANotificationAndShortcutsHasOnePageThatLeadsWithTheNotificationWhichOpensInPlace() {
        show(startShown = true)
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey != null }
        composeRule.onNodeWithTag(dockItemTestTag(pinnedId(chat))).performClick()
        awaitPage(chatPage)

        // One page for the app: no separate shortcuts page beside its live one.
        val chatKey = "${chat.identity.packageName.value}:$personal"
        assertEquals(listOf(chatPage), host.dock.pages.filter { it.groupKey == chatKey }.map { it.key })
        // The newest notification is the front card; the shortcut cards are the ones behind it.
        composeRule.onNodeWithText("From Chat").assertIsDisplayed()
        assertTrue(
            "a shortcut card follows",
            composeRule.onAllNodes(
                hasContentDescription("Recent", substring = true),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        // A tap on it opens it in place (with its reply, dismiss and snooze), it does not launch the app.
        composeRule.onNodeWithText("From Chat").performClick()
        composeRule.waitForIdle()
        assertTrue("the notification did not open in place", host.dock.openCards.state.isOpen)
        assertTrue("the tap launched the app: $actions", actions.none { it is LauncherShellAction.LaunchApp })
    }
}
