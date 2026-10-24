package com.riffle.app.screenshots.wizard

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.app.launcher.wizard.WIZARD_NEXT_TAG
import com.riffle.app.launcher.wizard.WIZARD_NOTICE_TAG
import com.riffle.app.launcher.wizard.WIZARD_PREVIEW_TAG
import com.riffle.app.launcher.wizard.wizardGoalTag
import com.riffle.app.launcher.wizard.wizardReviewAccessTag
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The content wizard's steps on a compact phone, light and dark: Source (with a goal that needs access), Content,
 * Look (the live preview above the looks), Place and Save, plus a refused finish and large text. The unfolded
 * two-pane layout is [WizardUnfoldedScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WizardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceStep() {
        composeRule.renderWizard(WizardScreenshotFixtures.source())
        // Nothing chosen yet: Next waits, and a goal that needs access is still choosable.
        composeRule.onNodeWithTag(WIZARD_NEXT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(wizardGoalTag(ContentGoalId.CALENDAR.key)).assertIsEnabled()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun sourceStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.source())

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun sourceStepLargeFont() = composeRule.renderWizard(WizardScreenshotFixtures.source())

    @Test
    fun sourceNeedsAccess() {
        composeRule.renderWizard(WizardScreenshotFixtures.needsAccess())
        composeRule.onNodeWithTag(wizardGoalTag(ContentGoalId.CALENDAR.key)).assertIsSelected()
        composeRule.onNodeWithTag(wizardReviewAccessTag(SourcePermission.CALENDAR)).assertExists()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun sourceNeedsAccessDark() = composeRule.renderWizard(WizardScreenshotFixtures.needsAccess())

    @Test
    fun contentStep() = composeRule.renderWizard(WizardScreenshotFixtures.content())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun contentStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.content())

    @Test
    fun lookStep() {
        composeRule.renderWizard(WizardScreenshotFixtures.look())
        composeRule.onNodeWithTag(WIZARD_PREVIEW_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun lookStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.look())

    @Test
    fun placeStep() = composeRule.renderWizard(WizardScreenshotFixtures.place())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun placeStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.place())

    @Test
    fun saveStep() {
        composeRule.renderWizard(WizardScreenshotFixtures.save())
        composeRule.onNodeWithText("Add").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun saveStepDark() = composeRule.renderWizard(WizardScreenshotFixtures.save())

    @Test
    fun useThisSourceStartsOnContent() =
        composeRule.renderWizard(
            WizardScreenshotFixtures.at(WizardScreenshotFixtures.fromSource),
            WizardScreenshotFixtures.fromSource,
        )

    @Test
    fun refusedFinish() {
        composeRule.renderWizard(WizardScreenshotFixtures.refused())
        composeRule.onNodeWithTag(WIZARD_NOTICE_TAG).assertExists()
        composeRule.onNodeWithText(READ_ONLY_HOME_NOTICE).assertExists()
    }
}
