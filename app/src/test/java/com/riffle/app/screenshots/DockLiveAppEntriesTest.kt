package com.riffle.app.screenshots

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.apps.AppProfile
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
 * A live app on the Dock's dynamic side (ADR 0003, Amendment 3), through the real [HomeDestination] over the real
 * Stream wiring: a tap opens the app, a press that starts on its icon (the Dock pressed down, then released) opens the
 * Stream on that app's page, a press from anywhere else lands on the first page, a long-press offers the app's menu
 * with
 * "Open in Stream", and in the contextual mode its tile is ringed while its page is open and a tap switches pages. A
 * screen reader and a keyboard can do all of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockLiveAppEntriesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "live-apps").apply { isDaemon = true } }
    private val actions = mutableListOf<LauncherShellAction>()
    private var shellShown by mutableStateOf(false)
    private lateinit var host: StreamHost

    private val mail = ScreenshotFixtures.mail
    private val chat = ScreenshotFixtures.chat
    private val personal = AppProfile.personal().id.value

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun notification(
        app: com.riffle.core.domain.launcher.apps.InstalledApp,
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

    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(keyboard: Boolean = false) {
        host =
            realWiringStreamHost(
                executor = executor,
                activeNotifications = listOf(notification(chat, minutesAgo = 1), notification(mail, minutesAgo = 5)),
                installedApps = ScreenshotFixtures.installedApps,
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                val inputMode = LocalInputModeManager.current
                // A keyboard user: focus can only land on a tile once the input mode is the keyboard's.
                LaunchedEffect(keyboard) { if (keyboard) inputMode.requestInputMode(InputMode.Keyboard) }
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
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
        // The live apps arrive from the real notification source on its own thread.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(mail.label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tile(app: com.riffle.core.domain.launcher.apps.InstalledApp) =
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(app.label))

    private fun pageKey(app: com.riffle.core.domain.launcher.apps.InstalledApp) =
        StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "${app.identity.packageName.value}:$personal")

    /** Whether the page of [app] is the one in front, and so its Dock tile is the ringed one. */
    private fun assertOpen(app: com.riffle.core.domain.launcher.apps.InstalledApp) {
        composeRule.waitForIdle()
        assertEquals(pageKey(app), host.dock.currentKey)
        tile(app).assertIsSelected()
    }

    /** Presses the Dock down by a tile's own press, starting on [tileTag], and releases. */
    private fun pressFrom(
        tileTag: String,
        distancePx: Float = 150f,
    ) {
        composeRule.pressDock(distancePx, node = composeRule.onNodeWithTag(tileTag))
    }

    private fun waitForStream() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(STREAM_SURFACE_TEST_TAG).fetchSemanticsNodes().isNotEmpty() &&
                host.dock.currentKey != null
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aTapOnALiveAppOpensTheAppNotTheStream() {
        show()
        tile(mail).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.LaunchApp(mail.identity)), actions)
        assertTrue(
            "the Stream stays closed",
            composeRule.onAllNodesWithTag(STREAM_SURFACE_TEST_TAG)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun aPressThatStartsOnALiveAppsIconOpensTheStreamOnThatAppsPage() {
        show()
        pressFrom(dockDynamicSectionTileTestTag(mail.label))
        waitForStream()

        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        assertOpen(mail)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
        assertEquals("the landing was consumed", null, host.focus.pending)
        assertTrue("the press never launches the app", actions.none { it is LauncherShellAction.LaunchApp })
    }

    @Test
    fun aPressFromAnywhereElseOpensTheStreamWhereItOpensWithNothingRemembered() {
        show()
        composeRule.pressDock(distancePx = 150f, start = { Offset(width * 0.05f, height / 2f) })
        waitForStream()

        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        // No page is remembered, so it opens on its first page when it opens, which is Now; no app's page is landed on.
        assertEquals(StreamPageKeys.NOW, host.dock.currentKey)
        assertEquals(null, host.focus.pending)
        tile(chat).assertIsNotSelected()
        tile(mail).assertIsNotSelected()
    }

    @Test
    fun aPressThatDoesNotPassTheThresholdLeavesNoLandingBehind() {
        show()
        // Past touch slop (16 dp, so it is a press and not a tap) but short of the 28 dp threshold.
        pressFrom(dockDynamicSectionTileTestTag(mail.label), distancePx = 22f * composeRule.density.density)
        composeRule.waitUntil(timeoutMillis = 5_000) { host.focus.pending == null }
        assertTrue(composeRule.onAllNodesWithTag(STREAM_SURFACE_TEST_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue("nothing was asked of the shell: $actions", actions.isEmpty())
    }

    @Test
    fun inTheContextualModeTheLiveAppsAndAnExtraTilePerPageShareOneRowAndATapSwitchesPages() {
        show()
        pressFrom(dockDynamicSectionTileTestTag(mail.label))
        waitForStream()
        actions.clear()

        // The same live apps in the same order, then the tiles of the pages that are not apps.
        assertOpen(mail)
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Recents")).assertExists()
        tile(chat).assertIsNotSelected()

        // A tap switches the page; it does not launch the app.
        tile(chat).performClick()
        waitForStream()
        assertOpen(chat)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Recents")).performClick()
        composeRule.waitForIdle()
        assertEquals(StreamPageKeys.RECENTS, host.dock.currentKey)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Recents")).assertIsSelected()
        tile(chat).assertIsNotSelected()
        assertTrue("a tap in the contextual mode switches pages, it launches nothing: $actions", actions.isEmpty())
    }

    @Test
    fun aLongPressOffersOpenInStreamAndTheAppsOwnMenu() {
        show()
        tile(mail).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open in Stream").assertIsDisplayed()
        composeRule.onNodeWithText("App info").assertIsDisplayed()
        composeRule.onNodeWithText("Hide app").assertIsDisplayed()

        composeRule.onNodeWithText("App info").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppInfo(mail.identity)), actions)
    }

    @Test
    fun openInStreamFromTheMenuLandsOnThatAppsPage() {
        show()
        tile(mail).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open in Stream").performClick()
        waitForStream()

        assertOpen(mail)
    }

    @Test
    fun aScreenReaderCanOpenTheAppAndShowItInTheStreamFromOneNode() {
        show()
        val node = tile(mail).fetchSemanticsNode()
        assertEquals("Open Mail", node.config[SemanticsActions.OnClick].label)
        // ONE customActions list: a second semantics block on the node would have replaced it.
        assertEquals(listOf("Show Mail in Stream"), node.config[SemanticsActions.CustomActions].map { it.label })

        composeRule.runOnUiThread { node.config[SemanticsActions.CustomActions].single().action() }
        waitForStream()
        assertOpen(mail)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shiftEnterOnAFocusedLiveAppShowsItInTheStream() {
        show(keyboard = true)
        composeRule.waitForIdle()
        tile(chat).requestFocus()
        tile(chat).assertIsFocused()
        tile(chat).performKeyInput {
            keyDown(Key.ShiftLeft)
            keyDown(Key.Enter)
            keyUp(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        waitForStream()
        assertOpen(chat)
        assertTrue("Shift+Enter never launches the app", actions.none { it is LauncherShellAction.LaunchApp })
    }
}
