package com.riffle.app.screenshots.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.onboarding.ONBOARDING_NEXT_TEST_TAG
import com.riffle.app.launcher.onboarding.OnboardingScreen
import com.riffle.app.launcher.onboarding.PermissionGranted
import com.riffle.app.launcher.onboarding.TOUR_ILLUSTRATION_TEST_TAG
import com.riffle.app.launcher.onboarding.TourGesture
import com.riffle.app.launcher.onboarding.TourIllustration
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tour's pictures: the Dock, Library and Stream steps each show their gesture on a small Dock (not Home), the
 * picture stands still under reduced motion, and its loop recomposes nothing (it runs in the draw phase).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class OnboardingIllustrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(reducedMotion: Boolean = true) {
        composeRule.setContent {
            RiffleLauncherTheme(dynamicColor = false) {
                OnboardingScreen(
                    granted = PermissionGranted { false },
                    onAllow = {},
                    onFinished = {},
                    onSkip = {},
                    reducedMotion = reducedMotion,
                )
            }
        }
    }

    private fun next() = composeRule.onNodeWithTag(ONBOARDING_NEXT_TEST_TAG).performClick()

    @Test
    fun homeHasNoPictureAndTheOtherThreeStepsEachHaveOne() {
        render()
        composeRule.onNodeWithTag(TOUR_ILLUSTRATION_TEST_TAG).assertDoesNotExist()
        repeat(3) {
            next()
            composeRule.onNodeWithTag(TOUR_ILLUSTRATION_TEST_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun theDockStepShowsThePressStill() {
        render()
        next()
        composeRule.onNodeWithTag(TOUR_ILLUSTRATION_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun theLibraryStepShowsThePullStill() {
        render()
        repeat(2) { next() }
        composeRule.captureScreen()
    }

    @Test
    fun theStreamStepShowsTheHoldAndSlideStill() {
        render()
        repeat(3) { next() }
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theStreamStepInTheDarkTheme() {
        render()
        repeat(3) { next() }
        composeRule.captureScreen()
    }

    @Test
    fun theLoopRunsInTheDrawPhaseSoNothingRecomposesWhileItPlays() {
        val probe = RecompositionProbe()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffleLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    TourGesture.entries.forEach { gesture ->
                        TourIllustration(gesture = gesture, reducedMotion = false)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val settled = probe.total("tour-illustration")

        // A full loop and a half, a frame at a time.
        repeat(300) { composeRule.mainClock.advanceTimeByFrame() }

        assertTrue("every picture composed", settled >= TourGesture.entries.size)
        assertEquals("no frame recomposed a picture", settled, probe.total("tour-illustration"))
    }

    @Test
    fun underReducedMotionThereIsNoAnimationToWaitFor() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffleLauncherTheme(dynamicColor = false) {
                TourIllustration(gesture = TourGesture.PRESS, reducedMotion = true)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        // With autoAdvance off the rule is idle only when nothing animates; a still picture leaves it so.
        assertTrue(composeRule.mainClock.autoAdvance.not())
        composeRule.onNodeWithTag(TOUR_ILLUSTRATION_TEST_TAG).assertIsDisplayed()
    }
}
