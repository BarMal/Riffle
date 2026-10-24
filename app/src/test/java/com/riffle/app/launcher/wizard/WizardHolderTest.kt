package com.riffle.app.launcher.wizard

import com.riffle.app.launcher.wizard.WizardTestFixtures.context
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardExit
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardHolderTest {
    private val editor = context()

    private fun reduce(
        start: WizardUiState,
        vararg actions: WizardAction,
    ): WizardUiState = actions.fold(start) { state, action -> WizardHolder.reduce(state, action, editor) }

    @Test
    fun editorRunWalksEveryStepAndFinishes() {
        var ui = WizardHolder.start(editor)
        assertEquals(WizardStep.SOURCE, ui.wizard.step)
        assertEquals(1 to 5, WizardHolder.progress(ui.wizard, editor))
        ui = reduce(ui, WizardAction.ChooseGoal(ContentGoalId.MY_APPS))
        val seen = mutableListOf(ui.wizard.step)
        while (ui.wizard.exit == null) {
            ui = reduce(ui, WizardAction.Next)
            seen += ui.wizard.step
        }
        assertEquals(
            listOf(
                WizardStep.SOURCE,
                WizardStep.CONTENT,
                WizardStep.LOOK,
                WizardStep.PLACE,
                WizardStep.SAVE,
                WizardStep.DONE,
            ),
            seen,
        )
        assertEquals(WizardExit.Finished, ui.wizard.exit)
        assertEquals(Destination.PAGE, ui.wizard.destination)
    }

    @Test
    fun theLastStepSaysAddOrSaveForALensOnly() {
        val atSave =
            reduce(
                WizardHolder.start(editor),
                WizardAction.ChooseGoal(ContentGoalId.MY_APPS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        assertEquals(WizardStep.SAVE, atSave.wizard.step)
        assertTrue(WizardHolder.isLast(atSave.wizard, editor))
        assertEquals(WizardText.ADD, WizardText.primary(true, WizardHolder.lensOnly(atSave.wizard)))
        assertEquals(5 to 5, WizardHolder.progress(atSave.wizard, editor))

        val lens = context(entry = WizardEntry.NewSavedLens)
        val start = WizardHolder.start(lens)
        assertTrue(WizardHolder.lensOnly(start.wizard))
        assertEquals(WizardText.SAVE, WizardText.primary(true, WizardHolder.lensOnly(start.wizard)))
        assertFalse(WizardHolder.isLast(start.wizard, lens))
    }

    @Test
    fun backStepsBackAndCancelsOnTheFirstStep() {
        val atLook =
            reduce(WizardHolder.start(editor), WizardAction.ChooseGoal(ContentGoalId.MY_APPS), WizardAction.Next)
                .let { reduce(it, WizardAction.Next) }
        assertEquals(WizardStep.LOOK, atLook.wizard.step)
        val back = reduce(atLook, WizardAction.Back)
        assertEquals(WizardStep.CONTENT, back.wizard.step)
        val cancelled = reduce(back, WizardAction.Back, WizardAction.Back)
        assertEquals(WizardExit.Cancelled, cancelled.wizard.exit)
    }

    @Test
    fun cancelClosesFromAnyStepWithoutAResult() {
        val cancelled =
            reduce(WizardHolder.start(editor), WizardAction.ChooseGoal(ContentGoalId.MY_APPS), WizardAction.Cancel)
        assertEquals(WizardExit.Cancelled, cancelled.wizard.exit)
        // A closed wizard ignores everything after.
        assertSame(cancelled, reduce(cancelled, WizardAction.Next))
    }

    @Test
    fun anActionThatChangesNothingKeepsTheNotice() {
        val refused =
            WizardHolder.refused(
                WizardHolder.start(editor).let { reduce(it, WizardAction.ChooseGoal(ContentGoalId.MY_APPS)) },
                "Nope",
                editor,
            )
        assertEquals("Nope", refused.notice)
        assertEquals("Nope", reduce(refused, WizardAction.ChooseLook("grid")).notice)
        assertNull(reduce(refused, WizardAction.Back).notice)
    }

    @Test
    fun aRefusedFinishReopensOnTheLastStepWithEveryChoiceKept() {
        val finished =
            reduce(
                WizardHolder.start(editor),
                WizardAction.ChooseGoal(ContentGoalId.MY_APPS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        assertEquals(WizardExit.Finished, finished.wizard.exit)
        val refused = WizardHolder.refused(finished, "This workspace was deleted.", editor)
        assertNull(refused.wizard.exit)
        assertEquals(WizardStep.SAVE, refused.wizard.step)
        assertEquals(finished.wizard.setup, refused.wizard.setup)
        assertEquals("This workspace was deleted.", refused.notice)
        assertEquals(WizardStep.PLACE, reduce(refused, WizardAction.Back).wizard.step)
    }

    @Test
    fun aRestoredRunContinuesWhereItWas() {
        val atPlace =
            reduce(
                WizardHolder.start(editor),
                WizardAction.ChooseGoal(ContentGoalId.MY_APPS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        val restored = WizardHolder.start(editor, WizardStateCodec.decode(WizardStateCodec.encode(atPlace.wizard)))
        assertEquals(atPlace.wizard, restored.wizard)
    }

    @Test
    fun aRestoredStateThatNoLongerFitsStartsAgain() {
        // A step this run does not show (Place for a new saved lens), or a closed run, is not restored.
        val lens = context(entry = WizardEntry.NewSavedLens)
        val stale = WizardState(step = WizardStep.PLACE)
        assertEquals(WizardStep.SOURCE, WizardHolder.start(lens, stale).wizard.step)
        val closed = WizardState(exit = WizardExit.Cancelled)
        assertNull(WizardHolder.start(editor, closed).wizard.exit)
    }

    @Test
    fun useThisSourceStartsPastTheSourceStep() {
        val fromSource = context(entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, WizardTestFixtures.homeId))
        val ui = WizardHolder.start(fromSource)
        assertEquals(ContentGoalId.NOTIFICATIONS, ui.wizard.setup?.goal)
        assertTrue(WizardStep.SOURCE !in WizardHolder.visibleSteps(ui.wizard, fromSource))
        assertEquals(1, WizardHolder.progress(ui.wizard, fromSource).first)
        // Back on the first shown step cancels.
        assertEquals(WizardExit.Cancelled, WizardHolder.reduce(ui, WizardAction.Back, fromSource).wizard.exit)
    }

    @Test
    fun theAnnouncementNamesTheSavedLensOrTheGoal() {
        val saving =
            reduce(WizardHolder.start(editor), WizardAction.ChooseGoal(ContentGoalId.MY_APPS)).wizard
        assertEquals("My apps, grid", WizardHolder.addedName(saving, editor))
        val notSaving = saving.copy(saveToLibrary = false)
        assertEquals("My apps", WizardHolder.addedName(notSaving, editor))
    }
}
