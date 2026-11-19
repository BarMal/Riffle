package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.riffle.app.launcher.DOCK_PRESS_HINT_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.library.LIBRARY_PAGED_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * The feature switches and Simple mode (ADR 0003, Amendment 1) through the real [HomeDestination]: each switch, turned
 * off, takes its entry points away (the gesture, the Dock's accessibility action, the first-run hint, the entries it
 * drew), and the others are untouched. Simple mode leaves the app library as Home and the Dock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class FeatureSwitchesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor =
        Executors.newSingleThreadExecutor {
            Thread(
                it,
                "feature-switches",
            ).apply { isDaemon = true }
        }
    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withManyApps())

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun show(
        features: FeatureSettings = FeatureSettings(),
        withNotifications: Boolean = false,
        edit: (LauncherShellState) -> LauncherShellState = { it },
    ) {
        shell = edit(ScreenshotFixtures.homeState().withManyApps().withFeatures(features))
        val host =
            if (withNotifications) {
                realWiringStreamHost(
                    executor = executor,
                    // Maps is not pinned: a pinned app that is live has no live entry of its own.
                    activeNotifications = listOf(notification(ScreenshotFixtures.maps)),
                    installedApps = ScreenshotFixtures.installedApps,
                )
            } else {
                emptyStreamHost()
            }
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    // The first-run hint is asked for, as the shell does: it is the switches that keep it away.
                    onDockPressHintSeen = {},
                    onAction = { action ->
                        actions += action
                        shell = shell.answering(action)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun notification(app: InstalledApp) =
        LauncherNotification(
            key = LauncherNotificationKey("${app.identity.packageName.value}-1"),
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            category = NotificationCategory.MESSAGE,
            title = "From ${app.label}",
            text = "Something new in ${app.label}",
            postedAtEpochMillis = StreamFixtures.NOW - 60_000L,
        )

    private fun pullUp(fraction: Float = 0.6f) {
        val height = composeRule.onRoot().fetchSemanticsNode().size.height
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, -height * fraction / 8f)) }
            up()
        }
        composeRule.waitForIdle()
    }

    // Defaults: today's behaviour

    @Test
    fun withEverySwitchOnTheDockOffersTheStreamTheLibraryAndSearchAndTheHintShows() {
        show()

        val labels = composeRule.dockActionLabels()
        assertTrue(labels.toString(), "Open Library" in labels)
        assertTrue(labels.toString(), "Show live pages" in labels)
        assertTrue(labels.toString(), "Search" in labels)
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertIsDisplayed()
    }

    // The Stream

    @Test
    fun withTheStreamOffThereIsNoPressNoActionAndNoHint() {
        show(FeatureSettings(stream = false))

        val labels = composeRule.dockActionLabels()
        assertFalse(labels.toString(), "Show live pages" in labels)
        assertTrue(labels.toString(), "Open Library" in labels)
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertDoesNotExist()

        composeRule.pressDock(distancePx = 150f)
        assertTrue("pressing the Dock opens nothing: $actions", LauncherShellAction.OpenStream !in actions)
    }

    @Test
    fun withTheStreamOnPressingTheDockOpensIt() {
        show()

        composeRule.pressDock(distancePx = 150f)

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun aGestureBoundToTheStreamIsUnboundWhileItsSwitchIsOff() {
        val bound = { state: LauncherShellState ->
            state.copy(
                launcherSettings =
                    state.launcherSettings.copy(
                        gestures =
                            state.launcherSettings.gestures.copy(
                                homeGestures =
                                    state.launcherSettings.gestures.homeGestures
                                        .withAction(HomeGesture.ONE_FINGER_UP, LauncherGestureAction.OPEN_STREAM),
                            ),
                    ),
            )
        }
        show(FeatureSettings(stream = false), edit = bound)

        composeRule.onNodeWithTag(com.riffle.app.launcher.HOME_WORKSPACE_GRID_TEST_TAG).performTouchInput {
            swipe(start = Offset(width / 2f, height * 0.8f), end = Offset(width / 2f, height * 0.2f))
        }
        composeRule.waitForIdle()

        assertTrue("an unbound swipe sends nothing: $actions", LauncherShellAction.OpenStream !in actions)
    }

    // The Library

    @Test
    fun withTheLibraryOffThePullAndTheDockActionAreGoneButSearchStays() {
        show(FeatureSettings(library = false))

        val labels = composeRule.dockActionLabels()
        assertFalse(labels.toString(), "Open Library" in labels)
        assertTrue(labels.toString(), "Search" in labels)
        assertTrue(labels.toString(), "Show live pages" in labels)

        pullUp()
        assertTrue("pulling away from the Dock opens nothing: $actions", LauncherShellAction.OpenAppDrawer !in actions)
    }

    @Test
    fun withTheLibraryOnPullingAwayFromTheDockOpensIt() {
        show()

        pullUp()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    // The Dock's live apps

    @Test
    fun theLiveAppsAppearOnTheDockWhenTheirSwitchIsOn() {
        show(withNotifications = true)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(ScreenshotFixtures.maps.label))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun withTheLiveAppsOffTheDockDrawsNoneButTheStreamStaysReachable() {
        show(FeatureSettings(dockLiveApps = false), withNotifications = true)

        val appeared =
            runCatching {
                composeRule.waitUntil(timeoutMillis = 1_500) {
                    composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(ScreenshotFixtures.maps.label))
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
        assertFalse("no live app is drawn while its switch is off", appeared)
        assertTrue("Show live pages" in composeRule.dockActionLabels())
        composeRule.pressDock(distancePx = 150f)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    // Simple mode

    @Test
    fun simpleModeLeavesTheAppLibraryAsHomeAndTheDockWithoutTheStreamTheLibraryOrTheHint() {
        show(FeatureSettings(simpleMode = true))

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(com.riffle.app.launcher.HOME_DOCK_TEST_TAG).assertIsDisplayed()
        val labels = composeRule.dockActionLabels()
        assertEquals(listOf("Search"), labels)
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertDoesNotExist()

        composeRule.pressDock(distancePx = 150f)
        pullUp()
        assertTrue(
            "Simple mode opens neither: $actions",
            actions.none {
                it is LauncherShellAction.OpenStream || it is LauncherShellAction.OpenAppDrawer
            },
        )
    }

    @Test
    fun turningSimpleModeOffGivesEverythingBack() {
        show(FeatureSettings(simpleMode = true))
        shell = shell.answering(LauncherShellAction.SetSimpleMode(false))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertDoesNotExist()
        val labels = composeRule.dockActionLabels()
        assertTrue(labels.toString(), "Open Library" in labels && "Show live pages" in labels)
    }
}
