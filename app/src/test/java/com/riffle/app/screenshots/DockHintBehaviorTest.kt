package com.riffle.app.screenshots

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.DOCK_HINT_TEST_TAG
import com.riffle.app.launcher.DockHint
import com.riffle.app.launcher.DockHintLayer
import com.riffle.app.launcher.HINT_ON_SCREEN_MILLIS
import com.riffle.app.launcher.HintLiveInputs
import com.riffle.app.launcher.HintPlacement
import com.riffle.app.launcher.HintStage
import com.riffle.app.launcher.HintsHost
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.hints.Hint
import com.riffle.core.domain.launcher.hints.HintContext
import com.riffle.core.domain.launcher.hints.HintProgress
import com.riffle.core.domain.launcher.hints.Hints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Dock hint (ADR 0003, Amendments 12 and 21): drawn by the glass material beside the Dock, announced when it appears,
 * shown once, with no button: a tap on it (also its click action, for an accessibility service), the next touch
 * anywhere or about 6 seconds put it away, and with a screen reader on only an interaction does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockHintBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed by mutableStateOf(0)
    private var hint = Hint.DOCK_PRESS

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                if (dismissed == 0) {
                    DockHint(
                        hint = hint,
                        dockBounds = Rect(24f, 1900f, 1056f, 2100f),
                        pull = DockPullDirection.UP,
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }
    }

    @Test
    fun itSaysWhatToDoAndIsAnnouncedWithNoButton() {
        show()

        composeRule.onNodeWithTag(DOCK_HINT_TEST_TAG).assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithText("Got it").assertDoesNotExist()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theHintInTheDarkTheme() {
        show()
        composeRule.captureScreen()
    }

    @Test
    fun theMenuHintSaysToTouchAndHoldAnApp() {
        hint = Hint.DOCK_MENU
        show()

        composeRule.onNodeWithText("Touch and hold an app on the Dock for its menu").assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun thePullHintSaysToPullTheDockUp() {
        hint = Hint.DOCK_PULL
        show()

        composeRule.onNodeWithText("Pull the Dock up for the Library").assertIsDisplayed()
    }

    @Test
    fun theStackHintSaysToHoldThenSlide() {
        hint = Hint.DOCK_SCRUB
        show()

        composeRule.onNodeWithText("Hold the Dock, then slide along it to change stack").assertIsDisplayed()
    }

    @Test
    fun aTapOnTheHintDismissesIt() {
        show()

        composeRule.onNodeWithTag(DOCK_HINT_TEST_TAG).performClick()

        assertEquals(1, dismissed)
        composeRule.onNodeWithTag(DOCK_HINT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun anAccessibilityServiceCanDismissItByItsClickAction() {
        show()

        composeRule.onNodeWithTag(DOCK_HINT_TEST_TAG).assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }

        assertEquals(1, dismissed)
    }

    // The layer: shown once, retired as it appears, gone by itself.

    private val facts =
        HintContext(streamAvailable = true, libraryAvailable = true, dockHasItems = true)
    private var streamShown by mutableStateOf(false)
    private var underneathTaps = 0

    private fun showLayer(host: HintsHost) {
        val stage = HintStage()
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.fillMaxSize().then(stage.observe())) {
                    Box(modifier = Modifier.fillMaxSize().clickable { underneathTaps++ })
                    DockHintLayer(
                        hints = host,
                        legacyPressSeen = false,
                        facts = facts.copy(streamShown = streamShown),
                        live = HintLiveInputs(dragging = { false }, dockBusy = { false }, stackCount = { 0 }),
                        stage = stage,
                        placement = HintPlacement(Rect(24f, 1900f, 1056f, 2100f), DockPullDirection.UP, 0.dp),
                    )
                }
            }
        }
    }

    private fun hintStands() = composeRule.onAllNodesWithTag(DOCK_HINT_TEST_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theLayerShowsAHintOnceRetiresItAtOnceAndPutsItAwayAfterAboutSixSeconds() {
        val host = HintsHost.inMemory()
        showLayer(host)

        composeRule.waitForIdle()
        assertTrue(hintStands())
        assertTrue(Hint.DOCK_PRESS in host.progress.value!!.retired)

        composeRule.mainClock.advanceTimeBy(HINT_ON_SCREEN_MILLIS - 500)
        composeRule.waitForIdle()
        assertTrue(hintStands())
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertFalse(hintStands())

        // Never again, however long it waits and whichever hint is next (they are paced and each shows once).
        composeRule.mainClock.advanceTimeBy(10 * HINT_ON_SCREEN_MILLIS)
        composeRule.waitForIdle()
        assertFalse(hintStands())
    }

    @Test
    fun theNextTouchAnywhereDismissesItAndStillReachesWhatIsUnderIt() {
        showLayer(HintsHost.inMemory())
        composeRule.waitForIdle()
        assertTrue(hintStands())

        composeRule.onRoot().performTouchInput { click(center.copy(y = 100f)) }
        composeRule.waitForIdle()

        assertFalse(hintStands())
        assertEquals(1, underneathTaps)
    }

    @Test
    fun withAScreenReaderOnThereIsNoTimerAndAnInteractionDismissesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager)
            .setTouchExplorationEnabled(true)
        showLayer(HintsHost.inMemory())
        composeRule.waitForIdle()

        composeRule.mainClock.advanceTimeBy(10 * HINT_ON_SCREEN_MILLIS)
        composeRule.waitForIdle()
        assertTrue(hintStands())

        composeRule.onNodeWithTag(DOCK_HINT_TEST_TAG).performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        assertFalse(hintStands())
    }

    @Test
    fun theGestureBeingPerformedPutsItsHintAwayAtOnce() {
        showLayer(HintsHost.inMemory())
        composeRule.waitForIdle()
        assertTrue(hintStands())

        streamShown = true
        composeRule.waitForIdle()

        assertFalse(hintStands())
    }

    @Test
    fun aHintAlreadyRetiredIsNotShownAndShowHintsAgainBringsItBackOnce() {
        val host = HintsHost.inMemory(HintProgress(retired = Hint.entries.toSet(), homeVisits = 30))
        showLayer(host)
        composeRule.waitForIdle()
        assertFalse(hintStands())

        host.showAgain()
        composeRule.waitForIdle()
        assertTrue(hintStands())
        composeRule.mainClock.advanceTimeBy(HINT_ON_SCREEN_MILLIS + 500)
        composeRule.waitForIdle()
        assertFalse(hintStands())
        assertEquals(setOf(Hint.DOCK_PRESS), host.progress.value!!.retired)
        assertTrue(Hints.MIN_VISITS_BETWEEN_HINTS > 1)
    }
}
