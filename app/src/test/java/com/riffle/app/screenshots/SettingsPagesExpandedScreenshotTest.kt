package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.SettingsPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The top-level pages of Settings on an unfolded foldable (840 dp wide): the page column is capped in width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class SettingsPagesExpandedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun main() = composeRule.renderSettingsPage(SettingsPage.MAIN)

    @Test fun home() = composeRule.renderSettingsPage(SettingsPage.HOME)

    @Test fun dock() = composeRule.renderSettingsPage(SettingsPage.DOCK)

    @Test fun library() = composeRule.renderSettingsPage(SettingsPage.LIBRARY)

    @Test fun stream() = composeRule.renderSettingsPage(SettingsPage.STREAM)

    @Test fun appearance() = composeRule.renderSettingsPage(SettingsPage.APPEARANCE)

    @Test fun gestures() = composeRule.renderSettingsPage(SettingsPage.GESTURES)

    @Test fun motionAndHaptics() = composeRule.renderSettingsPage(SettingsPage.MOTION)

    @Test fun privacyAndAccess() = composeRule.renderSettingsPage(SettingsPage.PRIVACY)

    @Test fun backup() = composeRule.renderSettingsPage(SettingsPage.BACKUP)

    @Test fun about() = composeRule.renderSettingsPage(SettingsPage.VERSION)
}
