package com.riffle.app.screenshots

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.APP_PAIR_ADVANCED_TAG
import com.riffle.app.launcher.APP_PAIR_COPY_TAG
import com.riffle.app.launcher.APP_PAIR_DUPLICATES_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_EDIT_AS_CUSTOM_TAG
import com.riffle.app.launcher.APP_PAIR_FIRST_OPEN_TAG
import com.riffle.app.launcher.APP_PAIR_FIRST_TAG
import com.riffle.app.launcher.APP_PAIR_LAUNCH_TAG
import com.riffle.app.launcher.APP_PAIR_LOG_TAG
import com.riffle.app.launcher.APP_PAIR_METHOD_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_ONE_CALL_TAG
import com.riffle.app.launcher.APP_PAIR_PICK_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_RESULT_FLAGS_TAG
import com.riffle.app.launcher.APP_PAIR_SCENARIO_RESULT_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_SCENARIO_TAG_PREFIX
import com.riffle.app.launcher.APP_PAIR_SCRIPT_CHOSEN_TAG
import com.riffle.app.launcher.APP_PAIR_SCRIPT_COPY_TAG
import com.riffle.app.launcher.APP_PAIR_SCRIPT_RUN_TAG
import com.riffle.app.launcher.APP_PAIR_SCRIPT_STATUS_TAG
import com.riffle.app.launcher.APP_PAIR_SCRIPT_STOP_TAG
import com.riffle.app.launcher.APP_PAIR_SECOND_OPEN_TAG
import com.riffle.app.launcher.APP_PAIR_SECOND_TAG
import com.riffle.app.launcher.APP_PAIR_VERDICT_TAG_PREFIX
import com.riffle.app.launcher.AppPairEnvironment
import com.riffle.app.launcher.AppPairScriptEnvironment
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
import com.riffle.app.launcher.diagnostics.pair.PairScenario
import com.riffle.app.launcher.diagnostics.pair.PairScriptLifecycle
import com.riffle.app.launcher.diagnostics.pair.PairScriptRunner
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.PairTarget
import com.riffle.app.launcher.diagnostics.pair.PairVerdict
import com.riffle.app.launcher.diagnostics.pair.TrampolineRequest
import com.riffle.app.launcher.diagnostics.pair.isOn
import com.riffle.app.launcher.settingsSurfaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
@OptIn(ExperimentalCoroutinesApi::class)
class AppPairExperimentPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The script runs in a scope on virtual time, so the tests decide when settle times and dwells pass.
    private val scheduler = TestCoroutineScheduler()
    private val launcherResumed = MutableStateFlow(true)
    private val toasts = mutableListOf<String>()
    private val unlaunchable = mutableSetOf<String>()
    private val scriptRunner by lazy {
        PairScriptRunner(
            CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob()),
            { scheduler.currentTime },
            object : PairScriptLifecycle {
                override val resumed: StateFlow<Boolean> = launcherResumed
            },
            { toasts += it },
            { id, change -> experiment.updateScript(id, change) },
        )
    }
    private val fixturePackages by lazy {
        ScreenshotFixtures.installedApps.map { it.identity.packageName.value }.let { it[0] to it[1] }
    }

    private val calls = mutableListOf<String>()
    private val starter =
        object : PairStarter {
            override fun start(start: PairStart) {
                calls += "start ${start.target.packageName}"
                launcherResumed.value = false
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

    private fun expandAdvanced() = composeRule.onNodeWithTag(APP_PAIR_ADVANCED_TAG).performScrollTo().performClick()

    private fun runScenario(scenario: PairScenario) =
        composeRule.onNodeWithTag(APP_PAIR_SCENARIO_TAG_PREFIX + scenario.name).performScrollTo().performClick()

    private fun scenarioLabel(scenario: PairScenario) =
        composeRule.onNodeWithTag(APP_PAIR_SCENARIO_RESULT_TAG_PREFIX + scenario.name, useUnmergedTree = true)

    private fun copiedText(): String {
        composeRule.onNodeWithTag(APP_PAIR_COPY_TAG).performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
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
                    LocalAppPairEnvironment provides
                        AppPairEnvironment(
                            experiment,
                            starter,
                            facts,
                            watcher,
                            AppPairScriptEnvironment(
                                scriptRunner,
                                fixturePackages,
                                { if (it in unlaunchable) null else PairTarget(it, "$it.Main", it) },
                                { launcherResumed.value = true },
                            ),
                        ),
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

    private fun advanceScript(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        composeRule.waitForIdle()
    }

    private fun scriptStatus() = composeRule.onNodeWithTag(APP_PAIR_SCRIPT_STATUS_TAG).performScrollTo()

    private fun pressRunScript() = composeRule.onNodeWithTag(APP_PAIR_SCRIPT_RUN_TAG).performScrollTo().performClick()

    @Test
    fun theScriptButtonPreselectsTheTwoPackagesAndStartsTheWarmUp() {
        render()
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_RUN_TAG).assertIsDisplayed()
            .assertTextContains("Run the whole script")
        scriptStatus().assertTextContains("Opens GitHub and Waterfox in every combination", substring = true)
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).performScrollTo().assertTextContains("Choose an app", true)
        pressRunScript()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).performScrollTo().assertTextContains(fixturePackages.first, true)
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).performScrollTo()
            .assertTextContains(fixturePackages.second, true)
        assertTrue(calls.isEmpty())

        advanceScript(1_001)
        assertEquals(listOf("start ${fixturePackages.first}", "start ${fixturePackages.second}"), calls)
        assertEquals("Warm-up: open both", experiment.log.value.single().scenario)
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_RUN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_STOP_TAG).assertIsDisplayed().assertTextContains("Stop")
    }

    @Test
    fun aMissingAppStopsTheScriptAndTheMessageNamesIt() {
        render()
        unlaunchable += fixturePackages.second
        pressRunScript()
        advanceScript(10_000)
        assertTrue(calls.isEmpty())
        scriptStatus().assertTextContains("Not installed or not launchable: ${fixturePackages.second}", true)
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_RUN_TAG).assertIsDisplayed()
    }

    @Test
    fun theStepCounterFollowsTheRunAndStopEndsIt() {
        render()
        pressRunScript()
        advanceScript(1_001)
        scriptStatus().assertTextEquals("Step 1 of 9: Warm-up: open both")
        advanceScript(4_001)
        advanceScript(2_000)
        scriptStatus().assertTextEquals("Step 2 of 9: Reuse both")
        advanceScript(4_000)
        advanceScript(2_000)
        scriptStatus().assertTextEquals("Step 3 of 9: Reset both")
        assertEquals(
            listOf("Step 1 of 9: Warm-up: open both", "Step 2 of 9: Reuse both", "Step 3 of 9: Reset both"),
            toasts,
        )
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_STOP_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        scriptStatus().assertTextEquals("Stopped at step 3 of 9.")
        advanceScript(60_000)
        assertEquals(3, experiment.log.value.size)
        composeRule.captureScreen()
    }

    @Test
    fun theScriptRunsWithTheChosenAppsToo() {
        render()
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_CHOSEN_TAG).performScrollTo().assertIsNotEnabled()
        choosePair()
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_CHOSEN_TAG).performScrollTo().assertIsEnabled().performClick()
        advanceScript(1_001)
        assertEquals(listOf("start ${fixturePackages.first}", "start ${fixturePackages.second}"), calls)
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_CHOSEN_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun copyScriptLogCopiesOnlyTheRunInStepOrderUnderOneHeader() {
        render()
        choosePair()
        runScenario(PairScenario.FRESH_BOTH)
        composeRule.waitForIdle()
        launcherResumed.value = true
        composeRule.onAllNodesWithTag(APP_PAIR_SCRIPT_COPY_TAG).assertCountEquals(0)
        pressRunScript()
        repeat(3) {
            advanceScript(1_001)
            advanceScript(4_001)
            advanceScript(2_000)
        }
        composeRule.onNodeWithTag(APP_PAIR_SCRIPT_COPY_TAG).performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()

        assertTrue(clip, clip.startsWith("App pair script log (oldest first)\n"))
        listOf(
            "device: Android SDK 34, Acme Fold",
            "app pair: first ${fixturePackages.first}, second ${fixturePackages.second}",
            "started: ",
            "script: step 1 of 9",
            "script: step 2 of 9",
            "script: step 3 of 9",
            "return attempt: ok",
            "returned to launcher: automatically after 0 ms",
        ).forEach { assertTrue("$it in\n$clip", clip.contains(it)) }
        assertTrue(clip.indexOf("script: step 1 of 9") < clip.indexOf("script: step 2 of 9"))
        assertTrue(clip.indexOf("script: step 2 of 9") < clip.indexOf("script: step 3 of 9"))
        assertEquals(1, Regex("started: ").findAll(clip).count())
        assertTrue("hand-run scenario left out\n$clip", !clip.contains("Fresh both"))
    }

    @Test
    fun thePageShowsItsPickersScenariosAndAnEmptyLogWithAdvancedCollapsed() {
        render()
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).assertIsDisplayed()
        PairScenario.entries.forEach {
            composeRule.onNodeWithTag(APP_PAIR_SCENARIO_TAG_PREFIX + it.name).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag(APP_PAIR_LOG_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(APP_PAIR_ADVANCED_TAG).performScrollTo().assertIsDisplayed()
        PairMethod.entries.forEach {
            composeRule.onAllNodesWithTag(APP_PAIR_METHOD_TAG_PREFIX + it.name).assertCountEquals(0)
        }
        composeRule.onAllNodesWithTag(APP_PAIR_LAUNCH_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(APP_PAIR_ONE_CALL_TAG).assertCountEquals(0)
        composeRule.captureScreen()
    }

    @Test
    fun advancedExpandsToTheFiveRoundOneMethodsAndCustom() {
        render()
        expandAdvanced()
        PairMethod.entries.forEach {
            composeRule.onNodeWithTag(APP_PAIR_METHOD_TAG_PREFIX + it.name).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().assertIsDisplayed()
        composeRule.captureScreen()
        expandAdvanced()
        composeRule.onAllNodesWithTag(APP_PAIR_LAUNCH_TAG).assertCountEquals(0)
    }

    @Test
    fun scenarioButtonsAreDisabledUntilTwoDifferentAppsAreChosen() {
        render()
        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }

        fun assertAll(enabled: Boolean) =
            PairScenario.entries.forEach {
                val node = composeRule.onNodeWithTag(APP_PAIR_SCENARIO_TAG_PREFIX + it.name).performScrollTo()
                if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
            }
        assertAll(false)
        composeRule.onNodeWithTag(APP_PAIR_FIRST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_PICK_TAG_PREFIX + apps[0]).performScrollTo().performClick()
        assertAll(false)
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_PICK_TAG_PREFIX + apps[0]).performScrollTo().performClick()
        assertAll(false)
        runScenario(PairScenario.RESET_BOTH)
        composeRule.waitForIdle()
        assertTrue(calls.isEmpty())
        composeRule.onNodeWithTag(APP_PAIR_SECOND_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_PICK_TAG_PREFIX + apps[1]).performScrollTo().performClick()
        assertAll(true)
    }

    @Test
    fun tappingResetBothLaunchesOnceWithItsFlagsAndWritesAnEntryNamedAfterIt() {
        render()
        choosePair()
        runScenario(PairScenario.RESET_BOTH)
        composeRule.waitForIdle()

        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }
        assertEquals(listOf("start ${apps[0]}", "start ${apps[1]}"), calls)
        assertEquals(1, watchedLaunches)
        val entry = experiment.log.value.single()
        assertEquals("Reset both", entry.scenario)
        assertEquals(PairMethod.CUSTOM, entry.method)
        assertEquals(PairPreset.RESET_BOTH.pair, entry.custom)
        assertEquals(600L, entry.delayMillis)
        composeRule.onNodeWithTag(APP_PAIR_LOG_TAG).performScrollTo().assertIsDisplayed()
        val clip = copiedText()
        listOf(
            "Reset both (Custom)  delay 600 ms",
            "flags first:  LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK",
            "flags second: LAUNCH_ADJACENT|NEW_TASK|CLEAR_TASK",
        ).forEach { assertTrue("$it in\n$clip", clip.contains(it)) }
    }

    @Test
    fun oneCallUsesTheRoundOneStartActivitiesStrategy() {
        render()
        choosePair()
        runScenario(PairScenario.ONE_CALL)
        composeRule.waitForIdle()

        assertEquals(listOf("startAll"), calls)
        val entry = experiment.log.value.single()
        assertEquals("One call", entry.scenario)
        assertEquals(PairMethod.START_ACTIVITIES_ARRAY, entry.method)
        val clip = copiedText()
        listOf(
            "One call (One startActivities call)  one startActivities call, delay ignored",
            "flags first:  NEW_TASK",
            "flags second: LAUNCH_ADJACENT|NEW_TASK|MULTIPLE_TASK",
        ).forEach { assertTrue("$it in\n$clip", clip.contains(it)) }
    }

    @Test
    fun theAlreadyOpenTicksAreWrittenIntoTheScenarioEntry() {
        render()
        choosePair()
        toggle(APP_PAIR_SECOND_OPEN_TAG)
        runScenario(PairScenario.FRESH_BOTH)
        composeRule.waitForIdle()
        val entry = experiment.log.value.single()
        assertTrue(!entry.firstWasOpen && entry.secondWasOpen)
        assertTrue(copiedText().contains("already open (ticked by the owner): first no, second yes"))
    }

    @Test
    fun aRowsTrailingLabelFollowsTheVerdictOfItsMostRecentEntry() {
        render()
        choosePair()
        scenarioLabel(PairScenario.RESET_BOTH).assertDoesNotExist()
        runScenario(PairScenario.RESET_BOTH)
        composeRule.waitForIdle()
        scenarioLabel(PairScenario.RESET_BOTH).assertTextEquals("not marked")
        scenarioLabel(PairScenario.FRESH_BOTH).assertDoesNotExist()
        val id = experiment.log.value.single().id
        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX$id.${PairVerdict.SPLIT_UNEVEN}")
            .performScrollTo().performClick()
        scenarioLabel(PairScenario.RESET_BOTH).assertTextEquals("Split, uneven")
        toggle("$APP_PAIR_DUPLICATES_TAG_PREFIX$id")
        scenarioLabel(PairScenario.RESET_BOTH).assertTextEquals("Split, uneven, duplicates")
        composeRule.onNodeWithTag("$APP_PAIR_VERDICT_TAG_PREFIX$id.${PairVerdict.SPLIT_UNEVEN}")
            .performScrollTo().performClick()
        scenarioLabel(PairScenario.RESET_BOTH).assertTextEquals("not marked, duplicates")
        runScenario(PairScenario.RESET_BOTH)
        composeRule.waitForIdle()
        scenarioLabel(PairScenario.RESET_BOTH).assertTextEquals("not marked")
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun thePageInTheDarkTheme() {
        render()
        composeRule.captureScreen()
    }

    @Test
    fun launchingFromAdvancedRecordsALogEntryAndCopyPutsItOnTheClipboard() {
        render()
        choosePair()
        expandAdvanced()
        val bothAdjacent = APP_PAIR_METHOD_TAG_PREFIX + PairMethod.BOTH_ADJACENT.name
        composeRule.onNodeWithTag(bothAdjacent).performScrollTo().performClick()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        val apps = ScreenshotFixtures.installedApps.map { it.identity.packageName.value }
        assertEquals(listOf("start ${apps[0]}", "start ${apps[1]}"), calls)
        composeRule.onNodeWithTag(APP_PAIR_LOG_TAG).performScrollTo().assertIsDisplayed()
        assertEquals(1, experiment.log.value.size)
        assertNull(experiment.log.value.single().scenario)

        val clip = copiedText()
        assertTrue(clip, clip.contains("Both adjacent") && clip.contains(apps[0]) && clip.contains(apps[1]))
        assertTrue(clip.contains("Android SDK 34, Acme Fold"))
    }

    @Test
    fun customSwitchesAreHiddenUntilCustomIsChosenThenShowTheResultingFlagsLive() {
        render()
        expandAdvanced()
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
    fun editAsCustomLoadsTheLatestScenariosFlagsIntoTheSwitches() {
        render()
        choosePair()
        expandAdvanced()
        selectCustom()
        composeRule.onNodeWithTag(APP_PAIR_EDIT_AS_CUSTOM_TAG).performScrollTo().assertIsNotEnabled()
        PairScenario.entries.forEach { scenario ->
            runScenario(scenario)
            composeRule.waitForIdle()
            toggle(APP_PAIR_EDIT_AS_CUSTOM_TAG)
            assertSwitches("first", scenario.custom.first)
            assertSwitches("second", scenario.custom.second)
            val one = composeRule.onNodeWithTag(APP_PAIR_ONE_CALL_TAG).performScrollTo()
            if (scenario.custom.oneCall) one.assertIsOn() else one.assertIsOff()
            assertFlags("First: ${scenario.custom.first.description}\nSecond: ${scenario.custom.second.description}")
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
        expandAdvanced()
        selectCustom()
        toggle(appPairSwitchTag("first", CustomSwitch.ADJACENT))
        toggle(appPairSwitchTag("first", CustomSwitch.CLEAR_TASK))
        toggle(appPairSwitchTag("second", CustomSwitch.MULTIPLE_TASK))
        toggle(appPairSwitchTag("second", CustomSwitch.CLEAR_TASK))
        toggle(APP_PAIR_ONE_CALL_TAG)
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

        val clip = copiedText()
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
        runScenario(PairScenario.REUSE_BOTH)
        runScenario(PairScenario.REUSE_BOTH)
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
    fun advancedLaunchDoesNothingUntilBothAppsAreChosen() {
        render()
        expandAdvanced()
        composeRule.onNodeWithTag(APP_PAIR_LAUNCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(calls.isEmpty())
        assertTrue(experiment.log.value.isEmpty())
    }
}
