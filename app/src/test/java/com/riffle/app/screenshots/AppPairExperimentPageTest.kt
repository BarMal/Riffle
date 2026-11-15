package com.riffle.app.screenshots

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.APP_PAIR_COPY_TAG
import com.riffle.app.launcher.APP_PAIR_FIRST_TAG
import com.riffle.app.launcher.APP_PAIR_LAUNCH_TAG
import com.riffle.app.launcher.APP_PAIR_LOG_TAG
import com.riffle.app.launcher.APP_PAIR_METHOD_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_PICK_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_SECOND_TAG
import com.riffle.app.launcher.AppPairEnvironment
import com.riffle.app.launcher.LocalAppPairEnvironment
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.LocalTasksSettings
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.PairFacts
import com.riffle.app.launcher.diagnostics.pair.PairFactsSource
import com.riffle.app.launcher.diagnostics.pair.PairMethod
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.TrampolineRequest
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "App pair experiment" diagnostics page through the real [SettingsSurface], over a fake starter: it shows, a pair
 * can be chosen, Launch records a log entry with the calls made, and Copy log puts the text on the clipboard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class AppPairExperimentPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val starter =
        object : PairStarter {
            override fun start(start: PairStart) {
                calls += "start ${start.target.packageName}"
            }

            override fun startAll(starts: List<PairStart>) {
                calls += "startAll"
            }

            override fun startTrampoline(request: TrampolineRequest) {
                calls += "trampoline"
            }
        }
    private val facts =
        PairFactsSource { _, _ ->
            PairFacts(
                34,
                "Acme",
                "Fold",
                false,
                "412x915",
                "compact",
                "PHONE (FOLDED)",
                listOf("freeform=false"),
                emptyList(),
            )
        }
    private val experiment = AppPairExperiment(clock = { 0L }, pause = {})

    private fun render() {
        val state =
            ScreenshotFixtures.cardsState().settingsSurfaceState(
                appVersionLabel = "Riffle 1.0 (test)",
                appBuildIdentityLabel = "Build identity for screenshots",
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalStreamLayoutRepository provides settingsStreamLayoutRepository(),
                    LocalTasksSettings provides null,
                    LocalAppPairEnvironment provides AppPairEnvironment(experiment, starter, facts),
                ) {
                    SettingsSurface(state = state, initialPage = SettingsPage.APP_PAIR, onAction = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun choosePair() {
        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_PICK_TAG_PREFIX + apps[0]).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_PICK_TAG_PREFIX + apps[1]).performScrollTo().performClick()
    }

    @Test
    fun thePageShowsItsExplanationPickersMethodsAndAnEmptyLog() {
        render()
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).assertIsDisplayed()
        PairMethod.entries.forEach {
            composeRule.onNodeWithTag(APP_PAIR_METHOD_TAG_PREFIX + it.name).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag(APP_PAIR_LOG_TAG).assertCountEquals(0)
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun thePageInTheDarkTheme() {
        render()
        composeRule.captureScreen()
    }

    @Test
    fun choosingAPairAndLaunchingRecordsALogEntryAndCopyPutsItOnTheClipboard() {
        render()
        choosePair()
        val bothAdjacent = APP_PAIR_METHOD_TAG_PREFIX + PairMethod.BOTH_ADJACENT.name
        composeRule.onNodeWithTag(bothAdjacent).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }
        assertEquals(listOf("start ${apps[0]}", "start ${apps[1]}"), calls)
        composeRule.onNodeWithTag(APP_PAIR_LOG_TAG).performScrollTo().assertIsDisplayed()
        assertEquals(1, experiment.log.value.size)
        composeRule.captureScreen()

        composeRule.onNodeWithTag(APP_PAIR_COPY_TAG).performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip, clip.contains("Both adjacent") && clip.contains(apps[0]) && clip.contains(apps[1]))
        assertTrue(clip.contains("Android SDK 34, Acme Fold"))
    }

    @Test
    fun launchDoesNothingUntilBothAppsAreChosen() {
        render()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(calls.isEmpty())
        assertTrue(experiment.log.value.isEmpty())
    }
}
