package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.workspace.wizard.OfferProgress
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingPrompt
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launch decision over the device-local store: one controller is one process. */
class OnboardingControllerTest {
    private val store = InMemoryOnboardingStore()

    private fun process() = OnboardingController(store, Dispatchers.Unconfined)

    private fun OnboardingController.launch(fresh: Boolean): OnboardingPrompt = runBlocking { decideAtLaunch { fresh } }

    @Test
    fun aFreshInstallRunsTheWalkthroughFullScreen() {
        val controller = process()
        assertEquals(OnboardingPrompt.RUN_WALKTHROUGH, controller.launch(fresh = true))
        assertEquals(
            OnboardingSurface.Walkthrough(OnboardingOrigin.FRESH_INSTALL, firstRun = true),
            controller.surface.value,
        )
        assertEquals(OnboardingOrigin.FRESH_INSTALL, store.stored?.origin)
        assertEquals(WalkthroughProgress.STARTED, store.stored?.walkthrough)
    }

    @Test
    fun theDecisionIsMadeOncePerProcess() {
        val controller = process()
        controller.launch(fresh = true)
        controller.walkthroughCancelled()
        assertEquals(OnboardingPrompt.NONE, controller.launch(fresh = true))
        assertEquals(OnboardingSurface.None, controller.surface.value)
    }

    @Test
    fun anInterruptedFreshRunComesBackAtTheNextLaunch() {
        process().launch(fresh = true)
        // The app was closed mid-way: the next process runs it again.
        val next = process()
        assertEquals(OnboardingPrompt.RUN_WALKTHROUGH, next.launch(fresh = false))
        assertEquals(OnboardingOrigin.FRESH_INSTALL, (next.surface.value as OnboardingSurface.Walkthrough).origin)
    }

    @Test
    fun aCompletedOrSkippedRunIsNotAskedAgain() {
        process().apply { launch(fresh = true) }.walkthroughCompleted()
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = true))
        assertEquals(1, store.stored?.completedRuns)

        val skipped = InMemoryOnboardingStore()
        OnboardingController(skipped, Dispatchers.Unconfined).apply { runBlocking { decideAtLaunch { true } } }
            .walkthroughCancelled()
        assertEquals(WalkthroughProgress.SKIPPED, skipped.stored?.walkthrough)
        assertEquals(
            OnboardingPrompt.NONE,
            runBlocking { OnboardingController(skipped, Dispatchers.Unconfined).decideAtLaunch { true } },
        )
    }

    @Test
    fun anExistingUserGetsTheOfferOnce() {
        val first = process()
        assertEquals(OnboardingPrompt.SHOW_OFFER, first.launch(fresh = false))
        assertEquals(OnboardingSurface.Offer, first.surface.value)
        assertEquals(OfferProgress.SHOWN, store.stored?.offer)
        // Whatever the user did with it (here: nothing), it never comes back.
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = false))
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = true))
    }

    @Test
    fun notNowDismissesTheOfferForGood() {
        val controller = process()
        controller.launch(fresh = false)
        controller.dismissOffer()
        assertEquals(OnboardingSurface.None, controller.surface.value)
        assertEquals(OfferProgress.DISMISSED, store.stored?.offer)
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = false))
    }

    @Test
    fun setUpOpensTheWalkthroughThatOnlyAdds() {
        val controller = process()
        controller.launch(fresh = false)
        controller.acceptOffer()
        assertEquals(
            OnboardingSurface.Walkthrough(OnboardingOrigin.EXISTING_USER, firstRun = true),
            controller.surface.value,
        )
        assertEquals(OfferProgress.ACCEPTED, store.stored?.offer)
        controller.walkthroughCompleted()
        assertEquals(WalkthroughProgress.COMPLETED, store.stored?.walkthrough)
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = false))
    }

    @Test
    fun settingsCanRunItAgainAnyTimeAndARerunNeverReplaces() {
        val controller = process()
        controller.launch(fresh = true)
        controller.walkthroughCompleted()
        controller.rerun()
        assertEquals(
            OnboardingSurface.Walkthrough(OnboardingOrigin.FRESH_INSTALL, firstRun = false),
            controller.surface.value,
        )
        // Cancelling a re-run keeps the earlier completion.
        controller.walkthroughCancelled()
        assertEquals(WalkthroughProgress.COMPLETED, store.stored?.walkthrough)
        controller.rerun()
        controller.walkthroughCompleted()
        assertEquals(2, store.stored?.completedRuns)
    }

    @Test
    fun aRerunBeforeTheLaunchDecisionWins() {
        val controller = process()
        controller.rerun()
        controller.launch(fresh = false)
        // The walkthrough from Settings stays up; the offer is not drawn over it.
        assertTrue(controller.surface.value is OnboardingSurface.Walkthrough)
    }

    @Test
    fun freshnessIsOnlyAskedWhileTheOriginIsUndecided() {
        var asked = 0
        runBlocking { process().decideAtLaunch { asked++ == 0 } }
        runBlocking { process().decideAtLaunch { asked++ == 0 } }
        assertEquals(1, asked)
        assertEquals(OnboardingOrigin.FRESH_INSTALL, store.stored?.origin)
    }

    @Test
    fun nothingIsWrittenWhenNothingChanged() {
        process().launch(fresh = false)
        val writes = store.writes
        process().launch(fresh = false)
        assertEquals(writes, store.writes)
        assertNull(process().current)
    }
}
