package com.riffle.app.screenshots

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Gestures: the one-finger swipes that Home's own pages take (sideways pages: left and right; vertical
 * pages: up and down) say so under the binding; the multi-finger rows say nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GesturePagerHintTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theFourOneFingerRowsCarryTheHintAndNoOtherRowDoes() {
        val state = ScreenshotFixtures.homeState().settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop { SettingsSurface(state = state, initialPage = SettingsPage.GESTURES, onAction = {}) }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Home's pages use this swipe", substring = true).assertCountEquals(4)
        composeRule.onAllNodesWithText("they scroll sideways", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("they scroll up and down", substring = true).assertCountEquals(2)
    }
}
