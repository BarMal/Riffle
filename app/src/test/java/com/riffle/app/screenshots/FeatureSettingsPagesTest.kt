package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.HOME_MODE_TEST_TAG_PREFIX
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.LocalTasksSettings
import com.riffle.app.launcher.SIMPLE_MODE_TEST_TAG
import com.riffle.app.launcher.SettingKey
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.featureSwitchTestTag
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HomeMode
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
        mode: HomeMode = HomeMode.PlacedItems,
        anchor: SettingKey? = null,
        capture: Boolean = false,
    ) {
        val base: LauncherShellState = ScreenshotFixtures.cardsState()
        val state =
            base.copy(
                launcherSettings =
                    base.launcherSettings.copy(
                        features = features,
                        home = base.launcherSettings.home.copy(mode = mode),
                    ),
            ).settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)", appBuildIdentityLabel = "Build identity")
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalStreamLayoutRepository provides settingsStreamLayoutRepository(),
                    LocalTasksSettings provides null,
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
    fun homePageOffersHomeShowsAndSimpleModeFirstAndEachSendsItsAction() {
        render(SettingsPage.HOME, capture = true)

        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithTag("${HOME_MODE_TEST_TAG_PREFIX}AppLibrary").performClick()
        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SelectHomeMode(HomeMode.AppLibrary),
                LauncherShellAction.SetSimpleMode(true),
            ),
            actions,
        )
    }

    @Test
    fun homePageWithTheAppLibraryChosen() {
        render(SettingsPage.HOME, mode = HomeMode.AppLibrary, capture = true)
        composeRule.onNodeWithTag("${HOME_MODE_TEST_TAG_PREFIX}AppLibrary").assertIsDisplayed()
    }

    @Test
    fun homePageWithSimpleModeOnShowsTheAppLibraryAndCannotChangeIt() {
        render(SettingsPage.HOME, FeatureSettings(simpleMode = true), capture = true)

        composeRule.onNodeWithTag(SIMPLE_MODE_TEST_TAG).assertIsOn().assertIsEnabled()
        composeRule.onNodeWithTag("${HOME_MODE_TEST_TAG_PREFIX}PlacedItems").assertIsNotEnabled()
        composeRule.onNodeWithTag("${HOME_MODE_TEST_TAG_PREFIX}AppLibrary").assertIsNotEnabled()
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
