package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BindingFlowTest {
    private val base = workspace(boundPage("a"))

    private fun context(
        ws: com.riffle.core.domain.launcher.workspace.Workspace = base,
        mode: FlowMode = FlowMode.Add,
        access: Map<com.riffle.core.domain.launcher.workspace.SourceId, SourceAccess> = emptyMap(),
    ) = BindingFlowContext(choices(access), ws, mode, ids = counterIds())

    private fun run(
        context: BindingFlowContext,
        vararg actions: BindingFlowAction,
        from: BindingFlowState = BindingFlow.start(context),
    ) = actions.fold(from) { state, action -> BindingFlowReducer.reduce(state, action, context) }

    private fun enabledExpressions(
        state: BindingFlowState,
        context: BindingFlowContext,
    ) = BindingFlow.expressionChoices(state, context).filter { it.enabled }.map { it.kind }

    @Test
    fun cannotAdvanceWithoutASource() {
        val ctx = context()
        val state = run(ctx, BindingFlowAction.Next)
        assertEquals(EditorStep.SOURCE, state.step)
        assertFalse(BindingFlow.canAdvance(state, ctx))
    }

    @Test
    fun expressionStepOffersExactlyTheCompatibleExpressions() {
        val ctx = context()
        val state = run(ctx, BindingFlowAction.ToggleSource(APPS), BindingFlowAction.Next)
        assertEquals(EditorStep.EXPRESSION, state.step)
        val lens = assertNotNull(BindingFlow.lens(state))
        assertEquals(LensExpressionValidity.compatibleExpressions(lens, DESCRIPTORS), enabledExpressions(state, ctx))
        // Every expression is listed; incompatible ones say why.
        val all = BindingFlow.expressionChoices(state, ctx)
        assertEquals(ExpressionKind.entries, all.map { it.kind })
        val card = all.first { it.kind == ExpressionKind.CARD }
        assertFalse(card.enabled)
        assertTrue(card.lensIssues.isNotEmpty())
    }

    @Test
    fun disabledExpressionsCannotBePicked() {
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.CARD),
            )
        assertEquals(null, state.expression)
        assertFalse(BindingFlow.canAdvance(state, ctx))
    }

    @Test
    fun groupedLensOffersPageSetOnlyForPerGroupExpressions() {
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(NOTES),
                BindingFlowAction.ApplyPreset(LensPreset.GROUPED),
                BindingFlowAction.Next,
            )
        val choices = BindingFlow.expressionChoices(state, ctx).associateBy { it.kind }
        assertTrue(assertNotNull(choices[ExpressionKind.CATEGORIES]).enabled)
        assertFalse(assertNotNull(choices[ExpressionKind.CATEGORIES]).perGroupOnly)
        assertTrue(assertNotNull(choices[ExpressionKind.CARD_STACK]).perGroupOnly)
        assertTrue(assertNotNull(choices[ExpressionKind.CARD_STACK]).enabled)
        // Sideways scrolling cannot live in a pager.
        assertFalse(assertNotNull(choices[ExpressionKind.ICON_ROW]).enabled)

        val picked = run(ctx, BindingFlowAction.PickExpression(ExpressionKind.CARD_STACK), from = state)
        val containers = BindingFlow.containerChoices(picked, ctx).associateBy { it.kind }
        assertEquals(setOf(ContainerKind.PAGE_SET), containers.filterValues { it.enabled }.keys)
    }

    @Test
    fun pageSetIsOnlyOfferedForGroupedLenses() {
        val ctx = context()
        val flat =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
            )
        val containers = BindingFlow.containerChoices(flat, ctx).associateBy { it.kind }
        assertFalse(assertNotNull(containers[ContainerKind.PAGE_SET]).enabled)
        assertFalse(assertNotNull(containers[ContainerKind.FINDER_PAGE]).enabled)
        assertTrue(assertNotNull(containers[ContainerKind.PAGE]).enabled)
        assertTrue(assertNotNull(containers[ContainerKind.WIDGET]).enabled)
    }

    @Test
    fun finderIsOnlyOfferedForCategoriesOrAlphaListAndOnlyOnce() {
        val ctx = context()
        val alpha =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.ALPHA_LIST),
            )
        assertTrue(BindingFlow.containerChoices(alpha, ctx).first { it.kind == ContainerKind.FINDER_PAGE }.enabled)

        val withFinder =
            workspace(PageContainer(cid("f"), PageContent.Bound(binding(ExpressionKind.ALPHA_LIST)), PageRole.FINDER))
        val ctx2 = context(withFinder)
        val again =
            run(
                ctx2,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.ALPHA_LIST),
            )
        assertFalse(BindingFlow.containerChoices(again, ctx2).first { it.kind == ContainerKind.FINDER_PAGE }.enabled)
    }

    @Test
    fun widgetNeedsAFreeCellOnAnExistingPageOrFallsBackToANewPage() {
        val full =
            workspace(
                gridPage("g", 1, 1, WidgetPlacement(widget("w"), 0, 0)),
                gridPage("g2", 4, 6),
            )
        val ctx = context(full)
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.LIST),
                BindingFlowAction.Next,
            )
        val widget = BindingFlow.containerChoices(state, ctx).first { it.kind == ContainerKind.WIDGET }
        assertTrue(widget.enabled)
        assertEquals(listOf(cid("g2"), null), widget.widgetTargets.map { it.pageId })
    }

    @Test
    fun fullFlowAddsAPageSet() {
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(NOTES),
                BindingFlowAction.ApplyPreset(LensPreset.GROUPED),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.CARD_STACK),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.PAGE_SET),
                BindingFlowAction.Next,
            )
        assertEquals(EditorStep.CONFIRM, state.step)
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        assertIs<PageSetContainer>(ready.result.pages.last())
        assertEquals(2, ready.result.pages.size)
    }

    @Test
    fun fullFlowAddsAWidgetOnANewPage() {
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(CAL),
                BindingFlowAction.ApplyPreset(LensPreset.LATEST_ONE),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.CARD),
                BindingFlowAction.Next,
                BindingFlowAction.PickContainer(ContainerKind.WIDGET),
                BindingFlowAction.Next,
            )
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        val page = ready.result.pages.last() as PageContainer
        assertEquals(1, (page.content as PageContent.WidgetGrid).placements.size)
    }

    @Test
    fun changingTheLensDropsSelectionsItNoLongerAllows() {
        val ctx = context()
        var state = run(ctx, BindingFlowAction.ToggleSource(NOTES), BindingFlowAction.ApplyPreset(LensPreset.GROUPED))
        val categories = BindingFlowAction.PickExpression(ExpressionKind.CATEGORIES)
        state = run(ctx, BindingFlowAction.Next, categories, from = state)
        assertEquals(ExpressionKind.CATEGORIES, state.expression)
        state = run(ctx, BindingFlowAction.Back, BindingFlowAction.ApplyPreset(LensPreset.EVERYTHING), from = state)
        assertEquals(null, state.expression)
    }

    @Test
    fun lensActionsAreIgnoredOutsideTheSourceStep() {
        val ctx = context()
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.ToggleSource(NOTES),
            )
        assertEquals(listOf(APPS), state.draft.sources)
    }

    @Test
    fun groupedPresetsAreDisabledForNonGroupableSources() {
        val ctx = context()
        val state = run(ctx, BindingFlowAction.ToggleSource(CAL))
        val grouped = BindingFlow.presetChoices(state, ctx).first { it.preset == LensPreset.GROUPED }
        assertFalse(grouped.enabled)
        assertEquals(CAL, grouped.blockedBy)
        assertEquals(
            LensGroup.None,
            run(ctx, BindingFlowAction.ApplyPreset(LensPreset.GROUPED), from = state).draft.group,
        )
    }

    @Test
    fun permissionGatedSourcesAreFlaggedButSelectable() {
        val ctx = context(access = mapOf(CAL to SourceAccess.REQUIRED, NOTES to SourceAccess.UNAVAILABLE))
        val calendar = ctx.sources.first { it.id == CAL }
        assertTrue(calendar.needsPermission)
        assertTrue(calendar.selectable)
        assertFalse(ctx.sources.first { it.id == NOTES }.selectable)
        assertEquals(
            listOf(CAL),
            run(ctx, BindingFlowAction.ToggleSource(CAL), BindingFlowAction.ToggleSource(NOTES)).draft.sources,
        )
    }

    @Test
    fun customBuilderReachesTheFullLensModel() {
        val ctx = context()
        val state =
            run(ctx, BindingFlowAction.ToggleSource(APPS), BindingFlowAction.SetLimit(3), BindingFlowAction.SetLimit(0))
        assertEquals(null, state.draft.limit)
        assertEquals(null, state.draft.preset)
        val limited = run(ctx, BindingFlowAction.SetLimit(3), from = state)
        assertEquals(3, assertNotNull(BindingFlow.lens(limited)).limit)
    }

    @Test
    fun editingAPageSkipsTheContainerStepAndFiltersByThePage() {
        val ws = workspace(PageSetContainer(cid("s"), binding(ExpressionKind.INDEX, NOTES, LensGroup.ByGroupKey)))
        val ctx = context(ws, FlowMode.EditPage(cid("s")))
        val start = BindingFlow.start(ctx)
        assertEquals(ExpressionKind.INDEX, start.expression)
        assertEquals(LensPreset.GROUPED, start.draft.preset)

        val expression = run(ctx, BindingFlowAction.Next, from = start)
        // A page-set page accepts only per-group, vertical expressions.
        val enabled = enabledExpressions(expression, ctx)
        assertTrue(ExpressionKind.CARD_STACK in enabled)
        assertFalse(ExpressionKind.ICON_ROW in enabled)
        val confirm =
            run(
                ctx,
                BindingFlowAction.PickExpression(ExpressionKind.CARD_STACK),
                BindingFlowAction.Next,
                from = expression,
            )
        assertEquals(EditorStep.CONFIRM, confirm.step)
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(confirm, ctx))
        assertEquals(
            WorkspaceEdit.SetPageBinding(
                cid("s"),
                LensBinding(assertNotNull(BindingFlow.lens(confirm)), ExpressionKind.CARD_STACK),
            ),
            ready.edit,
        )
        assertEquals(EditorStep.EXPRESSION, run(ctx, BindingFlowAction.Back, from = confirm).step)
    }

    @Test
    fun editingADockSectionStartsEmptyWhenUnset() {
        val ctx = context(mode = FlowMode.EditDock)
        val start = BindingFlow.start(ctx)
        assertEquals(emptyList(), start.draft.sources)
        val state =
            run(
                ctx,
                BindingFlowAction.ToggleSource(APPS),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.ICON_ROW),
                BindingFlowAction.Next,
                from = start,
            )
        val ready = assertIs<FlowOutcome.Ready>(BindingFlow.confirm(state, ctx))
        assertNotNull(ready.result.dock.dynamicSection)
    }

    @Test
    fun confirmIsBlockedBeforeTheConfirmStep() {
        val ctx = context()
        assertIs<FlowOutcome.Blocked>(BindingFlow.confirm(BindingFlow.start(ctx), ctx))
    }
}
