package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seeded random sequences over the editor session with saved lenses: flows (add and re-bind) using saved lenses,
 * Save as lens with good and bad names, adopting identical containers, detaching, Undo and Redo. After every
 * step: no dangling reference, every reference resolves to its library lens, no workspace gains an issue, library
 * names stay unique, and each step's Undo restores the previous session exactly.
 */
class SavedLensEditorPropertyTest {
    private val flatSources = listOf(APPS, NOTES)
    private val names = listOf("Latest", "latest", "Per app", "", "  ", "x".repeat(45), "Notes", "Apps 5")

    private fun lensFor(random: Random) =
        lens(
            flatSources[random.nextInt(flatSources.size)],
            group = if (random.nextInt(3) == 0) LensGroup.ByGroupKey else LensGroup.None,
            limit = listOf(null, 5)[random.nextInt(2)],
        )

    private fun bindingFor(random: Random): LensBinding {
        val lens = lensFor(random)
        val kind = if (lens.group == LensGroup.None) ExpressionKind.LIST else ExpressionKind.INDEX
        return LensBinding(lens, kind)
    }

    private fun startLayout(random: Random): WorkspaceEditSession {
        val shared = LensBinding(lens(APPS, limit = 5), ExpressionKind.LIST)
        val home =
            Workspace(
                WorkspaceId("home"),
                "Home",
                listOf(
                    boundPage("a", shared),
                    boundPage("b", bindingFor(random)),
                    PageSetContainer(cid("s"), grouped()),
                ),
                dock = WorkspaceDock(bindingFor(random)),
            )
        val others =
            (0 until 2).map { i ->
                Workspace(
                    WorkspaceId("o$i"),
                    "Other $i",
                    listOf(boundPage("o$i-a", shared), boundPage("o$i-b", bindingFor(random))),
                )
            }
        return WorkspaceEditSession(home, scope = LensScope(LensLibrary(), others))
    }

    private fun grouped() = LensBinding(lens(NOTES, group = LensGroup.ByGroupKey), ExpressionKind.CARD_STACK)

    private fun sitesOf(workspace: Workspace) = WorkspaceBindings.sites(workspace)

    private fun targets(workspace: Workspace): List<FlowMode> =
        buildList {
            workspace.pages.forEach { page ->
                when (page) {
                    is PageSetContainer -> add(FlowMode.EditPage(page.id))
                    is PageContainer ->
                        if (page.content is PageContent.Bound) add(FlowMode.EditPage(page.id))
                }
            }
            if (workspace.dock.dynamicSection != null) add(FlowMode.EditDock)
        }

    private fun randomFlowAction(
        random: Random,
        session: WorkspaceEditSession,
        state: BindingFlowState,
    ): BindingFlowAction = if (random.nextBoolean()) lensAction(random, session) else flowAction(random, state)

    private fun lensAction(
        random: Random,
        session: WorkspaceEditSession,
    ): BindingFlowAction {
        val ids = session.scope.library.lenses.map { it.id } + LensId("unknown")
        return when (random.nextInt(6)) {
            0 -> BindingFlowAction.ToggleSource(flatSources[random.nextInt(2)])
            1 -> BindingFlowAction.ApplyPreset(LensPreset.entries[random.nextInt(LensPreset.entries.size)])
            2, 3 -> BindingFlowAction.UseSavedLens(ids[random.nextInt(ids.size)])
            4 -> BindingFlowAction.DetachSavedLens
            else -> if (random.nextBoolean()) BindingFlowAction.ShowSavedLenses else BindingFlowAction.ShowBuilder
        }
    }

    private fun flowAction(
        random: Random,
        state: BindingFlowState,
    ): BindingFlowAction =
        when (random.nextInt(10)) {
            0, 1 ->
                BindingFlowAction.PickExpression(
                    ExpressionKind.entries[random.nextInt(ExpressionKind.entries.size)],
                )
            2 -> BindingFlowAction.PickContainer(ContainerKind.entries[random.nextInt(ContainerKind.entries.size)])
            3, 4, 5 -> BindingFlowAction.Next
            6 -> BindingFlowAction.Back
            7 -> BindingFlowAction.StartSaveAsLens(names[random.nextInt(names.size)])
            8 -> BindingFlowAction.SetSaveAsName(names[random.nextInt(names.size)])
            else -> if (state.saveAs == null) BindingFlowAction.Next else BindingFlowAction.CancelSaveAsLens
        }

    private class Run(
        var session: WorkspaceEditSession,
        var flow: BindingFlowState?,
        var mode: FlowMode,
        /** The last step was a flow commit, an adoption or a detach (not an Undo or Redo). */
        var edited: Boolean = false,
    )

    private fun contextFor(
        session: WorkspaceEditSession,
        mode: FlowMode,
    ) = BindingFlowContext(choices(), session.draft, mode, ids = counterIds("g"), scope = session.scope)

