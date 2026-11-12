package com.riffle.core.domain.launcher.onboarding

import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingTest {
    @Test
    fun theFourStepsAreOnePerPlaceInOrder() {
        assertEquals(
            listOf(OnboardingStep.HOME, OnboardingStep.DOCK, OnboardingStep.LIBRARY, OnboardingStep.STREAM),
            OnboardingStep.entries,
        )
        assertNull(OnboardingStep.HOME.previous())
        assertEquals(OnboardingStep.DOCK, OnboardingStep.HOME.next())
        assertEquals(OnboardingStep.LIBRARY, OnboardingStep.DOCK.next())
        assertEquals(OnboardingStep.STREAM, OnboardingStep.LIBRARY.next())
        assertNull(OnboardingStep.STREAM.next())
        assertEquals(true, OnboardingStep.STREAM.isLast)
    }

    @Test
    fun aFreshInstallRunsTheSetUpUntilItIsFinishedOrSkipped() {
        val (decided, prompt) = Onboarding.atLaunch(OnboardingState(), freshInstall = true)
        assertEquals(OnboardingOrigin.FRESH_INSTALL, decided.origin)
        assertEquals(OnboardingPrompt.RUN, prompt)
        assertEquals(OnboardingProgress.STARTED, decided.progress)

        assertEquals(OnboardingPrompt.RUN, Onboarding.atLaunch(decided, freshInstall = false).second)
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(Onboarding.completed(decided), true).second)
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(Onboarding.skipped(decided), true).second)
    }

    @Test
    fun anExistingUserIsNeverPrompted() {
        val (decided, prompt) = Onboarding.atLaunch(OnboardingState(), freshInstall = false)
        assertEquals(OnboardingOrigin.EXISTING_USER, decided.origin)
        assertEquals(OnboardingPrompt.NONE, prompt)
        assertEquals(OnboardingProgress.NOT_STARTED, decided.progress)
        // A later fresh-looking start (a cleared store) never turns an existing user into a fresh install.
        assertEquals(OnboardingPrompt.NONE, Onboarding.atLaunch(decided, freshInstall = true).second)
    }

    @Test
    fun aCancelledRerunKeepsAnEarlierCompletion() {
        val completed = Onboarding.completed(OnboardingState(OnboardingOrigin.FRESH_INSTALL))
        assertEquals(completed, Onboarding.skipped(Onboarding.rerun(completed)))
        assertEquals(1, completed.completedRuns)
    }

    @Test
    fun aStateWrittenByTheWalkthroughBuildStillDecodes() {
        val old: StoredValue.Obj =
            obj(
                "version" to num(1),
                "origin" to str("EXISTING_USER"),
                "walkthrough" to str("COMPLETED"),
                "offer" to str("ACCEPTED"),
                "completedRuns" to num(2),
            )
        assertEquals(
            OnboardingState(OnboardingOrigin.EXISTING_USER, OnboardingProgress.COMPLETED, completedRuns = 2),
            OnboardingCodec.decode(old),
        )
        assertEquals(OnboardingState(), OnboardingCodec.decode(null))
        assertEquals(OnboardingState(), OnboardingCodec.decode(obj("origin" to str("MARS"))))
    }

    @Test
    fun theStateRoundTrips() {
        val state = OnboardingState(OnboardingOrigin.FRESH_INSTALL, OnboardingProgress.SKIPPED, completedRuns = 4)
        assertEquals(state, OnboardingCodec.decode(OnboardingCodec.encode(state)))
    }

    @Test
    fun eachPermissionNamesTheSourceItsExistingFlowServes() {
        assertEquals(SourceIds.NOTIFICATIONS, SourcePermission.NOTIFICATION_ACCESS.source)
        assertEquals(SourceIds.CALENDAR, SourcePermission.CALENDAR.source)
        assertEquals(SourceIds.RECENT_APPS, SourcePermission.USAGE_ACCESS.source)
    }
}
