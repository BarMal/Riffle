package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor state machine with saved lenses: use, Save as lens, the offer, detach, Undo, discard and save. */
class EditorSavedLensReducerTest {
    private val apps = SourceIds.ALL_APPS
    private val latest = LensDraft(sources = listOf(apps)).withPreset(LensPreset.LATEST_FIVE).toLens()!!
    private val inline = LensBinding(latest, ExpressionKind.LIST)

    private var counter = 0
    private val environment =
        EditorEnvironment(
            sources =
                SourceChoices.build(
                    listOf(
                        SourceDescriptor(apps, setOf(SourceCapability.GROUPABLE)),
                        SourceDescriptor(SourceIds.NOTIFICATIONS, setOf(SourceCapability.GROUPABLE)),
                    ),
                ),
            ids = WorkspaceIdFactory { "id-${counter++}" },
        )
    private val reducer = WorkspaceEditorReducer(environment)

    private val saved = (
        LensLibrary().tryAdd(
            "Latest apps",
            latest,
            WorkspaceIdFactory { "lens-1" },
        ) as LibraryAdd.Added
    )
    private val libraryWithLatest = saved.library
    private val lensId: LensId = saved.id

    private val home =
        Workspace(
            id = WorkspaceId("home"),
            name = "Home",
            pages =
                listOf(
                    PageContainer(
                        ContainerId("p1"),
                        PageContent.Bound(LensBinding(Lens(listOf(apps)), ExpressionKind.ICON_GRID)),
                    ),
                ),
        )

    private val work =
        Workspace(
            id = WorkspaceId("work"),
            name = "Work",
            pages = listOf(PageContainer(ContainerId("w1"), PageContent.Bound(inline))),
            dock = WorkspaceDock(inline),
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

    private fun addPageFlow(vararg extra: BindingFlowAction) =
        arrayOf<EditorAction>(EditorAction.StartFlow(FlowMode.Add)) +
            flow(
                BindingFlowAction.ToggleSource(apps),
                BindingFlowAction.ApplyPreset(LensPreset.LATEST_FIVE),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE),
                BindingFlowAction.Next,
                *extra,
            )

    private fun bindingOf(
        workspace: Workspace,
        pageId: String,
    ): LensBinding =
        ((workspace.pages.first { it.id.value == pageId } as PageContainer).content as PageContent.Bound).binding

    @Test
    fun usingASavedLensWhileRebindingAPageKeepsTheReferenceAndUndoRestoresIt() {
        val start = reducer.start(home, LensScope(libraryWithLatest))
        val done =
            run(
                start,
                EditorAction.StartFlow(FlowMode.EditPage(ContainerId("p1"))),
                *flow(
                    BindingFlowAction.ShowSavedLenses,
                    BindingFlowAction.UseSavedLens(lensId),
                    BindingFlowAction.Next,
                    BindingFlowAction.PickExpression(ExpressionKind.LIST),
                    BindingFlowAction.Next,
                ),
                EditorAction.ConfirmFlow,
            ).state
        assertNull(done.flow)
        val binding = bindingOf(done.session.draft, "p1")
        assertEquals(LensBinding(latest, ExpressionKind.LIST, lensId), binding)
        // The library did not change: only the binding references it.
        assertEquals(libraryWithLatest, done.session.scope.library)
        val undone = run(done, EditorAction.Undo).state
        assertEquals(home, undone.session.draft)
    }

    @Test
    fun saveAsLensCreatesTheLensAndOffersTheIdenticalContainersAsOneUndoableStep() {
        val start = reducer.start(home, LensScope(LensLibrary(), listOf(work)))
        val saved =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
            ).state
        val lens = saved.session.scope.library.lenses.single()
        assertEquals("Latest apps", lens.name)
        assertEquals(lens.id, bindingOf(saved.session.draft, saved.session.draft.pages.last().id.value).ref)
        val offer = checkNotNull(saved.offer)
        assertEquals(2, offer.count)
        assertNull("the offer says it; no second message", saved.message)

