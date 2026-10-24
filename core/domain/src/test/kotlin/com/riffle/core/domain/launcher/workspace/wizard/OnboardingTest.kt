package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingTest {
    @Test
    fun aFreshInstallRunsTheWalkthroughUntilItIsFinishedOrSkipped() {
        val (decided, prompt) = Onboarding.atLaunch(OnboardingState(), freshInstall = true)
        assertEquals(OnboardingOrigin.FRESH_INSTALL, decided.origin)
        assertEquals(OnboardingPrompt.RUN_WALKTHROUGH, prompt)
        // Closed mid-way (process death): asked again.
        val started = Onboarding.started(decided)
        assertEquals(OnboardingPrompt.RUN_WALKTHROUGH, Onboarding.atLaunch(started, freshInstall = false).second)
        assertEquals(
            OnboardingPrompt.NONE,
            Onboarding.atLaunch(Onboarding.completed(started), freshInstall = true).second,
        )
        assertEquals(
            OnboardingPrompt.NONE,
            Onboarding.atLaunch(Onboarding.skipped(started), freshInstall = true).second,
        )
    }

    @Test
    fun anExistingUserIsOfferedSetUpOnce() {
        val (shown, prompt) = Onboarding.atLaunch(OnboardingState(), freshInstall = false)
        assertEquals(OnboardingOrigin.EXISTING_USER, shown.origin)
        assertEquals(OnboardingPrompt.SHOW_OFFER, prompt)
        assertEquals(OfferProgress.SHOWN, shown.offer)
        // Ignored: never shown again.
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(shown, freshInstall = false).second)
        // Dismissed: never shown again, never runs by itself.
        val dismissed = Onboarding.offerDismissed(shown)
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(dismissed, freshInstall = true).second)
        // Accepted: the walkthrough is started; an interrupted run is not offered again either.
        val accepted = Onboarding.offerAccepted(shown)
        assertEquals(WalkthroughProgress.STARTED, accepted.walkthrough)
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(accepted, freshInstall = false).second)
    }

    @Test
    fun theOriginIsDecidedOnce() {
        val existing = Onboarding.atLaunch(OnboardingState(), freshInstall = false).first
        // A later start that looks fresh (a cleared workspace store) does not make it a fresh install.
        assertEquals(OnboardingOrigin.EXISTING_USER, Onboarding.atLaunch(existing, freshInstall = true).first.origin)
    }

    @Test
    fun itCanAlwaysBeRerunFromSettings() {
        val done = Onboarding.completed(Onboarding.started(OnboardingState(OnboardingOrigin.FRESH_INSTALL)))
        assertEquals(1, done.completedRuns)
        val rerun = Onboarding.rerun(done)
        // A cancelled re-run keeps the earlier completion.
        assertEquals(WalkthroughProgress.COMPLETED, Onboarding.skipped(rerun).walkthrough)
        assertEquals(2, Onboarding.completed(rerun).completedRuns)
        val skipped = Onboarding.skipped(OnboardingState(OnboardingOrigin.FRESH_INSTALL))
        assertEquals(WalkthroughProgress.STARTED, Onboarding.rerun(skipped).walkthrough)
        // Completing settles an offer that was never shown.
        assertEquals(OfferProgress.DISMISSED, done.offer)
    }

    @Test
    fun theCodecRoundTripsAndDecodesSafely() {
        val state =
            OnboardingState(OnboardingOrigin.EXISTING_USER, WalkthroughProgress.COMPLETED, OfferProgress.ACCEPTED, 3)
        assertEquals(state, OnboardingCodec.decode(OnboardingCodec.encode(state)))
        assertEquals(OnboardingState(), OnboardingCodec.decode(OnboardingCodec.encode(OnboardingState())))
        assertEquals(OnboardingState(), OnboardingCodec.decode(null))
        assertEquals(OnboardingState(), OnboardingCodec.decode(StoredValue.Str("garbage")))
        val odd =
            StoredValue.Obj(
                mapOf(
                    "version" to StoredValue.Num(99),
                    "origin" to StoredValue.Str("SOMEWHERE"),
                    "walkthrough" to StoredValue.Num(1),
                    "offer" to StoredValue.Str("DISMISSED"),
                    "completedRuns" to StoredValue.Num(-4),
                    "future" to StoredValue.Bool(true),
                ),
            )
        assertEquals(OnboardingState(offer = OfferProgress.DISMISSED), OnboardingCodec.decode(odd))
        val legacy = StoredValue.Obj(mapOf("completed" to StoredValue.Bool(true)))
        assertEquals(WalkthroughProgress.COMPLETED, OnboardingCodec.decode(legacy).walkthrough)
    }
}
