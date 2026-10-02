package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LensSessionOpsTest {
    private val latest = LensDraft(sources = listOf(APPS)).withPreset(LensPreset.LATEST_FIVE).toLens()!!
    private val inline = LensBinding(latest, ExpressionKind.LIST)

    private val home = workspace(boundPage("a", binding(ExpressionKind.ICON_GRID)))
    private val work =
        Workspace(
            WorkspaceId("work"),
            "Work",
            listOf(boundPage("w1", inline), boundPage("w2", inline.copy(expression = ExpressionKind.ICON_GRID))),
            dock = WorkspaceDock(inline),
        )

    private fun session(vararg others: Workspace) =
        WorkspaceEditSession(home, scope = LensScope(others = others.toList()))

    private fun context(
        session: WorkspaceEditSession,
        mode: FlowMode = FlowMode.Add,
    ) = BindingFlowContext(choices(), session.draft, mode, ids = counterIds("n"), scope = session.scope)

    private fun flowToConfirm(
        context: BindingFlowContext,
        vararg extra: BindingFlowAction,
    ) = listOf(
        BindingFlowAction.ToggleSource(APPS),
        BindingFlowAction.ApplyPreset(LensPreset.LATEST_FIVE),
        BindingFlowAction.Next,
        BindingFlowAction.PickExpression(ExpressionKind.LIST),
        BindingFlowAction.Next,
        BindingFlowAction.PickContainer(ContainerKind.PAGE),
        BindingFlowAction.Next,
        *extra,
    ).fold(BindingFlow.start(context)) { s, a -> BindingFlowReducer.reduce(s, a, context) }

    private fun saveAs(session: WorkspaceEditSession): FlowCommit {
        val ctx = context(session)
        return LensSessionOps.commit(session, flowToConfirm(ctx, BindingFlowAction.StartSaveAsLens("Latest apps")), ctx)
    }

    @Test
    fun saveAsLensAddsTheEntryAndTheReferencingContainerAsOneUndoStep() {
        val start = session()
        val applied = assertIs<FlowCommit.Applied>(saveAs(start))
        val next = applied.session
        val entry = assertNotNull(next.scope.library.lenses.singleOrNull())
        assertEquals("Latest apps", entry.name)
        assertEquals(latest, entry.lens)
        val added = next.draft.pages.last()
        assertEquals(
            entry.id,
            (added as PageContainer).let {
                (it.content as PageContent.Bound).binding.ref
            },
        )
        assertTrue(next.isDirty)
        assertNull(applied.offer)

        val undone = next.undo()
        assertEquals(start.draft, undone.draft)
        assertEquals(start.scope, undone.scope)
        assertFalse(undone.isDirty)
        assertEquals(next.draft, undone.redo().draft)
        assertEquals(next.scope, undone.redo().scope)
    }

    @Test
    fun theOfferCountsOnlyInlineContainersWithAnIdenticalLensAcrossTheLayout() {
        val applied = assertIs<FlowCommit.Applied>(saveAs(session(work)))
        val offer = assertNotNull(applied.offer)
        // work: page w1 and the dock are identical; w2 differs by expression only, which still counts (same lens).
        assertEquals(3, offer.count)
        assertEquals("Latest apps", offer.name)
    }

    @Test
    fun noOfferWhenNothingElseHoldsTheLens() {
        val other = Workspace(WorkspaceId("o"), "O", listOf(boundPage("o1", binding(ExpressionKind.LIST, NOTES))))
        assertNull(assertIs<FlowCommit.Applied>(saveAs(session(other))).offer)
    }

    @Test
    fun adoptingIsOneAtomicStepAndUndoIsExact() {
        val saved = assertIs<FlowCommit.Applied>(saveAs(session(work)))
        val offer = assertNotNull(saved.offer)
        val adopted = LensSessionOps.adopt(saved.session, offer.id)
        assertNotEquals(saved.session.scope, adopted.scope)
        val layout = adopted.scope.layoutWith(adopted.draft)
        assertEquals(offer.count + 1, LensLibraryOps.dependents(layout, offer.id).size)
        assertEquals(0, LensSessionOps.identicalCount(adopted, offer.id))
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty())
        // Every lens and expression is unchanged: only references were added.
        assertEquals(
            lensesOf(saved.session.scope.layoutWith(saved.session.draft)),
            lensesOf(layout),
        )

        val undone = adopted.undo()
        assertEquals(saved.session.draft, undone.draft)
        assertEquals(saved.session.scope, undone.scope)
        // The next Undo reverts Save as lens itself.
        assertTrue(undone.undo().scope.library.lenses.isEmpty())
    }

    @Test
    fun adoptingAnUnknownLensChangesNothing() {
        val start = session(work)
        assertEquals(start, LensSessionOps.adopt(start, LensId("nope")))
    }

    @Test
    fun adoptionLeavesBindingsThatUseAnotherSavedLensAlone() {
        val taken = assertIs<LibraryAdd.Added>(LensLibrary().tryAdd("Other", latest, counterIds("x")))
        val usingOther =
            work.copy(
                pages = listOf(boundPage("w1", inline.copy(ref = taken.id))),
                dock = WorkspaceDock(null),
            )
        val start = WorkspaceEditSession(home, scope = LensScope(taken.library, listOf(usingOther)))
        val applied = assertIs<FlowCommit.Applied>(saveAs(start))
        assertNull(applied.offer)
        val ref = applied.session.scope.layoutWith(applied.session.draft)
        assertEquals(
            taken.id,
            WorkspaceBindings.sites(ref.find(usingOther.id)!!).single().binding.ref,
        )
    }

    @Test
    fun detachMakesTheBindingInlineAndUndoReattaches() {
        val saved = assertIs<FlowCommit.Applied>(saveAs(session()))
        val pageId = saved.session.draft.pages.last().id
        val detached = LensSessionOps.detach(saved.session, FlowMode.EditPage(pageId))
        val binding =
            (
                (detached.draft.pages.last() as PageContainer).content as
                    PageContent.Bound
            ).binding
        assertNull(binding.ref)
        assertEquals(latest, binding.lens)
        // The library entry stays: Detach never deletes a saved lens.
        assertEquals(1, detached.scope.library.lenses.size)
        assertEquals(saved.session.draft, detached.undo().draft)
    }

    @Test
    fun detachingAnInlineOrUnknownTargetIsANoOp() {
        val start = session()
        assertEquals(start, LensSessionOps.detach(start, FlowMode.EditPage(cid("a"))))
        assertEquals(start, LensSessionOps.detach(start, FlowMode.EditPage(cid("missing"))))
        assertEquals(start, LensSessionOps.detach(start, FlowMode.Add))
    }

    @Test
    fun aLibraryOnlyChangeKeepsTheSessionDirtyUntilCommittedAndCancelRestoresIt() {
        val saved = assertIs<FlowCommit.Applied>(saveAs(session(work))).session
        val committed = assertIs<CommitResult.Committed>(saved.commit(EDIT_CONTEXT)).session
        assertFalse(committed.isDirty)
        assertEquals(saved.scope, committed.scope)
        assertEquals(session(work).scope, saved.cancel().scope)
        assertFalse(saved.cancel().isDirty)
    }

    @Test
    fun aLibraryOnlyStepIsAHistoryStepAndMarksTheSessionDirty() {
        val start = session(work)
        assertSame(start, start.applyLayout(start.scope.layoutWith(start.draft)))
        val added = assertIs<LibraryAdd.Added>(start.scope.library.tryAdd("Latest", latest, counterIds("x")))
        val next = start.applyLayout(start.scope.layoutWith(start.draft).copy(library = added.library))
        assertTrue(next.isDirty)
        assertTrue(next.canUndo)
        assertEquals(start.draft, next.undo().draft)
        assertEquals(start.scope, next.undo().scope)
    }

    @Test
    fun scopeSplitsAndRebuildsALayout() {
        val layout = LayoutWorkspaces(listOf(home, work), home.id, home.id)
        val scope = LensScope.of(layout, home.id)
        assertEquals(listOf(work), scope.others)
        assertEquals(layout.workspaces.toSet(), scope.layoutWith(home).workspaces.toSet())
        assertEquals(emptyList(), scope.changedOthers(scope))
        val changed = scope.copy(others = listOf(work.copy(name = "Renamed")))
        assertEquals(listOf("Renamed"), changed.changedOthers(scope).map { it.name })
        // The edited workspace never appears twice, even if the scope were built carelessly.
        assertEquals(2, LensScope(others = listOf(home, work)).layoutWith(home).workspaces.size)
    }

    private fun lensesOf(layout: LayoutWorkspaces) =
        layout.workspaces.map { ws ->
            ws.id to WorkspaceBindings.sites(ws).map { it.binding.lens to it.binding.expression }
        }
}
