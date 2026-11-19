package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.ADD_NEW_APPS_TEST_TAG
import com.riffle.app.launcher.DRAG_TUNING_RESET_TEST_TAG
import com.riffle.app.launcher.DRAG_TUNING_VALUES_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalRecentPlayers
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.LocalTasksSettings
import com.riffle.app.launcher.NOW_CLEAR_RECENT_PLAYERS_TEST_TAG
import com.riffle.app.launcher.NOW_RECENT_PLAYERS_TEST_TAG
import com.riffle.app.launcher.SIMPLE_MODE_TEST_TAG
import com.riffle.app.launcher.SettingKey
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.featureSwitchTestTag
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.app.launcher.stream.RecentPlayersControl
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherFeature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The slice 7b settings rows through the real [SettingsSurface]: Home shows (Placed items or App library) and Simple
 * mode on Home, the Stream, Dock live apps and Library switches on their own pages. Each sends one action, and
 * while Simple mode is on the rows it overrides read as off and cannot be changed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class FeatureSettingsPagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    private fun render(
        page: SettingsPage,
        features: FeatureSettings = FeatureSettings(),
        anchor: SettingKey? = null,
        capture: Boolean = false,
        recentPlayers: RecentPlayersControl? = null,
    ) {
        val base: LauncherShellState = ScreenshotFixtures.cardsState()
        val state =
            base.copy(
                launcherSettings =
                    base.launcherSettings.copy(features = features),
            ).settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)", appBuildIdentityLabel = "Build identity")
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalStreamLayoutRepository provides settingsStreamLayoutRepository(),
                    LocalTasksSettings provides null,
                    LocalRecentPlayers provides recentPlayers,
                ) {
                    SettingsSurface(
                        state = state,
                        initialPage = page,
                        initialAnchor = anchor,
                        onAction = { action -> actions += action },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        if (capture) composeRule.captureScreen()
    }

    @Test
    fun homePageOffersSimpleModeAndAddNewAppsFirstAndEachSendsItsAction() {
        render(SettingsPage.HOME, capture = true)

        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithTag(ADD_NEW_APPS_TEST_TAG).assertIsDisplayed().assertIsOn().performClick()
        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SetAddNewApps(false),
                LauncherShellAction.SetSimpleMode(true),
            ),
            actions,
        )
    }

    @Test
    fun homePageWithSimpleModeOnShowsTheSwitchOn() {
        render(SettingsPage.HOME, FeatureSettings(simpleMode = true), capture = true)

        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).assertIsOn().assertIsEnabled()
    }

    @Test
    fun theDragTuningPageShowsTheValuesAsCopyableTextAndResetSendsTheDefaults() {
        render(SettingsPage.DRAG_TUNING, capture = true)

        composeRule.onNodeWithTag(DRAG_TUNING_VALUES_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DRAG_TUNING_VALUES_TEST_TAG).assertTextContains("ring=0.75", substring = true)
        composeRule.onNodeWithTag(DRAG_TUNING_RESET_TEST_TAG).performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SetDragTuning(com.riffle.core.domain.launcher.home.HomeDragTuning()),
            ),
            actions,
        )
    }

    @Test
    fun homePageHasNoHomeShowsChoiceAnyMore() {
        // Deleted with the retired Home mode: the "Home shows" rows (placed items, app library).
        render(SettingsPage.HOME)
        composeRule.onAllNodesWithText("Home shows").assertCountEquals(0)
    }

    @Test
    fun theStreamSwitchIsOnByDefaultAndTurnsOff() {
        render(SettingsPage.STREAM, capture = true)

        composeRule.onNodeWithTag(featureSwitchTestTag(LauncherFeature.STREAM)).assertIsOn().performClick()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SetFeatureEnabled(LauncherFeature.STREAM, false)),
            actions,
        )
    }

    @Test
    fun nowsRecentPlayersAreOnByDefaultTurnOffAndClearForgetsThemInOneTap() {
        val cleared = java.util.concurrent.atomic.AtomicInteger()
        val control =
            object : RecentPlayersControl {
                override fun dismiss(key: String) = Unit

                override fun clear() {
                    cleared.incrementAndGet()
                }
            }
        render(
            SettingsPage.STREAM,
            anchor = SettingKey.NOW_CLEAR_RECENT_PLAYERS,
            recentPlayers = control,
            capture = true,
        )

        composeRule.onNodeWithTag(NOW_RECENT_PLAYERS_TEST_TAG).assertIsDisplayed().assertIsOn().performClick()
        composeRule.onNodeWithTag(NOW_RECENT_PLAYERS_TEST_TAG).assertIsOff()

        composeRule.onNodeWithTag(NOW_CLEAR_RECENT_PLAYERS_TEST_TAG).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { cleared.get() == 1 }
    }

    @Test
    fun theLibrarySwitchIsOnByDefaultAndTurnsOff() {
        render(SettingsPage.LIBRARY, capture = true)

        composeRule.onNodeWithTag(featureSwitchTestTag(LauncherFeature.LIBRARY)).assertIsOn().performClick()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SetFeatureEnabled(LauncherFeature.LIBRARY, false)),
            actions,
        )
    }

    @Test
    fun theLiveAppsSwitchIsOnTheDockPageAndTurnsOff() {
        render(SettingsPage.DOCK, anchor = SettingKey.FEATURE_DOCK_LIVE_APPS, capture = true)

        composeRule.onNodeWithTag(featureSwitchTestTag(LauncherFeature.DOCK_LIVE_APPS)).assertIsOn().performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SetFeatureEnabled(LauncherFeature.DOCK_LIVE_APPS, false),
            ),
            actions,
        )
    }

    @Test
    fun aSwitchThatIsOffReadsOffAndTurnsBackOn() {
        render(SettingsPage.LIBRARY, FeatureSettings(library = false))

        composeRule.onNodeWithTag(featureSwitchTestTag(LauncherFeature.LIBRARY)).assertIsOff().performClick()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SetFeatureEnabled(LauncherFeature.LIBRARY, true)),
            actions,
        )
    }

    @Test
    fun whileSimpleModeIsOnTheSwitchesItOverridesReadOffAndAreDisabled() {
        render(SettingsPage.STREAM, FeatureSettings(simpleMode = true))
        composeRule.onNodeWithTag(featureSwitchTestTag(LauncherFeature.STREAM)).assertIsOff().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun homePageDark() {
        render(SettingsPage.HOME, capture = true)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun libraryPageDarkWithTheLibraryOff() {
        render(SettingsPage.LIBRARY, FeatureSettings(library = false), capture = true)
    }
}
