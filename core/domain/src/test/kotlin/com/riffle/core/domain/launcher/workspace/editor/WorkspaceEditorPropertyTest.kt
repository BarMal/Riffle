package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceEditorPropertyTest {
    private val pageIds = (0..4).map { cid("p$it") }
    private val widgetIds = (0..5).map { cid("w$it") }
    private val sourcePool = listOf(APPS, NOTES, CAL)
    private val groups = listOf(LensGroup.None, LensGroup.ByGroupKey, LensGroup.ByDay)

    private fun Random.pick(ids: List<ContainerId>) = ids[nextInt(ids.size)]

    private fun randomBinding(random: Random) =
        LensBinding(
            lens(
                random.pickSource(),
                group = groups[random.nextInt(groups.size)],
                limit = listOf(null, 1, 5)[random.nextInt(3)],
            ),
            ExpressionKind.entries[random.nextInt(ExpressionKind.entries.size)],
        )

    private fun Random.pickSource(): SourceId = sourcePool[nextInt(sourcePool.size)]

    private fun randomPage(random: Random): PageHost {
        val id = random.pick(pageIds)
        return when (random.nextInt(4)) {
            0 -> PageSetContainer(id, randomBinding(random))
            1 -> PageContainer(id, PageContent.Bound(randomBinding(random)), PageRole.FINDER)
            2 -> PageContainer(id, PageContent.WidgetGrid(1 + random.nextInt(4), 1 + random.nextInt(5), emptyList()))
            else -> PageContainer(id, PageContent.Bound(randomBinding(random)))
        }
    }

    private fun randomEdit(random: Random): WorkspaceEdit =
        when (random.nextInt(13)) {
            0, 1 -> WorkspaceEdit.AddPage(randomPage(random), random.nextInt(-1, 6))
            2 -> WorkspaceEdit.RemovePage(random.pick(pageIds))
            3 -> WorkspaceEdit.MovePage(random.pick(pageIds), random.nextInt(-2, 8))
            4 -> WorkspaceEdit.SetPageBinding(random.pick(pageIds), randomBinding(random))
            5, 6 -> {
                val span = WidgetSpan(1 + random.nextInt(3), 1 + random.nextInt(3))
                WorkspaceEdit.AddWidget(
                    random.pick(pageIds),
                    com.riffle.core.domain.launcher.workspace.WidgetContainer(
                        random.pick(widgetIds),
                        span,
                        randomBinding(random),
                    ),
                    random.nextInt(-1, 5),
                    random.nextInt(-1, 6),
                )
            }
            7 ->
                WorkspaceEdit.MoveWidget(
                    random.pick(pageIds),
                    random.pick(widgetIds),
                    random.nextInt(-1, 5),
                    random.nextInt(-1, 6),
                )
            8 ->
                WorkspaceEdit.ResizeWidget(
                    random.pick(pageIds),
                    random.pick(widgetIds),
                    WidgetSpan(1 + random.nextInt(4), 1 + random.nextInt(4)),
                )
            9 -> WorkspaceEdit.SetWidgetBinding(random.pick(pageIds), random.pick(widgetIds), randomBinding(random))
            10 -> WorkspaceEdit.RemoveWidget(random.pick(pageIds), random.pick(widgetIds))
            11 -> WorkspaceEdit.SetDockSection(if (random.nextBoolean()) randomBinding(random) else null)
            else -> WorkspaceEdit.Rename(listOf("", " ", "Home", "Work")[random.nextInt(4)])
        }

    @Test
    fun randomEditSequencesNeverProduceAnInvalidWorkspace() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            var session = WorkspaceEditSession(workspace(boundPage("start")))
            assertTrue(WorkspaceEditor.issues(session.draft, EDIT_CONTEXT).isEmpty())
            var applied = 0
            repeat(STEPS) {
                val before = session.draft
                val step = session.apply(randomEdit(random), EDIT_CONTEXT)
                session = step.session
                when (val result = step.result) {
                    is EditResult.Applied -> applied++
                    is EditResult.Rejected ->
                        assertEquals(
                            before,
                            session.draft,
                            "seed $seed: rejected edit changed the draft",
                        )
                }
                val issues = WorkspaceEditor.issues(session.draft, EDIT_CONTEXT)
                assertTrue(issues.isEmpty(), "seed $seed: $issues in ${session.draft}")
                if (random.nextInt(10) == 0) session = session.undo()
                if (random.nextInt(15) == 0) session = session.redo()
            }
            assertTrue(applied > 0, "seed $seed applied nothing; the generator is too hostile to test anything")
        }
    }

    @Test
    fun everyOfferedChoiceIsAcceptedAndEveryRefusedOneIsNot() {
        repeat(SEEDS) { seed ->
            val random = Random(1000 + seed)
            var ws: Workspace = workspace(boundPage("start"))
            repeat(STEPS / 4) {
                (
                    WorkspaceEditor.apply(
                        ws,
                        randomEdit(random),
                        EDIT_CONTEXT,
                    ) as? EditResult.Applied
                )?.let { ws = it.workspace }
                val ctx = BindingFlowContext(choices(), ws, ids = counterIds("g$seed"))
                var state = BindingFlowState()
                val steps = random.nextInt(4, 14)
                repeat(steps) { state = BindingFlowReducer.reduce(state, randomAction(random), ctx) }
                assertFlowInvariants(seed, state, ctx)
            }
        }
    }

    private fun randomAction(random: Random): BindingFlowAction =
        when (random.nextInt(9)) {
            0, 1 -> BindingFlowAction.ToggleSource(random.pickSource())
            2 -> BindingFlowAction.ApplyPreset(LensPreset.entries[random.nextInt(LensPreset.entries.size)])
            3 -> BindingFlowAction.SetLimit(random.nextInt(-1, 4))
            4, 5 ->
                BindingFlowAction.PickExpression(
                    ExpressionKind.entries[random.nextInt(ExpressionKind.entries.size)],
                )
            6 -> BindingFlowAction.PickContainer(ContainerKind.entries[random.nextInt(ContainerKind.entries.size)])
            else -> BindingFlowAction.Next
        }

    private fun assertFlowInvariants(
        seed: Int,
        state: BindingFlowState,
        ctx: BindingFlowContext,
    ) {
        val lens = BindingFlow.lens(state)
        if (lens != null) {
            val offered =
                BindingFlow.expressionChoices(
                    state,
                    ctx,
                ).filter { it.enabled && !it.perGroupOnly }.map { it.kind }
            assertEquals(LensExpressionValidity.compatibleExpressions(lens, DESCRIPTORS), offered, "seed $seed")
        }
        // Selections are always among the enabled options.
        state.expression?.let { kind ->
            assertTrue(
                BindingFlow.expressionChoices(state.copy(step = EditorStep.EXPRESSION), ctx).first {
                    it.kind == kind
                }.enabled,
            )
        }
        BindingFlow.containerChoices(state, ctx).forEach { choice ->
            val edit =
                state.expression?.let {
                    ContainerEdits.build(
                        choice.kind,
                        LensBinding(checkNotNull(lens), it),
                        choice.widgetTargets.firstOrNull(),
                    )
                }
            if (edit != null) {
                val accepted = WorkspaceEditor.apply(ctx.workspace, edit, ctx.edit) is EditResult.Applied
                assertEquals(choice.enabled, accepted, "seed $seed ${choice.kind}")
            }
        }
        if (state.step == EditorStep.CONFIRM) {
            val ready = BindingFlow.confirm(state, ctx)
            assertTrue(ready is FlowOutcome.Ready, "seed $seed: confirm blocked with $ready")
            assertTrue(WorkspaceEditor.issues((ready as FlowOutcome.Ready).result, ctx.edit).isEmpty())
        }
    }

    private companion object {
        const val SEEDS = 60
        const val STEPS = 80
    }
}
