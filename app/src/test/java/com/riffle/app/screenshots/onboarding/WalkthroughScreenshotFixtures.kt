@file:Suppress("MatchingDeclarationName") // The states and the two render helpers of the walkthrough screenshots.

package com.riffle.app.screenshots.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.onboarding.ApplyBlock
import com.riffle.app.launcher.onboarding.OnboardingOfferCard
import com.riffle.app.launcher.onboarding.WalkthroughContexts
import com.riffle.app.launcher.onboarding.WalkthroughFixtures
import com.riffle.app.launcher.onboarding.WalkthroughScreen
import com.riffle.app.launcher.onboarding.WalkthroughScreenModel
import com.riffle.app.launcher.onboarding.WalkthroughSession
import com.riffle.app.launcher.onboarding.WalkthroughUi
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep

/** Each walkthrough step as a fresh install sees it (nothing granted yet), driven through the real session. */
internal object WalkthroughScreenshotStates {
    private val installed = WalkthroughFixtures.installed("phone", "mail", "camera", "maps", "music", "notes")

    private fun session() =
        WalkthroughSession(
            contextOf = {
                WalkthroughFixtures.context(access = WalkthroughFixtures.NOTHING_GRANTED)
            },
            launchPermission = {},
        )

    fun at(step: WalkthroughStep): WalkthroughUi {
        val session = session()
        repeat(MAX_STEPS) {
            if (session.ui.value.state.step != step) session.dispatch(WalkthroughAction.Next)
        }
        if (step == WalkthroughStep.PERMISSIONS) {
            // The calendar's rationale is open; notifications are still to decide.
            session.dispatch(WalkthroughAction.ExplainPermission(SourcePermission.CALENDAR))
        }
        check(session.ui.value.state.step == step) { "Did not reach $step" }
        return session.ui.value
    }

    fun model(
        step: WalkthroughStep,
        applyBlock: ApplyBlock? = null,
    ) = WalkthroughScreenModel(
        ui = at(step),
        apps = WalkthroughContexts.pickableApps(installed),
        applyBlock = applyBlock,
        reducedMotion = true,
    )

    private const val MAX_STEPS = 30
}

internal fun ComposeContentTestRule.renderWalkthrough(model: WalkthroughScreenModel) {
    setContent { ScreenshotBackdrop { WalkthroughScreen(model = model, onAction = {}) } }
    captureScreen()
}

internal fun ComposeContentTestRule.renderOffer() {
    setContent {
        ScreenshotBackdrop {
            OnboardingOfferCard(onAccept = {}, onDismiss = {}, modifier = Modifier.padding(16.dp))
        }
    }
    captureScreen()
}
