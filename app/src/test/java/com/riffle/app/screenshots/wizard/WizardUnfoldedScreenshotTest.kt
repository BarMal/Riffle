package com.riffle.app.screenshots.wizard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.wizard.WIZARD_PREVIEW_TAG
import com.riffle.app.screenshots.ScreenshotDevices
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The content wizard on an unfolded foldable, light and dark: at 600 dp and wider the live preview sits beside the
 * steps on every step once a goal is chosen (as in the editor).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class WizardUnfoldedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceStep() = composeRule.renderWizard(WizardScreenshotFixtures.source())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun sourceStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.source())

    @Test
    fun sourceNeedsAccess() = composeRule.renderWizard(WizardScreenshotFixtures.needsAccess())

    @Test
    fun contentStep() {
        composeRule.renderWizard(WizardScreenshotFixtures.content())
        composeRule.onNodeWithTag(WIZARD_PREVIEW_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun contentStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.content())

    @Test
    fun lookStep() = composeRule.renderWizard(WizardScreenshotFixtures.look())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun lookStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.look())

    @Test
    fun placeStep() = composeRule.renderWizard(WizardScreenshotFixtures.place())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun placeStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.place())

    @Test
    fun saveStep() = composeRule.renderWizard(WizardScreenshotFixtures.save())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun saveStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.save())
}
