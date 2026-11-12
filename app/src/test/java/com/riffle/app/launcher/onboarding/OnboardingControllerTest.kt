package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.onboarding.OnboardingOrigin
import com.riffle.core.domain.launcher.onboarding.OnboardingProgress
import com.riffle.core.domain.launcher.onboarding.OnboardingPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The launch decision over the device-local store: one controller is one process. */
class OnboardingControllerTest {
    private val store = InMemoryOnboardingStore()

    private fun process() = OnboardingController(store, Dispatchers.Unconfined)

    private fun OnboardingController.launch(fresh: Boolean): OnboardingPrompt = runBlocking { decideAtLaunch { fresh } }

    @Test
    fun aFreshInstallRunsTheSetUpFullScreen() {
        val controller = process()
        assertEquals(OnboardingPrompt.RUN, controller.launch(fresh = true))
        assertEquals(OnboardingSurface.SetUp, controller.surface.value)
        assertEquals(OnboardingOrigin.FRESH_INSTALL, store.stored?.origin)
        assertEquals(OnboardingProgress.STARTED, store.stored?.progress)
    }

    @Test
    fun anExistingUserGetsNothingForced() {
        val controller = process()
        assertEquals(OnboardingPrompt.NONE, controller.launch(fresh = false))
        assertEquals(OnboardingSurface.None, controller.surface.value)
        assertEquals(OnboardingOrigin.EXISTING_USER, store.stored?.origin)
        assertEquals(OnboardingProgress.NOT_STARTED, store.stored?.progress)
        // Not at a later launch either.
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = true))
    }

    @Test
    fun theDecisionIsMadeOncePerProcess() {
        val controller = process()
        controller.launch(fresh = true)
        controller.cancelled()
        assertEquals(OnboardingPrompt.NONE, controller.launch(fresh = true))
        assertEquals(OnboardingSurface.None, controller.surface.value)
    }

    @Test
    fun anInterruptedFreshRunComesBackAtTheNextLaunch() {
        process().launch(fresh = true)
        // The app was closed mid-way: the next process runs it again.
        val next = process()
        assertEquals(OnboardingPrompt.RUN, next.launch(fresh = false))
        assertEquals(OnboardingSurface.SetUp, next.surface.value)
    }

    @Test
    fun aCompletedOrSkippedRunIsNotAskedAgain() {
        process().apply { launch(fresh = true) }.completed()
        assertEquals(OnboardingPrompt.NONE, process().launch(fresh = true))
        assertEquals(1, store.stored?.completedRuns)

        val skipped = InMemoryOnboardingStore()
        OnboardingController(skipped, Dispatchers.Unconfined).apply { runBlocking { decideAtLaunch { true } } }
            .cancelled()
        assertEquals(OnboardingProgress.SKIPPED, skipped.stored?.progress)
        assertEquals(
            OnboardingPrompt.NONE,
            runBlocking { OnboardingController(skipped, Dispatchers.Unconfined).decideAtLaunch { true } },
        )
    }

    @Test
    fun theSetUpCanBeRunAgainFromSettingsAtAnyTime() {
        val controller = process()
        controller.launch(fresh = false)
        controller.rerun()
        assertEquals(OnboardingSurface.SetUp, controller.surface.value)
        controller.completed()
        assertEquals(OnboardingSurface.None, controller.surface.value)
        controller.rerun()
        assertEquals(OnboardingSurface.SetUp, controller.surface.value)
        // A cancelled re-run keeps the earlier completion.
        controller.cancelled()
        assertEquals(OnboardingProgress.COMPLETED, store.stored?.progress)
    }

    @Test
    fun aRerunBeforeTheLaunchDecisionWins() {
        val controller = process()
        controller.rerun()
        controller.launch(fresh = true)
        assertEquals(OnboardingSurface.SetUp, controller.surface.value)
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
