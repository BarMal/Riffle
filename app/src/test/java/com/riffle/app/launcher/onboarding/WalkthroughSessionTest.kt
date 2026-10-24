package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.onboarding.WalkthroughFixtures.NOTHING_GRANTED
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.PermissionStatus
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughExit
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The walkthrough's state holder: navigation, skip, Back, and the permission round trip through the host. */
class WalkthroughSessionTest {
    private var access: Map<SourceId, SourceAccess> = NOTHING_GRANTED
    private val launched = mutableListOf<SourcePermission>()
    private val session =
        WalkthroughSession(
            contextOf = { WalkthroughFixtures.context(access = access) },
            launchPermission = { launched += it },
        )

    private val step get() = session.ui.value.state.step

    private fun nextUntil(target: WalkthroughStep) {
        repeat(MAX_STEPS) { if (step != target) session.dispatch(WalkthroughAction.Next) }
        assertEquals(target, step)
    }

    @Test
    fun itStartsOnWelcomeAndWalksForwardAndBack() {
        assertEquals(WalkthroughStep.WELCOME, step)
        assertEquals(1, session.ui.value.position)
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughStep.GOALS, step)
        assertEquals(2, session.ui.value.position)
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughStep.ARRANGE, step)
        session.dispatch(WalkthroughAction.Back)
        assertEquals(WalkthroughStep.GOALS, step)
        nextUntil(WalkthroughStep.REVIEW)
        assertEquals(session.ui.value.steps.size - 1, session.ui.value.position)
    }

    @Test
    fun backOnTheFirstStepAndSkipLeaveWithoutApplying() {
        session.dispatch(WalkthroughAction.Back)
        assertEquals(WalkthroughExit.Cancelled, session.ui.value.state.exit)

        val other = WalkthroughSession({ WalkthroughFixtures.context() }, {})
        other.dispatch(WalkthroughAction.Next)
        other.dispatch(WalkthroughAction.Next)
        other.dispatch(WalkthroughAction.Cancel)
        assertEquals(WalkthroughExit.Cancelled, other.ui.value.state.exit)
        // Nothing moves once closed.
        other.dispatch(WalkthroughAction.Back)
        assertEquals(WalkthroughExit.Cancelled, other.ui.value.state.exit)
    }

    @Test
    fun goalsCanBeToggled() {
        session.dispatch(WalkthroughAction.Next)
        val before = session.ui.value.state.chosen
        session.dispatch(WalkthroughAction.ToggleGoal(ContentGoalId.NEWS_FEEDS))
        assertEquals((before + ContentGoalId.NEWS_FEEDS).toSet(), session.ui.value.state.chosen.toSet())
        session.dispatch(WalkthroughAction.ToggleGoal(ContentGoalId.NEWS_FEEDS))
        assertEquals(before, session.ui.value.state.chosen)
    }

    @Test
    fun aPermissionIsExplainedThenRequestedThroughTheHostAndReportedOnReturn() {
        nextUntil(WalkthroughStep.PERMISSIONS)
        // "Allow": the rationale only; nothing opens.
        session.dispatch(WalkthroughAction.ExplainPermission(SourcePermission.CALENDAR))
        assertTrue(launched.isEmpty())
        // "Continue": the existing flow opens, once.
        session.dispatch(WalkthroughAction.RequestPermission(SourcePermission.CALENDAR))
        assertEquals(listOf(SourcePermission.CALENDAR), launched)
        assertEquals(SourcePermission.CALENDAR, session.ui.value.state.pendingRequest)
        // A resume that is not a return from the flow reports nothing.
        session.onHostResumed()
        assertEquals(SourcePermission.CALENDAR, session.ui.value.state.pendingRequest)
        // The user allows it in the system dialog and comes back.
        session.onHostPaused()
        access = access + (SourceIds.CALENDAR to SourceAccess.GRANTED)
        session.onHostResumed()
        val ui = session.ui.value
        assertNull(ui.state.pendingRequest)
        assertEquals(
            PermissionStatus.GRANTED,
            WalkthroughFlow.permissionStatus(ui.state, SourcePermission.CALENDAR, ui.context),
        )
        assertEquals(listOf(SourcePermission.CALENDAR), launched)
    }

    @Test
    fun aRefusalOrAReturnFromSettingsWithoutTheSwitchIsDenied() {
        nextUntil(WalkthroughStep.PERMISSIONS)
        session.dispatch(WalkthroughAction.ExplainPermission(SourcePermission.NOTIFICATION_ACCESS))
        session.dispatch(WalkthroughAction.RequestPermission(SourcePermission.NOTIFICATION_ACCESS))
        session.onHostPaused()
        session.onHostResumed()
        val ui = session.ui.value
        assertEquals(
            PermissionStatus.DENIED,
            WalkthroughFlow.permissionStatus(ui.state, SourcePermission.NOTIFICATION_ACCESS, ui.context),
        )
        // The content stays and reads "Needs access" at the review.
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughStep.REVIEW, step)
        val review = WalkthroughFlow.review(session.ui.value.state, session.ui.value.context)
        assertTrue(ContentGoalId.NOTIFICATIONS in review.needsAccess)
    }

    @Test
    fun checkAgainReportsAFlowThatNeverOpened() {
        nextUntil(WalkthroughStep.PERMISSIONS)
        session.dispatch(WalkthroughAction.ExplainPermission(SourcePermission.CALENDAR))
        session.dispatch(WalkthroughAction.RequestPermission(SourcePermission.CALENDAR))
        // While pending, Next and Back wait.
        session.dispatch(WalkthroughAction.Next)
        session.dispatch(WalkthroughAction.Back)
        assertEquals(WalkthroughStep.PERMISSIONS, step)
        session.checkPermissionAgain()
        assertNull(session.ui.value.state.pendingRequest)
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughStep.REVIEW, step)
    }

    @Test
    fun skippingEveryPermissionKeepsTheContent() {
        nextUntil(WalkthroughStep.PERMISSIONS)
        session.dispatch(WalkthroughAction.SkipPermission(SourcePermission.NOTIFICATION_ACCESS))
        session.dispatch(WalkthroughAction.Next)
        val ui = session.ui.value
        assertEquals(WalkthroughStep.REVIEW, ui.state.step)
        assertEquals(PermissionStatus.SKIPPED, ui.state.permissions[SourcePermission.NOTIFICATION_ACCESS])
        assertEquals(PermissionStatus.SKIPPED, ui.state.permissions[SourcePermission.CALENDAR])
        assertTrue(launched.isEmpty())
        assertTrue(ContentGoalId.CALENDAR in ui.state.chosen)
    }

    @Test
    fun grantingEverythingOnThePermissionsStepStillMovesOnToTheReview() {
        nextUntil(WalkthroughStep.PERMISSIONS)
        for (permission in listOf(SourcePermission.NOTIFICATION_ACCESS, SourcePermission.CALENDAR)) {
            session.dispatch(WalkthroughAction.ExplainPermission(permission))
            session.dispatch(WalkthroughAction.RequestPermission(permission))
            session.onHostPaused()
            access = access.mapValues { (id, value) -> grantedFor(permission, id, value) }
            session.onHostResumed()
        }
        assertTrue(WalkthroughStep.PERMISSIONS !in session.ui.value.steps)
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughStep.REVIEW, step)
    }

    @Test
    fun aRefusedApplyReopensTheReviewWithEveryChoiceKept() {
        nextUntil(WalkthroughStep.REVIEW)
        val chosen = session.ui.value.state.chosen
        session.dispatch(WalkthroughAction.Next)
        assertEquals(WalkthroughExit.Finished, session.ui.value.state.exit)
        session.reopenReview()
        assertEquals(WalkthroughStep.REVIEW, step)
        assertNull(session.ui.value.state.exit)
        assertEquals(chosen, session.ui.value.state.chosen)
    }

    private fun grantedFor(
        permission: SourcePermission,
        id: SourceId,
        value: SourceAccess,
    ): SourceAccess = if (SourcePermission.forSource(id) == permission) SourceAccess.GRANTED else value

    private companion object {
        const val MAX_STEPS = 30
    }
}
