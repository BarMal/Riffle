package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Seeded random walks: whatever is tapped, a finished wizard or walkthrough applies to a valid layout. */
class WizardPropertyTest {
    private val entries =
        listOf(
            WizardEntry.Editor(MAIN_ID),
            WizardEntry.Editor(MAIN_ID, ContainerId("grid")),
            WizardEntry.NewSavedLens,
            WizardEntry.FromSource(SourceIds.NOTIFICATIONS, MAIN_ID),
            WizardEntry.FromSource(SourceIds.RSS, MAIN_ID),
        )

    private fun randomWizardAction(random: Random): WizardAction {
        val goal = ContentGoalId.entries.random(random)
        val anyGoal = ContentGoalCatalog.goal(goal)
        return when (random.nextInt(12)) {
            0, 1 -> WizardAction.ChooseGoal(goal)
            2 -> WizardAction.ChooseFilter(anyGoal.filters.random(random).id)
            3 -> WizardAction.ToggleApp(app(listOf("a", "b", "c").random(random)))
            4 -> WizardAction.ToggleFeed("f1")
            5 -> WizardAction.SetQuery(listOf("", "news", "  x ").random(random))
            6 -> WizardAction.ChooseLook(anyGoal.looks.random(random).id)
            7 -> WizardAction.ChooseDestination(Destination.entries.random(random))
            8 -> WizardAction.SetSaveToLibrary(random.nextBoolean())
            9 -> WizardAction.SetName(listOf("", "Mine", "Notifications, by app").random(random))
            10 -> WizardAction.Back
            else -> WizardAction.Next
        }
    }

    @Test
    fun aFinishedWizardAlwaysAppliesToAValidLayout() {
        var finished = 0
        repeat(400) { seed ->
            val random = Random(seed)
            val access =
                if (random.nextBoolean()) {
                    emptyMap()
                } else {
                    mapOf(
                        SourceIds.NOTIFICATIONS to SourceAccess.REQUIRED,
                    )
                }
            val ctx = wizardContext(entries.random(random), access = access)
            var state = WizardFlow.start(ctx)
            repeat(40) { if (state.exit == null) state = WizardReducer.reduce(state, randomWizardAction(random), ctx) }
            if (state.exit == WizardExit.Finished) {
                finished++
                val result =
                    WizardApply.apply(
                        state,
                        ctx,
                        WizardTarget(ctx.layout, existingPool(), listOf(MAIN_ID, MIGRATED), ids()),
                    )
                val applied = assertIs<WizardResult.Applied>(result, "seed $seed: $result")
                applied.layout.workspaces.forEach {
                    assertEquals(emptyList(), WorkspaceValidation.validate(it, sources = ctx.descriptors), "seed $seed")
                }
                assertEquals(emptyList(), LensLibraryOps.danglingRefs(applied.layout), "seed $seed")
            } else if (state.exit == null && state.step == WizardStep.DONE) {
                fail("seed $seed: DONE without an exit")
            }
        }
        assertTrue(finished > 20, "only $finished walks finished")
    }

    private fun randomWalkthroughAction(random: Random): WalkthroughAction {
        val goal = ContentGoalId.entries.random(random)
        val anyGoal = ContentGoalCatalog.goal(goal)
        val permission = SourcePermission.entries.random(random)
        return when (random.nextInt(14)) {
            0, 1 -> WalkthroughAction.ToggleGoal(goal)
            2 -> WalkthroughAction.SetRole(goal, GoalRole.entries.random(random))
            3 -> WalkthroughAction.MoveGoal(goal, random.nextInt(-1, 8))
            4 -> WalkthroughAction.SetLook(goal, anyGoal.looks.random(random).id)
            5 -> WalkthroughAction.ToggleApp(goal, app(listOf("a", "b").random(random)))
            6 -> WalkthroughAction.SetStartGoal(goal)
            7 -> WalkthroughAction.ToggleDockPin(app(listOf("a", "b").random(random)))
            8 -> WalkthroughAction.ExplainPermission(permission)
            9 -> WalkthroughAction.RequestPermission(permission)
            10 -> WalkthroughAction.PermissionResult(permission, random.nextBoolean())
            11 -> WalkthroughAction.Back
            else -> WalkthroughAction.Next
        }
    }

    @Test
    fun aFinishedWalkthroughAlwaysAppliesWithoutLosingData() {
        var finished = 0
        repeat(300) { seed ->
            val random = Random(seed)
            val origin = OnboardingOrigin.entries.random(random)
            val ctx = walkthroughContext(origin, access = if (random.nextBoolean()) emptyMap() else NOTHING_GRANTED)
            var state = WalkthroughFlow.start(ctx)
            repeat(
                60,
            ) { if (state.exit == null) state = WalkthroughReducer.reduce(state, randomWalkthroughAction(random), ctx) }
            if (state.exit == WalkthroughExit.Finished) {
                finished++
                val target =
                    WalkthroughTarget(ctx.layout, ctx.pool, listOf(WalkthroughFlow.WORKSPACE_ID, MIGRATED), ids())
                val applied =
                    assertIs<WalkthroughResult.Applied>(WalkthroughApply.apply(state, ctx, target), "seed $seed")
                assertEquals(
                    emptyList(),
                    WorkspaceValidation.validate(applied.layout.active, sources = ctx.descriptors),
                    "seed $seed",
                )
                ctx.layout.workspaces.filter { it.id !in applied.removed }.forEach {
                    assertEquals(it, applied.layout.find(it.id), "seed $seed: ${it.name} changed")
                }
                if (origin == OnboardingOrigin.EXISTING_USER) assertEquals(emptyList(), applied.removed)
                ctx.pool.arrangements.forEach {
                        (id, arrangement) ->
                    assertEquals(arrangement, applied.pool.arrangements[id], "seed $seed")
                }
            }
        }
        assertTrue(finished > 10, "only $finished walks finished")
    }
}
