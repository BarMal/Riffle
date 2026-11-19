package com.riffle.core.domain.launcher.onboarding

import com.riffle.core.domain.launcher.hints.Hint
import com.riffle.core.domain.launcher.hints.HintEvent
import com.riffle.core.domain.launcher.hints.HintProgress
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
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
    fun theHomeStartChoiceDefaultsToNotChosenInOldDataRoundTripsAndIsSetOnce() {
        val old = obj("origin" to str("FRESH_INSTALL"), "walkthrough" to str("STARTED"))
        assertEquals(false, OnboardingCodec.decode(old).homeStartChosen)
        assertEquals(false, OnboardingCodec.decode(obj("homeStartChosen" to str("yes"))).homeStartChosen)

        val chosen = Onboarding.homeStartChosen(OnboardingState(OnboardingOrigin.FRESH_INSTALL))
        assertEquals(true, OnboardingCodec.decode(OnboardingCodec.encode(chosen)).homeStartChosen)
        // Neither finishing, skipping nor re-running clears it.
        assertEquals(true, Onboarding.skipped(chosen).homeStartChosen)
        assertEquals(true, Onboarding.completed(chosen).homeStartChosen)
        assertEquals(true, Onboarding.rerun(chosen).homeStartChosen)
    }

    @Test
    fun hintsAreAbsentInDataWrittenBeforeThemAndNothingIsRetired() {
        val old =
            obj(
                "version" to num(1),
                "origin" to str("EXISTING_USER"),
                "walkthrough" to str("COMPLETED"),
                "completedRuns" to num(1),
                "homeStartChosen" to StoredValue.Bool(true),
            )
        val decoded = OnboardingCodec.decode(old)
        assertEquals(HintProgress(), decoded.hints)
        assertEquals(true, decoded.homeStartChosen)
    }

    @Test
    fun hintsRoundTripAndUnknownOrMalformedPartsReadAsDefaults() {
        val hints =
            HintProgress(
                retired = setOf(Hint.DOCK_PRESS, Hint.DOCK_SCRUB),
                homeVisits = 3,
                libraryOpenedElsewhere = true,
                lastRetiredVisit = 2,
            )
        val state = OnboardingState(OnboardingOrigin.FRESH_INSTALL, hints = hints)
        assertEquals(hints, OnboardingCodec.decode(OnboardingCodec.encode(state)).hints)

        val odd =
            obj(
                "hints" to
                    obj(
                        "retired" to arr(listOf(str("dockPress"), str("fromTheFuture"), num(7))),
                        "homeVisits" to str("many"),
                        "lastRetiredVisit" to num(-4),
                    ),
            )
        assertEquals(
            HintProgress(retired = setOf(Hint.DOCK_PRESS), lastRetiredVisit = 0),
            OnboardingCodec.decode(odd).hints,
        )
        assertEquals(HintProgress(), OnboardingCodec.decode(obj("hints" to str("nope"))).hints)
    }

    @Test
    fun hintEventsAndShowHintsAgainChangeOnlyTheHints() {
        val state = Onboarding.completed(OnboardingState(OnboardingOrigin.FRESH_INSTALL))
        val retired = Onboarding.hintEvent(state, HintEvent.Dismissed(Hint.DOCK_MENU))
        assertEquals(setOf(Hint.DOCK_MENU), retired.hints.retired)
        assertEquals(state.progress, retired.progress)
        assertEquals(state, Onboarding.hintEvent(state, HintEvent.StackScrubbed).copy(hints = state.hints))
        assertEquals(state, Onboarding.showHintsAgain(retired))
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

    @Test
    fun eachSectionsPermissionSaysWhatItGains() {
        assertEquals(PermissionGain.NOTIFICATIONS, PermissionGain.of(listOf(SourceIds.NOTIFICATIONS)))
        assertEquals(PermissionGain.MEDIA, PermissionGain.of(listOf(SourceIds.MEDIA)))
        assertEquals(PermissionGain.RECENT_APPS, PermissionGain.of(listOf(SourceIds.RECENT_APPS)))
        assertEquals(PermissionGain.CALENDAR, PermissionGain.of(listOf(SourceIds.CALENDAR)))
        assertEquals(PermissionGain.CALENDAR, PermissionGain.of(listOf(StreamSourceIds.AGENDA)))
    }

    @Test
    fun anUnknownOrMissingSourceKeepsTheSentenceGeneral() {
        assertEquals(PermissionGain.GENERIC, PermissionGain.of(emptyList()))
        assertEquals(PermissionGain.GENERIC, PermissionGain.of(listOf(SourceIds.RSS)))
        // The first source this knows decides, so an unknown one before it does not hide it.
        assertEquals(PermissionGain.RECENT_APPS, PermissionGain.of(listOf(SourceIds.RSS, SourceIds.RECENT_APPS)))
    }

    @Test
    fun everyPermissionTheOnboardingAsksForHasAGain() {
        SourcePermission.entries.forEach { permission ->
            assertEquals(false, PermissionGain.of(listOf(permission.source)) == PermissionGain.GENERIC)
        }
    }
}
