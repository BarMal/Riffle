package com.riffle.app.screenshots.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.onboarding.ONBOARDING_NEXT_TEST_TAG
import com.riffle.app.launcher.onboarding.ONBOARDING_SKIP_TEST_TAG
import com.riffle.app.launcher.onboarding.OnboardingScreen
import com.riffle.app.launcher.onboarding.PermissionGranted
import com.riffle.app.launcher.onboarding.onboardingPermissionTestTag
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.onboarding.SourcePermission
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The four-step set-up: one step per Place, permissions only in the Stream step, each after an explanation and each
 * skippable. Nothing is requested until an Allow button is tapped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val allowed = mutableListOf<SourcePermission>()
    private var finished = 0
    private var skipped = 0

    private fun render(granted: Set<SourcePermission> = emptySet()) {
        composeRule.setContent {
            RiffleLauncherTheme(dynamicColor = false) {
                OnboardingScreen(
                    granted = PermissionGranted { it in granted },
                    onAllow = { allowed += it },
                    onFinished = { finished++ },
                    onSkip = { skipped++ },
                )
            }
        }
    }

    private fun next() = composeRule.onNodeWithTag(ONBOARDING_NEXT_TEST_TAG).performClick()

    @Test
    fun theStepsRunHomeDockLibraryStreamAndDoneFinishes() {
        render()

        listOf("Home", "The Dock", "The Library", "The Stream").forEachIndexed { index, title ->
            composeRule.onNodeWithText("Step ${index + 1} of 4").assertIsDisplayed()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            if (index < 3) next()
        }
        assertEquals(0, finished)
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun noPermissionIsAskedBeforeTheStreamStep() {
        render()

        repeat(3) {
            composeRule.onNodeWithTag(onboardingPermissionTestTag(SourcePermission.NOTIFICATION_ACCESS))
                .assertDoesNotExist()
            next()
        }
        assertEquals(emptyList<SourcePermission>(), allowed)
    }

    @Test
    fun theStreamStepExplainsEachPermissionAndAllowOnlyReportsTheTappedOne() {
        render()
        repeat(3) { next() }

        SourcePermission.entries.forEach { permission ->
            composeRule.onNodeWithTag(onboardingPermissionTestTag(permission)).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Riffle reads your calendar events", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(onboardingPermissionTestTag(SourcePermission.CALENDAR)).performClick()

        assertEquals(listOf(SourcePermission.CALENDAR), allowed)
    }

    @Test
    fun aPermissionCanBeSkippedAndSaysItsSectionShowsAllowAccess() {
        render()
        repeat(3) { next() }

        composeRule.onNodeWithTag(onboardingPermissionTestTag(SourcePermission.USAGE_ACCESS)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Not now")[0].performClick()

        composeRule.onNodeWithText("Skipped", substring = true).assertIsDisplayed()
        assertEquals(emptyList<SourcePermission>(), allowed)
        // Skipping a permission never blocks finishing.
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun aGrantedPermissionShowsAllowedAndNoButton() {
        render(granted = setOf(SourcePermission.NOTIFICATION_ACCESS))
        repeat(3) { next() }

        composeRule.onNodeWithText("Allowed").assertIsDisplayed()
        composeRule.onNodeWithTag(onboardingPermissionTestTag(SourcePermission.NOTIFICATION_ACCESS))
            .assertDoesNotExist()
    }

    @Test
    fun skipSetUpLeavesWithoutFinishing() {
        render()

        composeRule.onNodeWithTag(ONBOARDING_SKIP_TEST_TAG).performClick()

        assertEquals(1, skipped)
        assertEquals(0, finished)
    }
}
