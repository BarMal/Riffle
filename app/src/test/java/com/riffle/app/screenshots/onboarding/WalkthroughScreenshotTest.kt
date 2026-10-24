package com.riffle.app.screenshots.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.onboarding.ApplyBlock
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every step of the first-time walkthrough on a compact phone, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WalkthroughScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun walkthroughWelcome() = shot(WalkthroughStep.WELCOME)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughWelcomeDark() = shot(WalkthroughStep.WELCOME)

    @Test
    fun walkthroughGoals() = shot(WalkthroughStep.GOALS)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughGoalsDark() = shot(WalkthroughStep.GOALS)

    @Test
    fun walkthroughArrange() = shot(WalkthroughStep.ARRANGE)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughArrangeDark() = shot(WalkthroughStep.ARRANGE)

    @Test
    fun walkthroughOptions() = shot(WalkthroughStep.OPTIONS)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughOptionsDark() = shot(WalkthroughStep.OPTIONS)

    @Test
    fun walkthroughDock() = shot(WalkthroughStep.DOCK)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughDockDark() = shot(WalkthroughStep.DOCK)

    @Test
    fun walkthroughPermissions() = shot(WalkthroughStep.PERMISSIONS)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughPermissionsDark() = shot(WalkthroughStep.PERMISSIONS)

    @Test
    fun walkthroughReview() = shot(WalkthroughStep.REVIEW)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun walkthroughReviewDark() = shot(WalkthroughStep.REVIEW)

    @Test
    fun walkthroughReviewWhileLoading() = shot(WalkthroughStep.REVIEW, ApplyBlock.LOADING)

    @Test
    fun walkthroughReviewReadOnlyHome() = shot(WalkthroughStep.REVIEW, ApplyBlock.READ_ONLY)

    @Test
    fun offerCard() = composeRule.renderOffer()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun offerCardDark() = composeRule.renderOffer()

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun walkthroughGoalsLargeFont() = shot(WalkthroughStep.GOALS)

    private fun shot(
        step: WalkthroughStep,
        block: ApplyBlock? = null,
    ) = composeRule.renderWalkthrough(WalkthroughScreenshotStates.model(step, block))
}
