package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the long-press behaviour tests share, through the real [HomeDestination] (dock modes plan, "Long-press
 * coverage"): a folded Home with simulated system bars (24 dp and 48 dp), a recording shell that answers nothing, a
 * counting haptics (one tick per menu that opens) and the press helpers. The coverage of every point of the window is
 * [LongPressCoverageBase]'s; the tests here are what each kind of press does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
abstract class LongPressBehaviorBase {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected val haptics = CountingHaptics()
    protected val actions = mutableListOf<LauncherShellAction>()
    protected var shell by mutableStateOf(ScreenshotFixtures.homeState())
    protected val dp get() = composeRule.density.density

    protected fun folded(dock: DockPosition? = DockPosition.BOTTOM) =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, dock).withHomePages(3).withManyApps()

    protected fun show(state: LauncherShellState) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    haptics = haptics,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.setSystemBarInsets(topDp = 24, bottomDp = 48)
    }

    protected fun root() = composeRule.onAllNodes(isRoot()).onFirst()

    protected fun windowDp(): Rect = root().fetchSemanticsNode().boundsInRoot

    protected fun handle(): Rect = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot

    protected fun longPressAt(
        xDp: Float,
        yDp: Float,
    ) = root().performTouchInput { longClick(Offset(xDp * dp, yDp * dp)) }

    protected fun assertHomeMenu() {
        composeRule.onNodeWithText("Edit page").assertIsDisplayed()
        composeRule.onNodeWithText("Manage pages").assertIsDisplayed()
        assertEquals("one light tick as it opens", 1, haptics.count)
        assertTrue("a long-press chooses nothing: $actions", actions.isEmpty())
    }
}
