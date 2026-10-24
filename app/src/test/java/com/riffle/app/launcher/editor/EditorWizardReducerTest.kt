package com.riffle.app.launcher.editor

import com.riffle.app.launcher.wizard.EditorWizardResult
import com.riffle.app.launcher.wizard.WizardCommit
import com.riffle.app.launcher.wizard.WizardPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardExit
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow
import com.riffle.core.domain.launcher.workspace.wizard.WizardReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's content wizard path: the wizard's result is one session step, so Undo, Revert and Done cover it. */
class EditorWizardReducerTest {
    private val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE, SourceCapability.SEARCHABLE)),
            SourceDescriptor(SourceIds.NOTIFICATIONS, setOf(SourceCapability.GROUPABLE)),
        )

    private var counter = 0
    private val environment =
        EditorEnvironment(SourceChoices.build(descriptors, emptyMap()), ids = WorkspaceIdFactory { "id-${counter++}" })
    private val reducer = WorkspaceEditorReducer(environment)

    private val home =
        Workspace(
            id = WorkspaceId("home"),
            name = "Home",
            pages =
                listOf(
                    PageContainer(
                        ContainerId("p1"),
                        PageContent.Bound(LensBinding(Lens(listOf(SourceIds.ALL_APPS)), ExpressionKind.ICON_GRID)),
                    ),
                ),
        )

    private fun reduce(
        state: WorkspaceEditorUiState,
        vararg actions: EditorAction,
    ): EditorTransition =
        actions.fold(EditorTransition(state)) { transition, action -> reducer.reduce(transition.state, action) }

    /** Opens the wizard, runs My apps through every step, and returns what the editor host dispatches. */
    private fun myAppsThroughTheWizard(state: WorkspaceEditorUiState): EditorAction.ApplyWizard {
        val open = checkNotNull(state.wizard)
        val context = editorWizardContext(state, open, environment)
        val start = WizardFlow.start(context)
        var wizard = WizardReducer.reduce(start, WizardAction.ChooseGoal(ContentGoalId.MY_APPS), context)
        while (wizard.exit == null) wizard = WizardReducer.reduce(wizard, WizardAction.Next, context)
        assertEquals(WizardExit.Finished, wizard.exit)
        val placement = WizardPlacement(HomeLayoutDeviceClass.PHONE, ids = environment.ids)
        val result = WizardCommit.forEditor(wizard, context, null, placement) as EditorWizardResult.Apply
        return EditorAction.ApplyWizard(result.edit, result.library, result.name)
    }

    @Test
    fun addPageOpensTheWizardAndBackClosesIt() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        assertNotNull(open.wizard)
        assertNull(open.flow)
        val closed = reduce(open, EditorAction.Back)
        assertNull(closed.state.wizard)
        assertNull(closed.effect)
        assertNull(reduce(open, EditorAction.CloseWizard).state.wizard)
        assertFalse(closed.state.session.isDirty)
    }

    @Test
    fun theWizardsResultIsOneUndoableStepWithItsSavedLens() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        val applied = reduce(open, myAppsThroughTheWizard(open)).state

        assertNull(applied.wizard)
        assertEquals(EditorMessage.WizardAdded("My apps, grid"), applied.message)
        assertEquals(2, applied.session.draft.pages.size)
        val lens = applied.session.scope.library.lenses.single()
        val added = applied.session.draft.pages.last() as PageContainer
        assertEquals(lens.id, (added.content as PageContent.Bound).binding.ref)
        assertTrue(applied.session.isDirty)

        val undone = reduce(applied, EditorAction.Undo).state
        assertEquals(home, undone.session.draft)
        assertTrue(undone.session.scope.library.lenses.isEmpty())
        assertFalse(undone.session.isDirty)
        val redone = reduce(undone, EditorAction.Redo).state
        assertEquals(applied.session.draft, redone.session.draft)
    }

    @Test
    fun doneSavesTheWizardsPageAndItsLensTogether() {
        val open = reduce(reducer.start(home, LensScope()), EditorAction.OpenWizard()).state
        val applied = reduce(open, myAppsThroughTheWizard(open)).state

        val saved = reduce(applied, EditorAction.Save).effect as EditorEffect.Save

        assertEquals(2, saved.workspace.pages.size)
        assertEquals(1, saved.change.library?.lenses?.size)
    }

    @Test
    fun revertDropsTheWizardsChanges() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        val applied = reduce(open, myAppsThroughTheWizard(open)).state
        val reverted = reduce(applied, EditorAction.RevertChanges).state
        assertEquals(home, reverted.session.draft)
        assertTrue(reverted.session.scope.library.lenses.isEmpty())
    }

    @Test
    fun aWidgetPagesAddWidgetOpensTheWizardOnThatPage() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard(ContainerId("p1"))).state
        assertEquals(ContainerId("p1"), open.wizard?.pageId)
        val context = editorWizardContext(open, checkNotNull(open.wizard), environment)
        assertEquals(ContainerId("p1"), (context.entry as WizardEntry.Editor).pageId)
        assertEquals(home, context.workspace)
    }

    @Test
    fun openAdvancedBuilderStartsTheAddFlowOnTheWizardsDraft() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        val draft = LensDraft(sources = listOf(SourceIds.NOTIFICATIONS))
        val handoff = AdvancedHandoff(draft, ExpressionKind.LIST, ContainerKind.WIDGET)

        val advanced = reduce(open, EditorAction.StartAdvanced(handoff)).state

        assertNull(advanced.wizard)
        val flow = checkNotNull(advanced.flow)
        assertEquals(FlowMode.Add, flow.mode)
        assertEquals(EditorStep.SOURCE, flow.state.step)
        assertEquals(draft, flow.state.draft)
        assertEquals(ExpressionKind.LIST, flow.state.expression)
        assertEquals(ContainerKind.WIDGET, flow.state.container)
    }

    @Test
    fun aPlacedPageTheWizardAddsDoesNotBlockDone() {
        // `home.grid` is never in the registry; the editor validates with it known, as the wizard does.
        val placedLens = Lens(listOf(WorkspaceSourceIds.HOME_GRID), filter = LensFilter.GroupKeyIs("page-x"))
        val placed =
            PageContainer(
                ContainerId("placed"),
                PageContent.Bound(LensBinding(placedLens, ExpressionKind.ICON_GRID)),
            )
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        val applied =
            reduce(
                open,
                EditorAction.ApplyWizard(
                    WorkspaceEdit.AddPage(placed, 0),
                    open.session.scope.library,
                    "Favourite apps",
                ),
            ).state
        val first = (applied.session.draft.pages.first() as PageContainer).content as PageContent.Bound
        assertEquals(WorkspaceSourceIds.HOME_GRID, first.binding.lens.sources.single())
        assertTrue(reduce(applied, EditorAction.Save).effect is EditorEffect.Save)
    }

    @Test
    fun onlyPickedAppsLeaveTheSessionUnchangedButStillAnnounce() {
        val open = reduce(reducer.start(home), EditorAction.OpenWizard()).state
        val applied = reduce(open, EditorAction.ApplyWizard(null, open.session.scope.library, "Favourite apps")).state
        assertNull(applied.wizard)
        assertEquals(home, applied.session.draft)
        assertFalse(applied.session.canUndo)
        assertEquals(EditorMessage.WizardAdded("Favourite apps"), applied.message)
        assertEquals("Added Favourite apps", messageText(applied.message!!))
        assertFalse(messageIsError(applied.message!!))
    }
}
