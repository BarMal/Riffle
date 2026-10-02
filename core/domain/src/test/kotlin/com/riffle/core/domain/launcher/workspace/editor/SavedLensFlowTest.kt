package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedLensFlowTest {
    private val latest = LensDraft(sources = listOf(APPS)).withPreset(LensPreset.LATEST_FIVE).toLens()!!
    private val perApp = lens(NOTES, group = LensGroup.ByGroupKey)
    private val ghost = lens(SourceId("ghost"))
    private val ids = counterIds("lens")

    private var library = LensLibrary()

    private fun save(
        name: String,
        lens: Lens,
    ): LensId {
        val added = assertIs<LibraryAdd.Added>(library.tryAdd(name, lens, ids))
        library = added.library
        return added.id
    }

    private fun context(
        workspace: Workspace = workspace(boundPage("a")),
        mode: FlowMode = FlowMode.Add,
        others: List<Workspace> = emptyList(),
        access: Map<SourceId, SourceAccess> = emptyMap(),
        descriptors: List<SourceDescriptor> = DESCRIPTORS,
    ) = BindingFlowContext(
        SourceChoices.build(descriptors, access),
        workspace,
        mode,
        ids = counterIds("n"),
        scope = LensScope(library, others),
    )

    private fun run(
        context: BindingFlowContext,
        vararg actions: BindingFlowAction,
        from: BindingFlowState = BindingFlow.start(context),
    ) = actions.fold(from) { state, action -> BindingFlowReducer.reduce(state, action, context) }

    @Test
    fun choicesAreSortedByNameWithUsedInCounts() {
        val b = save("b notes", perApp)
        save("A latest", latest)
        val user = workspace(boundPage("x", LensBinding(perApp, ExpressionKind.INDEX, b)))
        val choices = SavedLensFlow.choices(context(user))
        assertEquals(listOf("A latest", "b notes"), choices.map { it.saved.name })
        assertEquals(listOf(0, 1), choices.map { it.usedIn })
        assertTrue(choices.all { it.enabled })
    }

    @Test
    fun usedInCountsEveryWorkspaceOfTheLayout() {
        val id = save("Latest", latest)
        val bound = LensBinding(latest, ExpressionKind.LIST, id)
        val other =
            workspace(
                boundPage("o", bound),
            ).copy(id = com.riffle.core.domain.launcher.workspace.WorkspaceId("o"))
        val choices = SavedLensFlow.choices(context(workspace(boundPage("a", bound)), others = listOf(other)))
        assertEquals(2, choices.single().usedIn)
    }

    @Test
    fun aLensWithAnUnavailableOrUnknownSourceIsVisibleButDisabled() {
        save("Calendar", lens(CAL))
        save("Ghost", ghost)
        val choices = SavedLensFlow.choices(context(access = mapOf(CAL to SourceAccess.UNAVAILABLE)))
        assertEquals(
            listOf(CAL, SourceId("ghost")),
            choices.map {
                assertIs<SavedLensBlock.UnavailableSource>(it.block).source
            },
        )
        assertTrue(choices.none { it.enabled })
    }

    @Test
    fun aFlatLensCannotBeUsedForAPageSetAndTheReasonCarriesTheDomainIssues() {
        save("Latest", latest)
        val setPage = PageSetContainer(cid("set"), LensBinding(perApp, ExpressionKind.CARD_STACK))
        val choice = SavedLensFlow.choices(context(workspace(setPage), FlowMode.EditPage(cid("set")))).single()
        val block = assertIs<SavedLensBlock.CannotDraw>(choice.block)
        assertNotNull(block.nearest)
        assertTrue(block.nearest.containerIssues.isNotEmpty())
        assertFalse(choice.enabled)
    }

    @Test
    fun aGroupedLensIsEnabledForAPageSet() {
        save("Per app", perApp)
        val setPage = PageSetContainer(cid("set"), LensBinding(perApp, ExpressionKind.CARD_STACK))
        assertTrue(SavedLensFlow.choices(context(workspace(setPage), FlowMode.EditPage(cid("set")))).single().enabled)
    }

    @Test
    fun usingASavedLensFillsTheDraftAndKeepsTheReference() {
        val id = save("Latest", latest)
        val ctx = context()
        val state = run(ctx, BindingFlowAction.ShowSavedLenses, BindingFlowAction.UseSavedLens(id))
        assertEquals(id, state.ref)
        assertFalse(state.choosingSaved)
        assertEquals(latest, BindingFlow.lens(state))
        assertEquals(id, SavedLensFlow.current(state, ctx)?.id)
        assertTrue(BindingFlow.canAdvance(state, ctx))
    }

    @Test
    fun aDisabledOrUnknownSavedLensIsIgnored() {
        val blocked = save("Ghost", ghost)
        val ctx = context()
        val initial = BindingFlow.start(ctx)
        assertEquals(initial, run(ctx, BindingFlowAction.UseSavedLens(blocked)))
        assertEquals(initial, run(ctx, BindingFlowAction.UseSavedLens(LensId("nope"))))
    }

    @Test
    fun usingIsOnlyPossibleOnTheSourceStep() {
        val id = save("Latest", latest)
        val ctx = context()
        val atExpression = run(ctx, BindingFlowAction.ToggleSource(APPS), BindingFlowAction.Next)
        assertEquals(atExpression, run(ctx, BindingFlowAction.UseSavedLens(id), from = atExpression))
    }

    @Test
    fun lensEditsAreIgnoredWhileTheBindingUsesASavedLensAndWorkAfterDetaching() {
        val id = save("Latest", latest)
        val ctx = context()
        val using = run(ctx, BindingFlowAction.UseSavedLens(id))
        assertEquals(using, run(ctx, BindingFlowAction.ToggleSource(NOTES), from = using))
        assertEquals(using, run(ctx, BindingFlowAction.ApplyPreset(LensPreset.A_TO_Z), from = using))
        val detached = run(ctx, BindingFlowAction.DetachSavedLens, from = using)
        assertNull(detached.ref)
        assertEquals(latest, BindingFlow.lens(detached))
        val edited = run(ctx, BindingFlowAction.ApplyPreset(LensPreset.A_TO_Z), from = detached)
        assertEquals(LensPreset.A_TO_Z, edited.draft.preset)
    }

    @Test
    fun startingOnAReferencedBindingKeepsTheReferenceAndADanglingOneBecomesInline() {
        val id = save("Latest", latest)
        val page = boundPage("p", LensBinding(latest, ExpressionKind.LIST, id))
        val ctx = context(workspace(page), FlowMode.EditPage(cid("p")))
        assertEquals(id, BindingFlow.start(ctx).ref)
        val dangling = boundPage("p", LensBinding(latest, ExpressionKind.LIST, LensId("gone")))
        assertNull(BindingFlow.start(context(workspace(dangling), FlowMode.EditPage(cid("p")))).ref)
    }

    @Test
    fun confirmingAnEditKeepsTheReference() {
        val id = save("Latest", latest)
        val page = boundPage("p", LensBinding(latest, ExpressionKind.LIST, id))
        val ctx = context(workspace(page), FlowMode.EditPage(cid("p")))
        val state =
            run(
                ctx,
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.ICON_GRID),
                BindingFlowAction.Next,
            )
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        val edit = assertIs<WorkspaceEdit.SetPageBinding>(ready.edit)
        assertEquals(LensBinding(latest, ExpressionKind.ICON_GRID, id), edit.binding)
        assertNull(ready.saved)
    }

    @Test
    fun addingWithASavedLensBindsTheNewContainerToIt() {
        val id = save("Latest", latest)
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.UseSavedLens(id),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE),
                BindingFlowAction.Next,
            )
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        val added = ready.result.pages.last()
        val binding = ((added as PageContainer).content as PageContent.Bound).binding
        assertEquals(id, binding.ref)
    }

    @Test
    fun anUnreferencedRefIsNeverWritten() {
        val id = save("Latest", latest)
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.UseSavedLens(id),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
            )
        // The saved lens disappears (an Undo, say) before the flow is confirmed: reconcile drops the reference.
        val gone = context().copy(scope = LensScope())
        assertNull(SavedLensFlow.reconcile(state, gone).ref)
        assertEquals(state, SavedLensFlow.reconcile(state, ctx))
        val confirm =
            run(
                gone,
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE),
                BindingFlowAction.Next,
                from = state,
            )
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(confirm, gone))
        val binding = (((ready.result.pages.last()) as PageContainer).content as PageContent.Bound).binding
        assertNull(binding.ref)
    }

    private fun atConfirm(
        ctx: BindingFlowContext,
        vararg extra: BindingFlowAction,
    ) = run(
        ctx,
        BindingFlowAction.ToggleSource(APPS),
        BindingFlowAction.ApplyPreset(LensPreset.LATEST_FIVE),
        BindingFlowAction.Next,
        BindingFlowAction.PickExpression(ExpressionKind.LIST),
        BindingFlowAction.Next,
        BindingFlowAction.PickContainer(ContainerKind.PAGE),
        BindingFlowAction.Next,
        *extra,
    )

    @Test
    fun saveAsLensSuggestsAUniqueNameAndOnlyOnTheConfirmStep() {
        save("Latest apps", lens(CAL))
        val ctx = context()
        val source = run(ctx, BindingFlowAction.ToggleSource(APPS), BindingFlowAction.StartSaveAsLens("Latest apps"))
        assertNull(source.saveAs)
        val state = atConfirm(ctx, BindingFlowAction.StartSaveAsLens("Latest apps"))
        assertEquals("Latest apps 2", state.saveAs?.name)
        assertNull(SavedLensFlow.saveAsProblem(state, ctx))
    }

    @Test
    fun saveAsLensIsNotOfferedForASavedLensOrAFullLibrary() {
        val id = save("Latest", latest)
        val ctx = context()
        val using =
            run(
                ctx,
                BindingFlowAction.UseSavedLens(id),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE),
                BindingFlowAction.Next,
            )
        assertFalse(SavedLensFlow.canSaveAs(using, ctx))
        assertNull(run(ctx, BindingFlowAction.StartSaveAsLens("x"), from = using).saveAs)

        library = LensLibrary()
        repeat(MAX_SAVED_LENSES) { save("Lens $it", lens(APPS, limit = it + 1)) }
        val full = context()
        val state = atConfirm(full)
        assertFalse(SavedLensFlow.canSaveAs(state, full))
        assertNull(run(full, BindingFlowAction.StartSaveAsLens("x"), from = state).saveAs)
    }

    @Test
    fun nameProblemsBlockConfirmWithTheLibrarysReason() {
        save("Taken", lens(CAL))
        val ctx = context()
        val state = atConfirm(ctx, BindingFlowAction.StartSaveAsLens("New"))
        val cases =
            listOf(
                "" to LibraryProblem.BLANK_NAME,
                "  " to LibraryProblem.BLANK_NAME,
                "taken" to LibraryProblem.NAME_TAKEN,
                "x".repeat(41) to LibraryProblem.NAME_TOO_LONG,
            )
        cases.forEach { (name, problem) ->
            val named = run(ctx, BindingFlowAction.SetSaveAsName(name), from = state)
            assertEquals(problem, SavedLensFlow.saveAsProblem(named, ctx), name)
            val blocked = assertIs<FlowOutcome.Blocked>(BindingFlow.confirm(named, ctx))
            assertEquals(problem, blocked.problem)
        }
        val fine = run(ctx, BindingFlowAction.SetSaveAsName("  Fresh  "), from = state)
        assertNull(SavedLensFlow.saveAsProblem(fine, ctx))
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(fine, ctx))
        assertEquals("Fresh", ready.saved?.name)
    }

    @Test
    fun confirmWithSaveAsReturnsTheLibraryAndABindingThatReferencesIt() {
        val ctx = context()
        val state = atConfirm(ctx, BindingFlowAction.StartSaveAsLens("Latest apps"))
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        val plan = assertNotNull(ready.saved)
        assertEquals(latest, plan.library.find(plan.id)?.lens)
        val binding = (((ready.result.pages.last()) as PageContainer).content as PageContent.Bound).binding
        assertEquals(plan.id, binding.ref)
        assertEquals(latest, binding.lens)
    }

    @Test
    fun cancellingSaveAsConfirmsAnInlineBinding() {
        val ctx = context()
        val state = atConfirm(ctx, BindingFlowAction.StartSaveAsLens("Latest apps"), BindingFlowAction.CancelSaveAsLens)
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        assertNull(ready.saved)
        assertNull((((ready.result.pages.last()) as PageContainer).content as PageContent.Bound).binding.ref)
    }
}
