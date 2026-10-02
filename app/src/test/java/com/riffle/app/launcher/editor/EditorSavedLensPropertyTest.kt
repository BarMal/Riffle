package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Seeded random action sequences through the editor's own state machine (flow, Save as lens, the offer, adopt, detach,
 * Undo, Redo, Done): no dangling reference, every reference resolves to its library lens, no workspace gains an issue,
 * the open flow never holds a reference that does not resolve, and the saved result applies cleanly to a layout.
 */
class EditorSavedLensPropertyTest {
    private val apps = SourceIds.ALL_APPS
    private val notes = SourceIds.NOTIFICATIONS
    private val names = listOf("Latest", "latest", "Per app", "", "x".repeat(60), "Notes")

    private var counter = 0
    private val environment =
        EditorEnvironment(
            sources =
                SourceChoices.build(
                    listOf(
                        SourceDescriptor(apps, setOf(SourceCapability.GROUPABLE)),
                        SourceDescriptor(notes, setOf(SourceCapability.GROUPABLE)),
                    ),
                ),
            ids = WorkspaceIdFactory { "p-${counter++}" },
        )
    private val reducer = WorkspaceEditorReducer(environment)

    private val shared = LensBinding(Lens(listOf(apps), limit = 5), ExpressionKind.LIST)

    private fun page(
        id: String,
        binding: LensBinding = shared,
    ) = PageContainer(ContainerId(id), PageContent.Bound(binding))

    private val home = Workspace(WorkspaceId("home"), "Home", listOf(page("a"), page("b")))
    private val others =
        listOf(
            Workspace(
                WorkspaceId("o1"),
                "One",
                listOf(page("o1a"), page("o1b", shared.copy(expression = ExpressionKind.ICON_GRID))),
            ),
            Workspace(WorkspaceId("o2"), "Two", listOf(page("o2a"))),
        )

    private fun action(
        random: Random,
        state: WorkspaceEditorUiState,
    ): EditorAction {
        val library = state.session.scope.library.lenses.map { it.id } + LensId("unknown")
        val targets = state.session.draft.pages.map { FlowMode.EditPage(it.id) } + FlowMode.Add
        return when (random.nextInt(14)) {
            0 -> EditorAction.StartFlow(targets[random.nextInt(targets.size)])
            1 -> EditorAction.Undo
            2 -> EditorAction.Redo
            3 -> EditorAction.Detach(targets[random.nextInt(targets.size)])
            4 -> EditorAction.AdoptIdentical
            5 -> EditorAction.Save
            6 -> EditorAction.ConfirmFlow
            else -> EditorAction.Flow(flowAction(random, library))
        }
    }

    private fun flowAction(
        random: Random,
        library: List<LensId>,
    ): BindingFlowAction =
        when (random.nextInt(12)) {
            0 -> BindingFlowAction.ToggleSource(if (random.nextBoolean()) apps else notes)
            1 -> BindingFlowAction.ApplyPreset(LensPreset.entries[random.nextInt(LensPreset.entries.size)])
            2, 3 -> BindingFlowAction.UseSavedLens(library[random.nextInt(library.size)])
            4 -> BindingFlowAction.DetachSavedLens
            5 -> BindingFlowAction.PickExpression(ExpressionKind.entries[random.nextInt(ExpressionKind.entries.size)])
            6 -> BindingFlowAction.PickContainer(ContainerKind.entries[random.nextInt(ContainerKind.entries.size)])
            7, 8 -> BindingFlowAction.Next
            9 -> BindingFlowAction.StartSaveAsLens(names[random.nextInt(names.size)])
            10 -> BindingFlowAction.SetSaveAsName(names[random.nextInt(names.size)])
            else -> BindingFlowAction.ShowSavedLenses
        }

    @Test
    fun randomEditorSessionsKeepSavedLensesConsistent() {
        var saves = 0
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val scope = LensScope(LensLibrary(), others)
            var state = reducer.start(home, scope)
            val known =
                (listOf(home) + others).associate {
                    it.id to WorkspaceEditor.issues(it, environment.editContext).toSet()
                }
            repeat(OPERATIONS) { step ->
                val action = action(random, state)
                val transition = reducer.reduce(state, action)
                state = transition.state
                check(state, known, "seed $seed step $step ($action)")
                (transition.effect as? EditorEffect.Save)?.let {
                    saves++
                    val layout =
                        scope.layoutWith(home).let {
                                base ->
                            it.change.applyTo(base.replace(home.id) { _ -> it.workspace })
                        }
                    assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty())
                }
            }
        }
        assertTrue("some sessions reach a save: $saves", saves > 0)
    }

    private fun check(
        state: WorkspaceEditorUiState,
        known: Map<WorkspaceId, Set<WorkspaceIssue>>,
        label: String,
    ) {
        val session = state.session
        val layout = session.scope.layoutWith(session.draft)
        assertTrue("dangling: $label", LensLibraryOps.danglingRefs(layout).isEmpty())
        layout.workspaces.forEach { workspace ->
            workspace.pages.filterIsInstance<PageContainer>().forEach { page ->
                val binding = (page.content as? PageContent.Bound)?.binding
                val saved = binding?.ref?.let(layout.library::find)
                if (saved != null) assertEquals("snapshot: $label", saved.lens, binding.lens)
            }
            val introduced =
                WorkspaceEditor.issues(workspace, environment.editContext).filterNot {
                    it in known.getValue(workspace.id)
                }
            assertTrue("issues $introduced: $label", introduced.isEmpty())
        }
        state.flow?.state?.ref?.let { ref ->
            assertEquals("flow ref: $label", session.scope.library.find(ref)?.lens, state.flow?.state?.draft?.toLens())
        }
    }

    private companion object {
        const val SEEDS = 80
        const val OPERATIONS = 60
    }
}