    @Test
    fun randomSessionsKeepTheLibraryTheWorkspacesAndUndoConsistent() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val initial = startLayout(random)
            val known = knownIssues(initial)
            val run = Run(initial, null, FlowMode.Add)
            repeat(OPERATIONS) { index -> advance(random, run, known, "seed $seed step $index") }
        }
        assertTrue(
            commits > 0 && savedUses > 0 && saveAsCommits > 0 && adoptions > 0,
            "coverage $commits $savedUses $saveAsCommits $adoptions",
        )
    }

    private var commits = 0
    private var savedUses = 0
    private var saveAsCommits = 0
    private var adoptions = 0

    private fun knownIssues(session: WorkspaceEditSession): Map<WorkspaceId, Set<WorkspaceIssue>> =
        (listOf(session.draft) + session.scope.others).associate {
            it.id to WorkspaceEditor.issues(it, EDIT_CONTEXT).toSet()
        }

    private fun advance(
        random: Random,
        run: Run,
        known: Map<WorkspaceId, Set<WorkspaceIssue>>,
        label: String,
    ) {
        val before = run.session
        run.edited = false
        val flow = run.flow
        when {
            flow == null -> startOrOperate(random, run)
            random.nextInt(25) == 0 -> run.flow = null
            random.nextInt(12) == 0 -> {
                run.session = if (random.nextBoolean()) run.session.undo() else run.session.redo()
            }
            flow.step == EditorStep.CONFIRM && random.nextInt(3) == 0 -> confirm(random, run, flow)
            else -> {
                val ctx = contextFor(run.session, run.mode)
                val action = guided(random, flow, ctx) ?: randomFlowAction(random, run.session, flow)
                run.flow = BindingFlowReducer.reduce(flow, action, ctx)
                if (action is BindingFlowAction.UseSavedLens && run.flow?.ref != null) savedUses++
            }
        }
        check(run, known, label)
        redoRestores(run.session, label)
        if (run.edited) undoIsExact(before, run.session, label)
    }

    /** Mostly walks the flow forward through options the domain offers, so confirms are common. */
    private fun guided(
        random: Random,
        flow: BindingFlowState,
        ctx: BindingFlowContext,
    ): BindingFlowAction? =
        if (random.nextBoolean()) {
            null
        } else {
            when (flow.step) {
                EditorStep.SOURCE ->
                    if (flow.draft.toLens() == null) {
                        BindingFlowAction.ToggleSource(
                            flatSources[random.nextInt(2)],
                        )
                    } else {
                        BindingFlowAction.Next
                    }
                EditorStep.EXPRESSION ->
                    BindingFlow.expressionChoices(flow, ctx).filter { it.enabled }.randomOrNull(random)
                        ?.let { BindingFlowAction.PickExpression(it.kind) }
                        ?.takeIf { flow.expression == null } ?: BindingFlowAction.Next
                EditorStep.CONTAINER ->
                    BindingFlow.containerChoices(flow, ctx).filter { it.enabled }.randomOrNull(random)
                        ?.let { BindingFlowAction.PickContainer(it.kind) }
                        ?.takeIf { flow.container == null } ?: BindingFlowAction.Next
                EditorStep.CONFIRM ->
                    if (flow.saveAs == null) {
                        BindingFlowAction.StartSaveAsLens(
                            names[random.nextInt(names.size)],
                        )
                    } else {
                        null
                    }
            }
        }

    private fun startOrOperate(
        random: Random,
        run: Run,
    ) {
        val targets = targets(run.session.draft)
        when (random.nextInt(8)) {
            0, 1 -> startFlow(run, FlowMode.Add)
            2, 3 -> if (targets.isNotEmpty()) startFlow(run, targets[random.nextInt(targets.size)])
            4 ->
                if (targets.isNotEmpty()) {
                    run.session = LensSessionOps.detach(run.session, targets[random.nextInt(targets.size)])
                    run.edited = true
                }
            5 -> adoptRandom(random, run)
            6 -> run.session = run.session.undo()
            else -> run.session = run.session.redo()
        }
    }

    private fun startFlow(
        run: Run,
        mode: FlowMode,
    ) {
        run.mode = mode
        run.flow = BindingFlow.start(contextFor(run.session, mode))
    }

    private fun adoptRandom(
        random: Random,
        run: Run,
    ) {
        val library = run.session.scope.library.lenses
        if (library.isNotEmpty()) {
            run.session = LensSessionOps.adopt(run.session, library[random.nextInt(library.size)].id)
            run.edited = true
        }
    }

    private fun confirm(
        random: Random,
        run: Run,
        flow: BindingFlowState,
    ) {
        val ctx = contextFor(run.session, run.mode)
        when (val result = LensSessionOps.commit(run.session, flow, ctx)) {
            is FlowCommit.Blocked -> Unit
            is FlowCommit.Applied -> {
                commits++
                if (flow.saveAs != null && result.session.scope.library != run.session.scope.library) saveAsCommits++
                undoIsExact(run.session, result.session, "commit")
                run.session = result.session
                run.flow = null
                val offer = result.offer
                if (offer != null && random.nextBoolean()) {
                    val adopted = LensSessionOps.adopt(run.session, offer.id)
                    assertEquals(0, LensSessionOps.identicalCount(adopted, offer.id))
                    run.session = adopted
                    adoptions++
                }
            }
        }
    }

    private fun check(
        run: Run,
        known: Map<WorkspaceId, Set<WorkspaceIssue>>,
        label: String,
    ) {
        val session = run.session
        val layout = session.scope.layoutWith(session.draft)
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty(), "dangling ref: $label")
        layout.workspaces.forEach { workspace ->
            sitesOf(workspace).forEach { site ->
                val saved = site.binding.ref?.let(layout.library::find)
                if (site.binding.ref != null) assertEquals(saved?.lens, site.binding.lens, "snapshot: $label")
            }
            val introduced =
                WorkspaceEditor.issues(
                    workspace,
                    EDIT_CONTEXT,
                ).filterNot { it in known.getValue(workspace.id) }
            assertTrue(introduced.isEmpty(), "workspace ${workspace.id.value} gained $introduced: $label")
        }
        val libraryNames = session.scope.library.lenses.map { it.name.lowercase() }
        assertEquals(libraryNames.toSet().size, libraryNames.size, "unique names: $label")
        run.flow = run.flow?.let { SavedLensFlow.reconcile(it, contextFor(session, run.mode)) }
        run.flow?.let { flow ->
            if (flow.ref != null) {
                assertEquals(session.scope.library.find(flow.ref)?.lens, flow.draft.toLens(), "flow ref: $label")
            }
        }
    }

    /** Undo then Redo returns to the same draft and scope. */
    private fun redoRestores(
        after: WorkspaceEditSession,
        label: String,
    ) {
        if (after.canUndo) {
            val redone = after.undo().redo()
            assertEquals(after.draft, redone.draft, "redo draft: $label")
            assertEquals(after.scope, redone.scope, "redo scope: $label")
        }
    }

    /** A step that changed the session is exactly one history step: Undo restores the previous draft and scope. */
    private fun undoIsExact(
        before: WorkspaceEditSession,
        after: WorkspaceEditSession,
        label: String,
    ) {
        if (after.draft != before.draft || after.scope != before.scope) {
            val undone = after.undo()
            assertEquals(before.draft, undone.draft, "undo draft: $label")
            assertEquals(before.scope, undone.scope, "undo scope: $label")
        }
    }

    @Test
    fun everyCommitIsExactlyOneUndoStep() {
        repeat(SEEDS) { seed ->
            val random = Random(1000 + seed)
            var session = startLayout(random)
            repeat(OPERATIONS) {
                val mode =
                    if (random.nextBoolean()) FlowMode.Add else targets(session.draft).firstOrNull() ?: FlowMode.Add
                val ctx = contextFor(session, mode)
                var state = BindingFlow.start(ctx)
                repeat(random.nextInt(4, 14)) {
                    state = BindingFlowReducer.reduce(state, randomFlowAction(random, session, state), ctx)
                }
                if (state.step == EditorStep.CONFIRM) {
                    val result = LensSessionOps.commit(session, state, ctx)
                    if (result is FlowCommit.Applied && result.session != session) {
                        val undone = result.session.undo()
                        assertEquals(session.draft, undone.draft, "seed $seed")
                        assertEquals(session.scope, undone.scope, "seed $seed")
                        session = result.session
                    }
                }
            }
        }
    }

    @Test
    fun savingALensNeverAddsIdsTwiceAndUnknownNamesAreUnique() {
        var library = LensLibrary()
        val ids = counterIds("p")
        repeat(30) { i ->
            val name = LensNameProbe.suggest(library, "Latest")
            val added = library.tryAdd(name, lens(APPS, limit = i + 1), ids)
            library = (added as LibraryAdd.Added).library
        }
        assertEquals(30, library.lenses.map { it.name.lowercase() }.toSet().size)
    }

    private companion object {
        const val SEEDS = 150
        const val OPERATIONS = 40
    }
}

/** Mirrors what the flow does for a suggested name, through the public flow API. */
private object LensNameProbe {
    fun suggest(
        library: LensLibrary,
        base: String,
    ): String {
        val ctx =
            BindingFlowContext(
                choices(),
                workspace(boundPage("a")),
                FlowMode.Add,
                ids = counterIds("z"),
                scope = LensScope(library),
            )
        val state =
            listOf(
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE),
                BindingFlowAction.Next,
                BindingFlowAction.StartSaveAsLens(base),
            ).fold(BindingFlow.start(ctx)) { s, a -> BindingFlowReducer.reduce(s, a, ctx) }
        return state.saveAs?.name ?: error("Save as lens was not offered")
    }
}
