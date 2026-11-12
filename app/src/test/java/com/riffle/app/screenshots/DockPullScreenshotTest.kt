package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
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
 * The Dock pull by side (ADR 0003, The Dock is the handle) through the real [HomeDestination]: a pull that starts on
 * the dynamic side opens the Stream, tracking the finger (the Stream slides in from the Dock's edge as Home fades),
 * one that starts on the pinned side opens the Library, and a short pull springs back. The shell is stood in for by
 * recording the actions the pull asks for. Screenshots: at rest, mid-pull, and mid-pull with reduced motion.
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
    fun streamPullMid() {
        render(ScreenshotFixtures.homeState())
        pull(from = Side.DYNAMIC, fraction = 0.35f, release = false)
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertExists()
        composeRule.captureScreen()
    }

    @Test
    fun streamPullMidReducedMotion() {
        val reduced =
            LauncherSettings(motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON))
        render(ScreenshotFixtures.homeState().copy(launcherSettings = reduced))
        pull(from = Side.DYNAMIC, fraction = 0.35f, release = false)
        composeRule.captureScreen()
    }

    @Test
    fun aPullFromTheDynamicSideOpensTheStream() {
        render(ScreenshotFixtures.homeState())
        pull(from = Side.DYNAMIC, fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun aPullFromThePinnedSideOpensTheLibrary() {
        render(ScreenshotFixtures.homeState())
        pull(from = Side.PINNED, fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    @Test
    fun aShortPullSpringsBackAndOpensNothing() {
        render(ScreenshotFixtures.homeState())
        pull(from = Side.DYNAMIC, fraction = 0.08f, release = true)
        composeRule.waitForIdle()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theSamePullFromTheStreamReturnsHome() {
        render(ScreenshotFixtures.homeState(), streamShown = true)
        pull(from = Side.DYNAMIC, fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun aPinnedSidePullFromTheStreamOpensTheLibrary() {
        render(ScreenshotFixtures.homeState(), streamShown = true)
        pull(from = Side.PINNED, fraction = 0.6f, release = true)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    @Test
    fun aBottomDockPullsUpToTheStream() = streamPullOn(DockPosition.BOTTOM)

    @Test
    fun aTopDockPullsDownToTheStream() = streamPullOn(DockPosition.TOP)

    @Test
    fun aLeftDockPullsRightToTheStream() = streamPullOn(DockPosition.LEFT)

    @Test
    fun aRightDockPullsLeftToTheStream() = streamPullOn(DockPosition.RIGHT)

    private fun streamPullOn(edge: DockPosition) {
        render(ScreenshotFixtures.homeState(dockPosition = edge))
        pull(from = Side.DYNAMIC, fraction = 0.6f, release = true, edge = edge)
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    private enum class Side { PINNED, DYNAMIC }

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

    /**
     * A steady pull away from the dock's edge by a fraction of the window, starting on the dock's [from] side: the
     * Stream button's tile for the dynamic side, the start of the dock's own area (before the dynamic section) for the
     * pinned one.
     */
    private fun pull(
        from: Side,
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
        val tag = if (from == Side.DYNAMIC) dockDynamicSectionTileTestTag("Stream") else HOME_DOCK_PULL_TEST_TAG
        composeRule.onNodeWithTag(tag).performTouchInput {
            val start =
                if (from == Side.DYNAMIC) {
                    center
                } else if (vertical) {
                    Offset(width * PINNED_FRACTION, height / 2f)
                } else {
                    Offset(width / 2f, height * PINNED_FRACTION)
                }
            down(start)
            repeat(PULL_STEPS) {
                moveBy(if (vertical) Offset(0f, total / PULL_STEPS) else Offset(total / PULL_STEPS, 0f))
            }
            if (release) up()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val PULL_STEPS = 12
        const val PINNED_FRACTION = 0.05f
    }
}