        val adopted = run(saved, EditorAction.AdoptIdentical).state
        assertNull(adopted.offer)
        assertEquals(EditorMessage.Adopted("Latest apps", 2), adopted.message)
        val other = adopted.session.scope.others.single()
        assertEquals(lens.id, bindingOf(other, "w1").ref)
        assertEquals(lens.id, other.dock.dynamicSection?.ref)

        val undoneAdopt = run(adopted, EditorAction.Undo).state
        assertEquals(saved.session.scope, undoneAdopt.session.scope)
        assertEquals(saved.session.draft, undoneAdopt.session.draft)
        val undoneSave = run(undoneAdopt, EditorAction.Undo).state
        assertEquals(start.session.scope, undoneSave.session.scope)
        assertEquals(home, undoneSave.session.draft)
        assertFalse(undoneSave.session.isDirty)
    }

    @Test
    fun withNothingIdenticalThereIsNoOfferAndTheSavedLensIsAnnounced() {
        val saved =
            run(
                reducer.start(home, LensScope()),
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
            ).state
        assertNull(saved.offer)
        assertEquals(EditorMessage.SavedAsLens("Latest apps"), saved.message)
        assertEquals("Saved as the lens “Latest apps”. Undo takes it back.", messageText(checkNotNull(saved.message)))
        assertFalse(messageIsError(checkNotNull(saved.message)))
    }

    @Test
    fun theOfferEndsWithAnyOtherActionAndNothingIsAdopted() {
        val start = reducer.start(home, LensScope(LensLibrary(), listOf(work)))
        val saved =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest")),
                EditorAction.ConfirmFlow,
            ).state
        assertNotNull(saved.offer)
        val renamed = run(saved, EditorAction.Apply(WorkspaceEdit.Rename("Renamed"))).state
        assertNull(renamed.offer)
        assertEquals(saved.session.scope, renamed.session.scope)
        assertNull(run(saved, EditorAction.DismissOffer).state.offer)
        assertEquals(saved.session.scope, run(saved, EditorAction.DismissOffer).state.session.scope)
        // Answering a stale offer does nothing.
        assertEquals(renamed.session, run(renamed, EditorAction.AdoptIdentical).state.session)
    }

    @Test
    fun aRefusedNameKeepsTheFlowOpenWithTheLibrarysReason() {
        val start = reducer.start(home, LensScope(libraryWithLatest))
        val state =
            run(
                start,
                *addPageFlow(
                    BindingFlowAction.StartSaveAsLens("Latest apps"),
                    BindingFlowAction.SetSaveAsName("latest APPS"),
                ),
                EditorAction.ConfirmFlow,
            ).state
        assertNotNull("the flow stays open", state.flow)
        assertEquals(EditorMessage.LibraryRefused(LibraryProblem.NAME_TAKEN), state.message)
        assertEquals("Another saved lens already has that name.", messageText(checkNotNull(state.message)))
        assertTrue(messageIsError(checkNotNull(state.message)))
        assertEquals(start.session.draft, state.session.draft)
        assertEquals(start.session.scope, state.session.scope)
    }

    @Test
    fun theSuggestedNameIsUniqueAgainstTheLibrary() {
        val start = reducer.start(home, LensScope(libraryWithLatest))
        val state = run(start, *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps"))).state
        assertEquals("Latest apps 2", state.flow?.state?.saveAs?.name)
    }

    @Test
    fun detachingIsOneUndoableStepThatKeepsTheLens() {
        val bound =
            home.copy(
                pages = listOf(PageContainer(ContainerId("p1"), PageContent.Bound(inline.copy(ref = lensId)))),
            )
        val start = reducer.start(bound, LensScope(libraryWithLatest))
        val detached = run(start, EditorAction.Detach(FlowMode.EditPage(ContainerId("p1")))).state
        assertEquals(inline, bindingOf(detached.session.draft, "p1"))
        assertEquals(EditorMessage.Detached("Latest apps"), detached.message)
        assertEquals(libraryWithLatest, detached.session.scope.library)
        assertEquals(bound, run(detached, EditorAction.Undo).state.session.draft)
        // Detaching an inline binding changes nothing and says nothing.
        val again = run(detached, EditorAction.Detach(FlowMode.EditPage(ContainerId("p1")))).state
        assertEquals(detached.session, again.session)
        assertNull(again.message)
    }

    @Test
    fun undoWhileAFlowUsesAnUndoneSavedLensKeepsTheLensInlineInTheFlow() {
        val start = reducer.start(home, LensScope(LensLibrary(), listOf(work)))
        val saved =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
            ).state
        val id = saved.session.scope.library.lenses.single().id
        val using =
            run(
                saved,
                EditorAction.StartFlow(FlowMode.EditPage(ContainerId("p1"))),
                *flow(BindingFlowAction.UseSavedLens(id)),
            ).state
        assertEquals(id, using.flow?.state?.ref)
        val undone = run(using, EditorAction.Undo).state
        assertTrue(undone.session.scope.library.lenses.isEmpty())
        assertNull(undone.flow?.state?.ref)
        assertEquals(latest, undone.flow?.state?.draft?.toLens())
    }

    @Test
    fun discardingRevertsTheLibraryAndTheOtherWorkspacesToo() {
        val start = reducer.start(home, LensScope(LensLibrary(), listOf(work)))
        val saved =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
                EditorAction.AdoptIdentical,
            ).state
        assertTrue(saved.session.isDirty)
        val asked = run(saved, EditorAction.RequestClose)
        assertTrue(asked.state.confirmingDiscard)
        val discarded = run(asked.state, EditorAction.DiscardAndClose)
        assertEquals(EditorEffect.Close, discarded.effect)
        assertEquals(start.session.scope, discarded.state.session.scope)
        assertEquals(home, discarded.state.session.draft)
    }

    @Test
    fun saveCarriesTheLibraryAndTheOtherWorkspacesThatAdoptedIt() {
        val layout = LayoutWorkspaces(listOf(home, work), home.id, home.id)
        val start = reducer.start(home, LensScope.of(layout, home.id))
        val adopted =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
                EditorAction.AdoptIdentical,
            ).state
        val saved = run(adopted, EditorAction.Save)
        val effect = saved.effect as EditorEffect.Save
        assertEquals(adopted.session.draft, effect.workspace)
        assertEquals(listOf(WorkspaceId("work")), effect.change.others.map { it.id })
        val written = effect.change.applyTo(layout.replace(home.id) { effect.workspace })
        assertTrue(LensLibraryOps.danglingRefs(written).isEmpty())
        val lens = written.library.lenses.single()
        assertEquals(lens.id, bindingOf(checkNotNull(written.find(work.id)), "w1").ref)
        // A session without saved-lens changes writes nothing extra.
        val plain =
            run(
                reducer.start(home, LensScope.of(layout, home.id)),
                EditorAction.Apply(WorkspaceEdit.Rename("X")),
                EditorAction.Save,
            )
        assertTrue((plain.effect as EditorEffect.Save).change.isEmpty)
    }

    @Test
    fun detachingAnInlineBindingIsNotAChange() {
        val bound = home.copy(pages = listOf(PageContainer(ContainerId("p1"), PageContent.Bound(inline))))
        val scope = LensScope(libraryWithLatest)
        val start = reducer.start(bound, scope)
        val adopted = run(start, EditorAction.Detach(FlowMode.EditPage(ContainerId("p1")))).state
        assertFalse("an inline binding detaches to itself", adopted.session.isDirty)
    }

    @Test
    fun theTopBarUndoAndRedoWorkAcrossSavedLensSteps() {
        val start = reducer.start(home, LensScope(LensLibrary(), listOf(work)))
        val saved =
            run(
                start,
                *addPageFlow(BindingFlowAction.StartSaveAsLens("Latest apps")),
                EditorAction.ConfirmFlow,
            ).state
        val undone = run(saved, EditorAction.Undo).state
        assertEquals(start.session.scope, undone.session.scope)
        val redone = run(undone, EditorAction.Redo).state
        assertEquals(saved.session.scope, redone.session.scope)
        assertEquals(saved.session.draft, redone.session.draft)
    }
}
