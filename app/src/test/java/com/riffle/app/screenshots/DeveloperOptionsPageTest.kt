package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.DEVELOPER_OPTIONS_REVEAL_TAPS
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.VERSION_ROW_TEST_TAG
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > About: the diagnostics tools sit behind the "Developer options" switch, which shows after the Version row
 * was tapped seven times. After-update starts stays reachable without it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DeveloperOptionsPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val SWITCH_SUMMARY = "Show experiments and tuning pages under About"
        const val PAGE_SUMMARY = "Experiments and tuning for troubleshooting"
    }

    private var shell by mutableStateOf(ScreenshotFixtures.homeState())
    private val actions = mutableListOf<LauncherShellAction>()

    private fun render(page: SettingsPage = SettingsPage.VERSION) {
        composeRule.setContent {
            ScreenshotBackdrop {
                SettingsSurface(
                    state = shell.settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)"),
                    initialPage = page,
                    onAction = { action ->
                        actions += action
                        shell = shell.reduceDeveloper(action)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun LauncherShellState.reduceDeveloper(action: LauncherShellAction): LauncherShellState {
        val diagnostics = launcherSettings.diagnostics
        val updated =
            when (action) {
                LauncherShellAction.RevealDeveloperOptions -> diagnostics.copy(developerOptionsRevealed = true)
                is LauncherShellAction.SetDeveloperOptions -> diagnostics.copy(developerOptions = action.enabled)
                else -> diagnostics
            }
        return copy(launcherSettings = launcherSettings.copy(diagnostics = updated))
    }

    private fun tapVersion(times: Int) {
        repeat(times) { composeRule.onNodeWithTag(VERSION_ROW_TEST_TAG).performScrollTo().performClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun aFreshAboutPageShowsNoDeveloperRowsButKeepsAfterUpdateStarts() {
        render()

        composeRule.onNodeWithText("After-update starts").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Developer options").assertDoesNotExist()
        composeRule.onNodeWithText("Drag tuning").assertDoesNotExist()
        composeRule.onNodeWithText("App pair experiment").assertDoesNotExist()
        composeRule.onNodeWithText("What apps publish").assertDoesNotExist()
    }

    @Test
    fun sixTapsOnTheVersionRowRevealNothingAndTheSeventhShowsTheSwitch() {
        render()

        tapVersion(DEVELOPER_OPTIONS_REVEAL_TAPS - 1)
        composeRule.onNodeWithText("Developer options").assertDoesNotExist()
        assertEquals(emptyList<LauncherShellAction>(), actions)

        tapVersion(1)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.RevealDeveloperOptions), actions)
        composeRule.onNodeWithText(SWITCH_SUMMARY).performScrollTo().assertIsDisplayed()
        // The switch is there, but the page it offers is not until it is turned on.
        composeRule.onNodeWithText(PAGE_SUMMARY).assertDoesNotExist()
    }

    @Test
    fun turningTheSwitchOnOffersThePageWithTheFourDiagnosticsAndOffTakesItAway() {
        render()
        tapVersion(DEVELOPER_OPTIONS_REVEAL_TAPS)

        composeRule.onNodeWithText(SWITCH_SUMMARY).performScrollTo().performClick()
        assertEquals(LauncherShellAction.SetDeveloperOptions(enabled = true), actions.last())
        composeRule.onNodeWithText(PAGE_SUMMARY).performScrollTo().performClick()
        composeRule.waitForIdle()
        listOf("What apps publish", "App pair experiment", "Pair recipe", "Drag tuning").forEach { title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun theSwitchStaysAfterwardsAndCanBeTurnedOff() {
        val revealed = shell.launcherSettings.diagnostics.copy(developerOptionsRevealed = true, developerOptions = true)
        shell = shell.copy(launcherSettings = shell.launcherSettings.copy(diagnostics = revealed))
        render()

        composeRule.onNodeWithText(SWITCH_SUMMARY).performScrollTo().performClick()
        assertEquals(LauncherShellAction.SetDeveloperOptions(enabled = false), actions.last())
        composeRule.onNodeWithText(PAGE_SUMMARY).assertDoesNotExist()
    }
}
