package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Dock's two gestures through the real [HomeDestination] (ADR 0003, Amendment 3): a pull away from the Dock's edge
 * opens the Library (tracking the finger, as the sheet rises from the Dock's edge), whichever side of the Dock it
 * starts
 * on, and a press toward the edge, released past the threshold, toggles Home and the Stream (the existing transition
 * then
 * runs from rest); a short pull or press springs back. The shell is stood in for by recording the actions the gesture
 * asks for. Screenshots: at rest, mid-pull to the Library (also with reduced motion), and the Dock pressed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPullScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    @Test
    fun homeAtRest() {
        render(ScreenshotFixtures.homeState())
        composeRule.captureScreen()
    }

    @Test
    fun libraryPullMid() {
        render(ScreenshotFixtures.homeState())
        pull(fraction = 0.35f, release = false)
        composeRule.captureScreen()
    }

    @Test
    fun libraryPullMidReducedMotion() {
        val reduced =
            LauncherSettings(motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON))
        render(ScreenshotFixtures.homeState().copy(launcherSettings = reduced))
        pull(fraction = 0.35f, release = false)
        composeRule.captureScreen()
    }

    @Test
    fun theDockPressedDownBeforeTheRelease() {
        render(ScreenshotFixtures.homeState())
        composeRule.pressDock(distancePx = PRESS_PX, release = false)
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
        composeRule.captureScreen()
    }

    @Test
    fun aPullOnTheDockOpensTheLibrary() {
        render(ScreenshotFixtures.homeState())
        pull(fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    @Test
    fun aShortPullSpringsBackAndOpensNothing() {
        render(ScreenshotFixtures.homeState())
        pull(fraction = 0.08f, release = true)
        composeRule.waitForIdle()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aPullFromTheStreamStillOpensTheLibraryOverIt() {
        render(ScreenshotFixtures.homeState(), streamShown = true)
        pull(fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    @Test
    fun aPressOpensTheStream() {
        render(ScreenshotFixtures.homeState())
        composeRule.pressDock(distancePx = PRESS_PX)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun theSamePressFromTheStreamReturnsHome() {
        render(ScreenshotFixtures.homeState(), streamShown = true)
        composeRule.pressDock(distancePx = PRESS_PX)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun aShortPressSpringsBackAndOpensNothing() {
        render(ScreenshotFixtures.homeState())
        // Past touch slop (16 dp) so it is a press, short of the 28 dp threshold, on whatever density the device has.
        composeRule.pressDock(distancePx = SHORT_PRESS_DP * composeRule.density.density)
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aBottomDockIsPressedDown() = pressOn(DockPosition.BOTTOM)

    @Test
    fun aTopDockIsPressedUp() = pressOn(DockPosition.TOP)

    @Test
    fun aLeftDockIsPressedLeft() = pressOn(DockPosition.LEFT)

    @Test
    fun aRightDockIsPressedRight() = pressOn(DockPosition.RIGHT)

    @Test
    fun aBottomDockPulledUpOpensTheLibrary() = pullOn(DockPosition.BOTTOM)

    @Test
    fun aTopDockPulledDownOpensTheLibrary() = pullOn(DockPosition.TOP)

    @Test
    fun aLeftDockPulledRightOpensTheLibrary() = pullOn(DockPosition.LEFT)

    @Test
    fun aRightDockPulledLeftOpensTheLibrary() = pullOn(DockPosition.RIGHT)

    private fun pullOn(edge: DockPosition) {
        render(ScreenshotFixtures.homeState(dockPosition = edge))
        pull(fraction = 0.6f, release = true, edge = edge)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    private fun pressOn(edge: DockPosition) {
        render(ScreenshotFixtures.homeState(dockPosition = edge))
        composeRule.pressDock(distancePx = PRESS_PX, edge = edge)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    private fun render(
        state: LauncherShellState,
        streamShown: Boolean = false,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    streamShown = streamShown,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** A steady pull away from the dock's edge by a fraction of the window, starting on the Dock's body. */
    private fun pull(
        fraction: Float,
        release: Boolean,
        edge: DockPosition = DockPosition.BOTTOM,
    ) {
        val window = composeRule.onRoot().fetchSemanticsNode().size
        val vertical = edge == DockPosition.BOTTOM || edge == DockPosition.TOP
        val sign =
            when (edge) {
                DockPosition.BOTTOM, DockPosition.RIGHT -> -1f
                DockPosition.TOP, DockPosition.LEFT -> 1f
            }
        val total = (if (vertical) window.height else window.width) * fraction * sign
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(PULL_STEPS) {
                moveBy(if (vertical) Offset(0f, total / PULL_STEPS) else Offset(total / PULL_STEPS, 0f))
            }
            if (release) up()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val PULL_STEPS = 12
        const val PRESS_PX = 150f
        const val SHORT_PRESS_DP = 22f
    }
}
