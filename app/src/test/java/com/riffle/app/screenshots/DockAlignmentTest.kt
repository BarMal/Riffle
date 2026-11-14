package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Dock > "Dock alignment" reaches every Dock edge. Along the bottom it places the Dock left, middle or
 * right; down a side (the unfolded default) it was ignored, and now places it at the top, the middle or the bottom of
 * the edge. The real [HomeDestination] is drawn with a Dock that does not fill its edge, and the Dock's bounds are
 * read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class DockAlignmentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun dockAt(
        edge: DockPosition,
        alignment: DockAlignment,
    ): Pair<Rect, Rect> {
        val deviceClass = HomeLayoutDeviceClass.FOLDABLE
        val base = ScreenshotFixtures.homeState(deviceClass, edge).withHomePages(2)
        val layout = base.homeLayout.copy(dock = base.homeLayout.dock.copy(alignment = alignment))
        val shell: LauncherShellState =
            base.copy(
                homeLayout = layout,
                homeLayoutSet = base.homeLayoutSet.copy(layouts = mapOf(base.homeLayoutSet.activeKey to layout)),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    adaptiveStageWindowLayout = screenshotWindowLayout(AdaptiveStagePosture.UNFOLDED),
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
        val dock = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return dock to root
    }

    @Test
    fun aRightDockStartsAtTheTopOfItsEdge() {
        val (dock, root) = dockAt(DockPosition.RIGHT, DockAlignment.START)

        assertTrue("top: ${dock.top} of ${root.height}", dock.top < root.height * 0.25f)
    }

    @Test
    fun aRightDockCentredIsInTheMiddleOfItsEdge() {
        val (dock, root) = dockAt(DockPosition.RIGHT, DockAlignment.CENTER)

        assertEquals(root.height / 2f, dock.center.y, root.height * 0.05f)
    }

    @Test
    fun aRightDockEndsAtTheBottomOfItsEdge() {
        val (dock, root) = dockAt(DockPosition.RIGHT, DockAlignment.END)

        assertTrue("bottom: ${dock.bottom} of ${root.height}", dock.bottom > root.height * 0.75f)
    }

    @Test
    fun aLeftDockFollowsTheSameAlignment() {
        val start = dockAt(DockPosition.LEFT, DockAlignment.START)

        assertTrue(start.first.top < start.second.height * 0.25f)
    }

    @Test
    fun aBottomDockStartsAtTheLeftOfItsEdge() {
        // The Dock must not fill its edge for alignment to show: it is content-sized by default.
        val start = dockAt(DockPosition.BOTTOM, DockAlignment.START)
        assertTrue("left: ${start.first.left} of ${start.second.width}", start.first.left < start.second.width * 0.25f)
    }
}
