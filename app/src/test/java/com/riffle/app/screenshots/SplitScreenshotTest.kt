package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.split.SPLIT_DIVIDER_TEST_TAG
import com.riffle.app.launcher.split.SPLIT_HOME_PANE_TEST_TAG
import com.riffle.app.launcher.split.SPLIT_STREAM_PANE_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_STAGE_RAIL_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamWideView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The unfolded split (ADR 0003, Folded and unfolded) through the real [HomeDestination]: Home and the Stream side by
 * side with a movable divider, the Stream alone, and the tabletop's stacked panes over the hinge. The screenshots show
 * each; the behaviour checks cover the divider's actions and keys, the Dock press opening the last-used mode and
 * returning Home, and the phone
 * staying a full switch. The shell is a recording lambda.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class SplitScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private lateinit var host: StreamHost

    private fun render(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
        posture: AdaptiveStagePosture = AdaptiveStagePosture.UNFOLDED,
        streamShown: Boolean = true,
        lastShown: SplitMode? = null,
        dockPosition: DockPosition = DockPosition.BOTTOM,
        wideView: StreamWideView = StreamWideView.PAGES,
    ) {
        host = emptyStreamHost()
        if (lastShown != null) {
            host.layouts.update(deviceClass, StreamLayoutAction.SetSplit(0.4f, lastShown))
        }
        host.layouts.update(deviceClass, StreamLayoutAction.SetWideView(wideView))
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(deviceClass, dockPosition),
                    appIconLoader = SolidColorAppIconLoader(),
                    adaptiveStageWindowLayout = screenshotWindowLayout(posture),
                    stream = host,
                    streamShown = streamShown,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun dividerActions() =
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]

    private fun invokeDividerAction(label: String) {
        val action = dividerActions().first { it.label == label }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    private fun dividerState(): String {
        val config = composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().config
        return config[SemanticsProperties.StateDescription]
    }

    private fun paneExists(tag: String): Boolean =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun unfoldedSplit() {
        render()
        composeRule.onNodeWithTag(SPLIT_HOME_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun unfoldedSplitWithASideDock() {
        // Home keeps the Dock's edge; the Stream takes the side away from it.
        render(dockPosition = DockPosition.LEFT)
        val home = composeRule.onNodeWithTag(SPLIT_HOME_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val stream = composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Home is on the Dock's side", home.left < stream.left)
        composeRule.captureScreen()
    }

    @Test
    fun unfoldedStreamOnly() {
        render(lastShown = SplitMode.STREAM_ONLY)
        composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).assertIsDisplayed()
        assertTrue(!paneExists(SPLIT_HOME_PANE_TEST_TAG))
        assertTrue(!paneExists(SPLIT_DIVIDER_TEST_TAG))
        composeRule.captureScreen()
    }

    @Test
    fun unfoldedStreamOnlyAsTwoPages() {
        render(lastShown = SplitMode.STREAM_ONLY)
        composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun unfoldedSplitWithTheStageViewCollapsesItsRailToIcons() {
        // The Stream's pane is 40 percent of 840 dp, under the width where the rail keeps its names.
        render(wideView = StreamWideView.STAGE)
        composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun unfoldedStreamOnlyWithTheStageViewKeepsTheFullRail() {
        render(lastShown = SplitMode.STREAM_ONLY, wideView = StreamWideView.STAGE)
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLETOP_FOLDABLE)
    fun tabletop() {
        render(posture = AdaptiveStagePosture.TABLETOP)
        composeRule.onNodeWithTag(SPLIT_HOME_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).assertIsDisplayed()
        // Stacked: Home above the hinge and the Stream below it, never across it.
        val home = composeRule.onNodeWithTag(SPLIT_HOME_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val stream = composeRule.onNodeWithTag(SPLIT_STREAM_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the Stream is below Home", stream.top >= home.top)
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun tabletLandscapeSplit() {
        render(deviceClass = HomeLayoutDeviceClass.TABLET, posture = AdaptiveStagePosture.UNKNOWN)
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun theDividerHasALabelAValueAndTheThreeActions() {
        render()
        assertEquals(listOf("Home only", "Split", "Stream only"), dividerActions().map { it.label })
        assertEquals("Split, Stream 40 percent", dividerState())
        val config = composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().config
        assertEquals("Divider between Home and Stream", config[SemanticsProperties.ContentDescription].single())
    }

    @Test
    fun theDividerIsA48DpTarget() {
        render()
        val bounds = composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density.density
        assertTrue("target is ${bounds.width / density} dp wide", bounds.width / density >= 48f - 0.5f)
    }

    @Test
    fun theStreamOnlyActionLeavesTheHomePaneAndRemembersTheChoice() {
        render()
        invokeDividerAction("Stream only")
        assertTrue(!paneExists(SPLIT_HOME_PANE_TEST_TAG))
        assertTrue(actions.isEmpty())
        val stored = host.layouts.layoutFor(HomeLayoutDeviceClass.FOLDABLE).split
        assertEquals(SplitMode.STREAM_ONLY, stored.lastShown)
    }

    @Test
    fun theHomeOnlyActionReturnsHome() {
        render()
        invokeDividerAction("Home only")
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun theSplitActionRestoresTheDefaultSplit() {
        render(lastShown = SplitMode.SPLIT)
        invokeDividerAction("Split")
        assertEquals("Split, Stream 40 percent", dividerState())
        assertTrue(paneExists(SPLIT_HOME_PANE_TEST_TAG))
    }

    @Test
    fun anArrowKeyMovesTheDivider() {
        render()
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        // The Stream is on the right: moving the divider left gives it more room.
        assertEquals("Split, Stream 45 percent", dividerState())
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        assertEquals("Split, Stream 40 percent", dividerState())
    }

    @Test
    fun draggingTheDividerMovesItAndRemembersTheFraction() {
        render()
        val before = composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().boundsInRoot.center.x
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(-DRAG_STEP_PX, 0f)) }
            up()
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag(SPLIT_DIVIDER_TEST_TAG).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("the divider did not move left ($before -> $after)", after < before - DRAG_STEP_PX)
        assertTrue(host.layouts.layoutFor(HomeLayoutDeviceClass.FOLDABLE).split.fraction > 0.4f)
    }

    @Test
    fun pressingTheDockFromHomeOpensTheLastUsedMode() {
        render(streamShown = false, lastShown = SplitMode.STREAM_ONLY)
        composeRule.pressDock(distancePx = PRESS_PX)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
        assertEquals(SplitMode.STREAM_ONLY, host.layouts.layoutFor(HomeLayoutDeviceClass.FOLDABLE).split.lastShown)
    }

    @Test
    fun pressingTheDockFromHomeOpensTheSplitWhenThatWasTheLastUsedMode() {
        render(streamShown = false, lastShown = SplitMode.SPLIT)
        composeRule.pressDock(distancePx = PRESS_PX)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
        assertEquals(SplitMode.SPLIT, host.layouts.layoutFor(HomeLayoutDeviceClass.FOLDABLE).split.lastShown)
    }

    @Test
    fun aShortPressFromHomeOpensNothing() {
        render(streamShown = false)
        // Past touch slop (16 dp) so it is a press, short of the 28 dp threshold, on whatever density the device has.
        composeRule.pressDock(distancePx = SHORT_PRESS_DP * composeRule.density.density)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun pressingTheDockReturnsHomeFromTheSplit() {
        render()
        composeRule.pressDock(distancePx = PRESS_PX)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun theDockShowsItsPageTilesBesideTheSplitStream() {
        render()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun aPhoneStaysAFullSwitchWithNoDivider() {
        render(deviceClass = HomeLayoutDeviceClass.PHONE, posture = AdaptiveStagePosture.COMPACT)
        assertTrue(!paneExists(SPLIT_DIVIDER_TEST_TAG))
        assertTrue(!paneExists(SPLIT_HOME_PANE_TEST_TAG))
    }

    private companion object {
        const val PRESS_PX = 150f
        const val SHORT_PRESS_DP = 22f
        const val DRAG_STEPS = 8
        const val DRAG_STEP_PX = 20f
    }
}
