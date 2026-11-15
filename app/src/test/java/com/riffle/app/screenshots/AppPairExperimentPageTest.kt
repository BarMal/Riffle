package com.riffle.app.screenshots

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.APP_PAIR_COPY_TAG
import com.riffle.app.launcher.APP_PAIR_DUPLICATES_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_FIRST_OPEN_TAG
import com.riffle.app.launcher.APP_PAIR_FIRST_TAG
import com.riffle.app.launcher.APP_PAIR_LAUNCH_TAG
import com.riffle.app.launcher.APP_PAIR_LOG_TAG
import com.riffle.app.launcher.APP_PAIR_METHOD_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_ONE_CALL_TAG
import com.riffle.app.launcher.APP_PAIR_PICK_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_PRESET_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_RESULT_FLAGS_TAG
import com.riffle.app.launcher.APP_PAIR_SECOND_TAG
import com.riffle.app.launcher.APP_PAIR_VERDICT_TAG_PREFIX
import com.riffle.app.launcher.AppPairEnvironment
import com.riffle.app.launcher.LocalAppPairEnvironment
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.LocalTasksSettings
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.appPairSwitchTag
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.CustomAppFlags
import com.riffle.app.launcher.diagnostics.pair.CustomSwitch
import com.riffle.app.launcher.diagnostics.pair.PairFacts
import com.riffle.app.launcher.diagnostics.pair.PairFactsSource
import com.riffle.app.launcher.diagnostics.pair.PairLaunchWatcher
import com.riffle.app.launcher.diagnostics.pair.PairMethod
import com.riffle.app.launcher.diagnostics.pair.PairObservation
import com.riffle.app.launcher.diagnostics.pair.PairPreset
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.PairVerdict
import com.riffle.app.launcher.diagnostics.pair.TrampolineRequest
import com.riffle.app.launcher.diagnostics.pair.isOn
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
    private var watchedLaunches = 0
    private val watcher =
        PairLaunchWatcher { _, onTrace ->
            watchedLaunches++
            onTrace(listOf(PairObservation(12, "onPause")), false)
        }

    private fun selectCustom() {
        composeRule.onNodeWithTag(APP_PAIR_METHOD_TAG_PREFIX + PairMethod.CUSTOM.name).performScrollTo().performClick()
    }

    private fun toggle(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo().performClick()

    private fun assertFlags(text: String) =
        composeRule.onNodeWithTag(
            APP_PAIR_RESULT_FLAGS_TAG,
        ).performScrollTo().assertTextContains(text, substring = true)

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
                    LocalAppPairEnvironment provides AppPairEnvironment(experiment, starter, facts, watcher),
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
    fun customSwitchesAreHiddenUntilCustomIsChosenThenShowTheResultingFlagsLive() {
        render()
        composeRule.onAllNodesWithTag(APP_PAIR_ONE_CALL_TAG).assertCountEquals(0)
        selectCustom()
        assertFlags("First: LAUNCH_ADJACENT|NEW_TASK\nSecond: LAUNCH_ADJACENT|NEW_TASK|MULTIPLE_TASK")
        toggle(appPairSwitchTag("first", CustomSwitch.CLEAR_TASK))
        toggle(appPairSwitchTag("first", CustomSwitch.NO_ANIMATION))
        toggle(appPairSwitchTag("second", CustomSwitch.ADJACENT))
        toggle(appPairSwitchTag("second", CustomSwitch.MULTIPLE_TASK))
        assertFlags("First: LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK|NO_ANIMATION\nSecond: NEW_TASK")
        assertFlags("Second app starts 600 ms after the first.")
        toggle(APP_PAIR_ONE_CALL_TAG)
        assertFlags("Both start in one call.")
        composeRule.captureScreen()
    }

    @Test
    fun aPresetFillsEverySwitch() {
        render()
        selectCustom()
        PairPreset.entries.forEach { preset ->
            toggle(APP_PAIR_PRESET_TAG_PREFIX + preset.name)
            assertSwitches("first", preset.pair.first)
            assertSwitches("second", preset.pair.second)
            val one = composeRule.onNodeWithTag(APP_PAIR_ONE_CALL_TAG).performScrollTo()
            if (preset.pair.oneCall) one.assertIsOn() else one.assertIsOff()
            assertFlags("First: ${preset.pair.first.description}\nSecond: ${preset.pair.second.description}")
        }
    }

    private fun assertSwitches(
        slot: String,
        flags: CustomAppFlags,
    ) = CustomSwitch.entries.forEach { switch ->
        val node = composeRule.onNodeWithTag(appPairSwitchTag(slot, switch)).performScrollTo()
        if (flags.isOn(switch)) node.assertIsOn() else node.assertIsOff()
    }

    @Test
    fun aCustomLaunchWritesItsRecipeTicksTraceAndVerdictIntoTheEntryAndTheCopiedText() {
        render()
        choosePair()
        selectCustom()
        toggle(APP_PAIR_PRESET_TAG_PREFIX + PairPreset.ONE_CALL_RESET_BOTH.name)
        toggle(APP_PAIR_FIRST_OPEN_TAG)
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }
        assertEquals(listOf("startAll"), calls)
        assertEquals(1, watchedLaunches)
        val entry = experiment.log.value.single()
        assertEquals(PairMethod.CUSTOM, entry.method)
        assertEquals(PairPreset.ONE_CALL_RESET_BOTH.pair, entry.custom)
        assertTrue(entry.firstWasOpen && !entry.secondWasOpen)
        assertEquals(listOf(PairObservation(12, "onPause")), entry.trace)

        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX${entry.id}.${PairVerdict.SPLIT}").performScrollTo()
        PairVerdict.entries.forEach {
            composeRule.onNodeWithTag(
                "$APP_PAIR_VERDICT_TAG_PREFIX${entry.id}.$it",
            ).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX${entry.id}.${PairVerdict.SPLIT_UNEVEN}")
            .performScrollTo().performClick()
        assertEquals(PairVerdict.SPLIT_UNEVEN, experiment.log.value.single().verdict)
        toggle("$APP_PAIR_DUPLICATES_TAG_PREFIX${entry.id}")
        assertTrue(experiment.log.value.single().duplicatesInRecents)
        composeRule.captureScreen()

        composeRule.onNodeWithTag(APP_PAIR_COPY_TAG).performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        listOf(
            "Custom  one startActivities call, delay ignored",
            "first:  ${apps[0]}",
            "second: ${apps[1]}",
            "flags first:  NEW_TASK|CLEAR_TASK",
            "flags second: LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK",
            "already open (ticked by the owner): first yes, second no",
            "+12 ms onPause",
            "owner's verdict: Split, uneven",
            "duplicates in Recents: yes",
            "Android SDK 34, Acme Fold",
        ).forEach { assertTrue("$it in\n$clip", clip.contains(it)) }
    }

    @Test
    fun theNewestEntryAndOlderEntriesEachTakeTheirOwnVerdict() {
        render()
        choosePair()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        val (newest, older) = experiment.log.value
        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX${older.id}.${PairVerdict.NO_SPLIT}")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX${newest.id}.${PairVerdict.SPLIT}")
            .performScrollTo().performClick()
        assertEquals(
            mapOf(older.id to PairVerdict.NO_SPLIT, newest.id to PairVerdict.SPLIT),
            experiment.log.value.associate { it.id to it.verdict },
        )
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
