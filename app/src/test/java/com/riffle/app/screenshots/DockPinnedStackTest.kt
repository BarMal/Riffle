package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DOCK_CHOICE_LABEL_TEST_TAG
import com.riffle.app.launcher.DockChoiceLabelKey
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
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
 * Every pinned app has a stack (ADR 0003, Amendment 3, "Every icon has a stack"), through the real [HomeDestination]
 * and the real Stream wiring. The Stream has a shortcuts page for each pinned app in the Dock's order and still opens
 * on Now. A tap on a pinned icon in the contextual mode switches to its page and rings it, while in the Home mode it
 * still launches the app. A press that starts on a pinned icon from Home opens its page, and holding the finger over
 * one while choosing selects it and opens its page when lifted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPinnedStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dock-pinned").apply { isDaemon = true } }
    private lateinit var host: StreamHost
    private var shellShown by mutableStateOf(false)
    private val sent = mutableListOf<LauncherShellAction>()

    private val camera = ScreenshotFixtures.camera
    private val cameraPage = "shortcuts/${StreamShortcutPages.appKeyOf(camera.identity)}"

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val state =
        ScreenshotFixtures.homeState().let { base ->
            base.copy(appShortcutsByApp = StreamFixtures.shortcutsOf(camera))
        }

    private fun cameraItemId() =
        state.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().first { it.appIdentity == camera.identity }.id

    private fun show(startShown: Boolean) {
        shellShown = startShown
        host = realWiringStreamHost(executor = executor)
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = shellShown,
                    onAction = { action ->
                        sent += action
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
        composeRule.waitForIdle()
        if (startShown) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Today")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
        }
    }

    private fun pinnedCentre(): Offset {
        val node = composeRule.onNodeWithTag(dockItemTestTag(cameraItemId())).fetchSemanticsNode()
        return Offset(node.positionInRoot.x + node.size.width / 2f, node.positionInRoot.y + node.size.height / 2f)
    }

    private fun label(): String? =
        composeRule.onAllNodesWithTag(DOCK_CHOICE_LABEL_TEST_TAG).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(DockChoiceLabelKey)

    private fun awaitPage(key: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == key }
        composeRule.waitForIdle()
    }

    @Test
    fun theStreamHasAShortcutsPageForEveryPinnedAppFirstInTheDocksOrderAndStillOpensOnNow() {
        show(startShown = true)
        val pinnedKeys =
            state.homeLayout.dock.items.filterIsInstance<AppShortcutItem>()
                .map { "shortcuts/${StreamShortcutPages.appKeyOf(it.appIdentity)}" }
        assertEquals(pinnedKeys, host.dock.pages.map { it.key }.take(pinnedKeys.size))
        assertEquals("now", host.dock.currentKey)
    }

    @Test
    fun aTapOnAPinnedAppInTheOpenStreamShowsItsShortcutsPageAndRingsIt() {
        show(startShown = true)
        composeRule.onNodeWithTag(dockItemTestTag(cameraItemId())).performScrollTo()
        composeRule.onNodeWithTag(dockItemTestTag(cameraItemId())).performClick()
        awaitPage(cameraPage)
        // The page is the app's launcher shortcuts, one card each; the app's icon is the ringed one.
        composeRule.onNodeWithText("Recent").assertIsDisplayed()
        composeRule.onNode(isSelected() and hasAnyAncestor(hasTestTag(dockItemTestTag(cameraItemId())))).assertExists()
        assertTrue(
            "a tap in the contextual mode launched the app: $sent",
            sent.none { it is LauncherShellAction.LaunchApp },
        )
    }

    @Test
    fun aTapOnAPinnedAppFromHomeStillLaunchesIt() {
        show(startShown = false)
        composeRule.onNodeWithTag(dockItemTestTag(cameraItemId())).performClick()
        composeRule.waitForIdle()
        assertTrue("the tap did not launch the app: $sent", sent.any { it is LauncherShellAction.LaunchApp })
        assertTrue("the tap opened the Stream: $sent", LauncherShellAction.OpenStream !in sent)
    }

    @Test
    fun aPressThatStartsOnAPinnedAppFromHomeOpensItsShortcutsPage() {
        show(startShown = false)
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val landing = pinnedCentre() - dock.positionInRoot
        composeRule.pressDock(distancePx = PRESS_PX, settle = false, start = { landing })
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertTrue("the press did not open the Stream: $sent", LauncherShellAction.OpenStream in sent)
        // The request waits for the Stream's notifications (the app's live page would replace its shortcuts page).
        composeRule.waitForIdle()
        awaitPage(cameraPage)
    }

    @Test
    fun holdingTheFingerOverAPinnedAppWhileChoosingSelectsItAndLiftingOpensItsPage() {
        show(startShown = false)
        composeRule.pressDock(distancePx = PRESS_PX, release = false, settle = false)
        composeRule.mainClock.advanceTimeBy(DockMotionConstants.DEFAULT_HOLD_MILLIS + 120L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Today")).fetchSemanticsNodes().isNotEmpty()
        }
        // The early extend scrolled the row to the live entries; holding at its start brings the pinned icons back.
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { moveTo(Offset(END_INSET_PX, center.y)) }
        repeat(EDGE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        val first = label()
        assertTrue("a pinned app was not selected at the row's start: $first", first != null && first != "Now")
        // Move over the camera's icon itself and lift.
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val centre = pinnedCentre()
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveTo(Offset(centre.x - dock.positionInRoot.x, center.y))
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("Camera", label())
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertTrue("the lift did not open the Stream: $sent", LauncherShellAction.OpenStream in sent)
        awaitPage(cameraPage)
    }

    private companion object {
        const val PRESS_PX = 150f
        const val SETTLE_MILLIS = 1_000L
        const val EDGE_FRAMES = 60
        const val END_INSET_PX = 4f
    }
}
