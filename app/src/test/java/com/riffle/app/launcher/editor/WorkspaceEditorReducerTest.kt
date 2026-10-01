package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEditorReducerTest {
    private val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(SourceIds.NOTIFICATIONS, setOf(SourceCapability.GROUPABLE, SourceCapability.LIVE)),
            SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.LIVE)),
        )

    private var counter = 0

    private val environment =
        EditorEnvironment(
            sources = SourceChoices.build(descriptors, mapOf(SourceIds.CALENDAR to SourceAccess.REQUIRED)),
            ids = WorkspaceIdFactory { "id-${counter++}" },
        )
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

    private fun run(
        start: WorkspaceEditorUiState,
        vararg actions: EditorAction,
    ): EditorTransition {
        var transition = EditorTransition(start)
        actions.forEach { transition = reducer.reduce(transition.state, it) }
        return transition
    }

    private fun flow(vararg actions: BindingFlowAction) = actions.map { EditorAction.Flow(it) }.toTypedArray()

    @Test
    fun editsChangeTheDraftAndUndoRestoresIt() {
        val start = reducer.start(home)
        val edited = run(start, EditorAction.Apply(WorkspaceEdit.Rename("Work"))).state
        assertEquals("Work", edited.session.draft.name)
        assertEquals("Home", edited.session.committed.name)
        val undone = run(edited, EditorAction.Undo).state
        assertEquals("Home", undone.session.draft.name)
        assertEquals("Work", run(undone, EditorAction.Redo).state.session.draft.name)
    }

    @Test
    fun anInvalidEditIsRefusedWithAMessage() {
        val result = run(reducer.start(home), EditorAction.Apply(WorkspaceEdit.RemovePage(ContainerId("p1")))).state
        assertEquals(home, result.session.draft)
        assertNotNull(result.message)
        assertEquals("A workspace needs at least one page", messageText(checkNotNull(result.message)))
        assertNull(run(result, EditorAction.DismissMessage).state.message)
    }

    @Test
    fun closingWithChangesAsksFirstAndDiscardCloses() {
        val dirty = run(reducer.start(home), EditorAction.Apply(WorkspaceEdit.Rename("Work"))).state
        val asked = run(dirty, EditorAction.RequestClose)
        assertTrue(asked.state.confirmingDiscard)
        assertNull(asked.effect)
        assertFalse(run(asked.state, EditorAction.KeepEditing).state.confirmingDiscard)
        val discarded = run(asked.state, EditorAction.DiscardAndClose)
        assertEquals(EditorEffect.Close, discarded.effect)
        assertEquals(home, discarded.state.session.draft)
    }

    @Test
    fun closingWithoutChangesJustCloses() {
        assertEquals(EditorEffect.Close, run(reducer.start(home), EditorAction.RequestClose).effect)
        assertEquals(EditorEffect.Close, run(reducer.start(home), EditorAction.Back).effect)
    }

    @Test
    fun saveEmitsTheCommittedWorkspaceOnce() {
        val dirty = run(reducer.start(home), EditorAction.Apply(WorkspaceEdit.Rename("Work"))).state
        val saved = run(dirty, EditorAction.Save)
        assertEquals(EditorEffect.Save(dirty.session.draft), saved.effect)
        assertFalse(saved.state.session.isDirty)
        assertEquals(EditorEffect.Close, run(saved.state, EditorAction.Save).effect)
    }

    @Test
    fun revertReturnsToTheCommittedWorkspace() {
        val dirty = run(reducer.start(home), EditorAction.Apply(WorkspaceEdit.Rename("Work"))).state
        assertEquals(home, run(dirty, EditorAction.RevertChanges).state.session.draft)
    }

    @Test
    fun theFlowAddsAPageSetThroughTheReducerAndNeverEmitsAnEffect() {
        val start = reducer.start(home)
        val actions =
            listOf<EditorAction>(EditorAction.StartFlow(FlowMode.Add)) +
                flow(
                    BindingFlowAction.ToggleSource(SourceIds.NOTIFICATIONS),
                    BindingFlowAction.ApplyPreset(LensPreset.GROUPED),
                    BindingFlowAction.Next,
                    BindingFlowAction.PickExpression(ExpressionKind.CARD_STACK),
                    BindingFlowAction.Next,
                    BindingFlowAction.PickContainer(ContainerKind.PAGE_SET),
                    BindingFlowAction.Next,
                )
        var transition = EditorTransition(start)
        actions.forEach {
            transition = reducer.reduce(transition.state, it)
            assertNull("the editor must never request access or close on its own", transition.effect)
        }
        assertEquals(EditorStep.CONFIRM, transition.state.flow?.state?.step)
        val done = run(transition.state, EditorAction.ConfirmFlow).state
        assertNull(done.flow)
        assertTrue(done.session.draft.pages.last() is PageSetContainer)
        assertTrue(done.session.canUndo)
    }

    @Test
    fun anIncompatibleExpressionCannotBePickedInTheFlow() {
        val state =
            run(
                reducer.start(home),
                EditorAction.StartFlow(FlowMode.Add),
                *flow(
                    BindingFlowAction.ToggleSource(SourceIds.ALL_APPS),
                    BindingFlowAction.Next,
                    BindingFlowAction.PickExpression(ExpressionKind.CARD),
                ),
            ).state
        assertNull(state.flow?.state?.expression)
    }

    @Test
    fun aPermissionGatedSourceCanBeChosenWithoutAnyPrompt() {
        val transition =
            run(
                reducer.start(home),
                EditorAction.StartFlow(FlowMode.Add),
                *flow(BindingFlowAction.ToggleSource(SourceIds.CALENDAR)),
            )
        assertEquals(listOf(SourceIds.CALENDAR), transition.state.flow?.state?.draft?.sources)
        assertNull(transition.effect)
        assertTrue(environment.sources.single { it.id == SourceIds.CALENDAR }.needsPermission)
    }

    @Test
    fun backStepsBackThenCancelsThenAsks() {
        var transition =
            run(
                reducer.start(home),
                EditorAction.StartFlow(FlowMode.Add),
                *flow(BindingFlowAction.ToggleSource(SourceIds.ALL_APPS), BindingFlowAction.Next),
            )
        assertEquals(EditorStep.EXPRESSION, transition.state.flow?.state?.step)
        transition = reducer.reduce(transition.state, EditorAction.Back)
        assertEquals(EditorStep.SOURCE, transition.state.flow?.state?.step)
        transition = reducer.reduce(transition.state, EditorAction.Back)
        assertNull(transition.state.flow)
        assertEquals(EditorEffect.Close, reducer.reduce(transition.state, EditorAction.Back).effect)
    }

    @Test
    fun reBindingAPageSkipsTheContainerStep() {
        val state =
            run(
                reducer.start(home),
                EditorAction.StartFlow(FlowMode.EditPage(ContainerId("p1"))),
                *flow(
                    BindingFlowAction.Next,
                    BindingFlowAction.PickExpression(ExpressionKind.LIST),
                    BindingFlowAction.Next,
                ),
            ).state
        assertEquals(EditorStep.CONFIRM, state.flow?.state?.step)
        val done = run(state, EditorAction.ConfirmFlow).state
        val page = done.session.draft.pages.single() as PageContainer
        assertEquals(ExpressionKind.LIST, (page.content as PageContent.Bound).binding.expression)
    }
}
