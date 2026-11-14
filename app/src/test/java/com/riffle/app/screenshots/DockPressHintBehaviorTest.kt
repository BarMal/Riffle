package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.riffle.app.launcher.DOCK_PRESS_HINT_DISMISS_TEST_TAG
import com.riffle.app.launcher.DOCK_PRESS_HINT_TEST_TAG
import com.riffle.app.launcher.DockPressHint
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-run hint (ADR 0003, Amendment 3): drawn by the glass material beside the Dock, announced when it appears,
 * and dismissed by its own button, which is a plain 48 dp target an accessibility service can reach.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPressHintBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed by mutableStateOf(0)

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                if (dismissed == 0) {
                    DockPressHint(
                        dockBounds = Rect(24f, 1900f, 1056f, 2100f),
                        pull = DockPullDirection.UP,
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }
    }

    @Test
    fun itSaysWhatToDoIsAnnouncedAndOffersADismissButton() {
        show()

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_DISMISS_TEST_TAG).assertHasClickAction()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theHintInTheDarkTheme() {
        show()
        composeRule.captureScreen()
    }

    @Test
    fun theDismissButtonDismissesIt() {
        show()

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_DISMISS_TEST_TAG).performClick()

        assertEquals(1, dismissed)
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun anAccessibilityServiceCanDismissItByItsClickAction() {
        show()

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_DISMISS_TEST_TAG)
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }

        assertEquals(1, dismissed)
    }

    @Test
    fun itTakesNoTouchOfItsOwnUntilTheButtonIsPressed() {
        show()

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertIsDisplayed()
        assertEquals(0, dismissed)
    }
}
