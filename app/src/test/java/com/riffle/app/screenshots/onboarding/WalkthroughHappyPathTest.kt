package com.riffle.app.screenshots.onboarding

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.onboarding.FakeWalkthroughStores
import com.riffle.app.launcher.onboarding.OnboardingOfferCard
import com.riffle.app.launcher.onboarding.WALKTHROUGH_PRIMARY_TEST_TAG
import com.riffle.app.launcher.onboarding.WalkthroughApplier
import com.riffle.app.launcher.onboarding.WalkthroughContexts
import com.riffle.app.launcher.onboarding.WalkthroughFixtures
import com.riffle.app.launcher.onboarding.WalkthroughOutcome
import com.riffle.app.launcher.onboarding.WalkthroughScreen
import com.riffle.app.launcher.onboarding.WalkthroughScreenModel
import com.riffle.app.launcher.onboarding.WalkthroughSession
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughExit
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The walkthrough driven through its screen: get started, pick, skip access, build, and the exits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WalkthroughHappyPathTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val launched = mutableListOf<SourcePermission>()
    private val session =
        WalkthroughSession(
            contextOf = { WalkthroughFixtures.context(access = WalkthroughFixtures.NOTHING_GRANTED) },
            launchPermission = { launched += it },
        )
    private val apps = WalkthroughContexts.pickableApps(WalkthroughFixtures.installed("phone", "mail", "camera"))

    private fun render() {
        composeRule.setContent {
            val ui by session.ui.collectAsState()
            ScreenshotBackdrop {
                WalkthroughScreen(
                    model = WalkthroughScreenModel(ui, apps, reducedMotion = true),
                    onAction = session::dispatch,
                    onCheckAgain = session::checkPermissionAgain,
                )
            }
        }
    }

    private fun primary() = composeRule.onNodeWithTag(WALKTHROUGH_PRIMARY_TEST_TAG).performClick()

    private val step get() = session.ui.value.state.step

    @Test
    fun aFreshInstallWalksThroughSkipsAccessAndBuilds() {
        render()
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
        primary()
        composeRule.waitForIdle()
        // The goals step: multi-select cards with their state.
        composeRule.onNodeWithText("My apps").assertIsOn()
        composeRule.onNodeWithText("Quick actions").assertIsOff().performClick()
        composeRule.onNodeWithText("Quick actions").assertIsOn().performClick()
        composeRule.onNodeWithText("Quick actions").assertIsOff()
        repeat(MAX_STEPS) {
            if (step != WalkthroughStep.PERMISSIONS) {
                primary()
                composeRule.waitForIdle()
            }
        }
        assertEquals(WalkthroughStep.PERMISSIONS, step)
        // Explain, then decide not to: nothing is ever requested.
        composeRule.onAllNodesWithText("Allow")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onAllNodesWithText("Not now")[0].performClick()
        composeRule.waitForIdle()
        primary()
        composeRule.waitForIdle()
        assertEquals(WalkthroughStep.REVIEW, step)
        composeRule.onNodeWithText("Build my home").assertIsDisplayed()
        primary()
        composeRule.waitForIdle()
        assertTrue(launched.isEmpty())

        val ui = session.ui.value
        assertEquals(WalkthroughExit.Finished, ui.state.exit)
        val stores = FakeWalkthroughStores()
        val outcome = WalkthroughApplier(stores).apply(ui.state, ui.context, WalkthroughFixtures.PHONE)
        val applied = outcome as WalkthroughOutcome.Applied
        assertEquals(WalkthroughFlow.WORKSPACE_ID, stores.set!!.workspacesFor(WalkthroughFixtures.PHONE).activeId)
        assertTrue(WalkthroughApplier(stores).undo(applied))
        assertEquals(WalkthroughFixtures.freshSet(), stores.set)
    }

    @Test
    fun skipSetUpLeavesFromAnyStep() {
        render()
        primary()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip set-up").performClick()
        composeRule.waitForIdle()
        assertEquals(WalkthroughExit.Cancelled, session.ui.value.state.exit)
    }

    @Test
    fun backReturnsAStepAndContinueOpensTheExistingFlow() {
        render()
        primary()
        composeRule.waitForIdle()
        primary()
        composeRule.waitForIdle()
        assertEquals(WalkthroughStep.ARRANGE, step)
        composeRule.onNodeWithText("Back").performClick()
        composeRule.waitForIdle()
        assertEquals(WalkthroughStep.GOALS, step)
        repeat(MAX_STEPS) {
            if (step != WalkthroughStep.PERMISSIONS) {
                primary()
                composeRule.waitForIdle()
            }
        }
        composeRule.onAllNodesWithText("Allow")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(SourcePermission.NOTIFICATION_ACCESS), launched)
        composeRule.onNodeWithText("Check again").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Not allowed").assertIsDisplayed()
    }

    @Test
    fun theOfferCardsButtonsAccept() {
        val taps = mutableListOf<String>()
        composeRule.setContent {
            ScreenshotBackdrop {
                OnboardingOfferCard(onAccept = { taps += "accept" }, onDismiss = { taps += "dismiss" })
            }
        }
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Set up").performClick()
        assertEquals(listOf("dismiss", "accept"), taps)
    }

    private companion object {
        const val MAX_STEPS = 30
    }
}
