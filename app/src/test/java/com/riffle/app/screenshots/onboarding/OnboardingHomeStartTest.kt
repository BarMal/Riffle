package com.riffle.app.screenshots.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.onboarding.ONBOARDING_NEXT_TEST_TAG
import com.riffle.app.launcher.onboarding.ONBOARDING_SKIP_TEST_TAG
import com.riffle.app.launcher.onboarding.OnboardingScreen
import com.riffle.app.launcher.onboarding.PermissionGranted
import com.riffle.app.launcher.onboarding.homeStartTestTag
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.home.HomeStart
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "How should Home start?": the first step of a fresh install's set-up, with two previews to choose between. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class OnboardingHomeStartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<HomeStart>()
    private var skipped = 0

    private fun render(capture: Boolean = false) {
        composeRule.setContent {
            RiffleLauncherTheme(dynamicColor = false) {
                OnboardingScreen(
                    granted = PermissionGranted { false },
                    onAllow = {},
                    onFinished = {},
                    onSkip = { skipped++ },
                    askHomeStart = true,
                    onHomeStart = { chosen += it },
                )
            }
        }
        if (capture) composeRule.captureScreen() else composeRule.waitForIdle()
    }

    @Test
    fun theQuestionComesFirstWithAllMyAppsPreselectedAndSkipSayingWhatItDoes() {
        render(capture = true)

        composeRule.onNodeWithText("Step 1 of 5").assertIsDisplayed()
        composeRule.onNodeWithText("How should Home start?").assertIsDisplayed()
        composeRule.onNodeWithTag(homeStartTestTag(HomeStart.ALL_APPS)).assertIsSelected()
        composeRule.onNodeWithText("Recommended").assertIsDisplayed()
        composeRule.onNodeWithText("Skip: start with all apps on Home").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theQuestionInTheDarkTheme() {
        render(capture = true)

        composeRule.onNodeWithText("Start empty").assertIsDisplayed()
    }

    @Test
    fun nextAppliesTheChoiceAndTheTourFollowsWithHomeAsStepTwoOfFive() {
        render()

        composeRule.onNodeWithTag(homeStartTestTag(HomeStart.EMPTY)).performClick()
        composeRule.onNodeWithTag(homeStartTestTag(HomeStart.EMPTY)).assertIsSelected()
        composeRule.onNodeWithTag(ONBOARDING_NEXT_TEST_TAG).performClick()

        assertEquals(listOf(HomeStart.EMPTY), chosen)
        composeRule.onNodeWithText("Step 2 of 5").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun theDefaultChoiceIsAllMyApps() {
        render()

        composeRule.onNodeWithTag(ONBOARDING_NEXT_TEST_TAG).performClick()

        assertEquals(listOf(HomeStart.ALL_APPS), chosen)
    }

    @Test
    fun skipLeavesWithoutChoosingHere() {
        render()

        composeRule.onNodeWithTag(ONBOARDING_SKIP_TEST_TAG).performClick()

        // The set-up's host seeds Home with all apps on skip; the screen only reports the skip.
        assertEquals(1, skipped)
        assertEquals(emptyList<HomeStart>(), chosen)
    }
}
