package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensDraftReducerTest {
    private val sources =
        SourceChoices.build(
            listOf(
                SourceDescriptor(APPS, setOf(SourceCapability.GROUPABLE)),
                SourceDescriptor(CAL, setOf(SourceCapability.LIVE)),
                SourceDescriptor(SourceIds.SEARCH),
                SourceDescriptor(NOTES, setOf(SourceCapability.GROUPABLE)),
            ),
            mapOf(NOTES to SourceAccess.UNAVAILABLE),
        )

    private fun reduce(
        draft: LensDraft,
        action: LensDraftAction,
    ) = LensDraftReducer.reduce(draft, action, sources)

    @Test
    fun aSourceIsAddedOnlyWhenTheRegistryOffersItAndItIsSelectable() {
        val one = reduce(LensDraft(), LensDraftAction.ToggleSource(APPS))
        assertEquals(listOf(APPS), one.sources)

        assertEquals(one, reduce(one, LensDraftAction.ToggleSource(NOTES)), "unavailable source")
        assertEquals(one, reduce(one, LensDraftAction.ToggleSource(SourceId("unknown"))), "not in the registry")
        assertEquals(emptyList(), reduce(one, LensDraftAction.ToggleSource(APPS)).sources)
    }

    @Test
    fun aGroupedPresetIsRefusedWhenAChosenSourceCannotGroup() {
        val draft = LensDraft(sources = listOf(APPS, CAL))

        assertEquals(draft, reduce(draft, LensDraftAction.ApplyPreset(LensPreset.GROUPED)))
        val ok = reduce(LensDraft(sources = listOf(APPS)), LensDraftAction.ApplyPreset(LensPreset.GROUPED))
        assertEquals(LensGroup.ByGroupKey, ok.group)
        assertEquals(LensPreset.GROUPED, ok.preset)
    }

    @Test
    fun customControlsClearThePresetAndALimitBelowOneMeansNone() {
        val base = reduce(LensDraft(sources = listOf(APPS)), LensDraftAction.ApplyPreset(LensPreset.LATEST_FIVE))
        assertEquals(5, base.limit)

        val sorted = reduce(base, LensDraftAction.SetSort(LensSort(LensSortField.TITLE, SortDirection.DESCENDING)))
        assertNull(sorted.preset)
        assertEquals(SortDirection.DESCENDING, sorted.sort.direction)
        assertNull(reduce(base, LensDraftAction.SetLimit(0)).limit)
        assertEquals(3, reduce(base, LensDraftAction.SetLimit(3)).limit)
        assertEquals(LensFilter.HasActions(), reduce(base, LensDraftAction.SetFilter(LensFilter.HasActions())).filter)
    }

    @Test
    fun aQueryAppliesOnlyToAChosenSourceThatTakesOne() {
        val draft = LensDraft(sources = listOf(SourceIds.SEARCH, APPS))

        assertEquals(
            "cats",
            reduce(draft, LensDraftAction.SetQuery(SourceIds.SEARCH, "cats")).queryFor(SourceIds.SEARCH),
        )
        assertEquals(draft, reduce(draft, LensDraftAction.SetQuery(APPS, "cats")), "apps takes no query")
        assertEquals(draft, reduce(draft, LensDraftAction.SetQuery(CAL, "cats")), "not chosen")
        val cleared =
            reduce(
                reduce(draft, LensDraftAction.SetQuery(SourceIds.SEARCH, "cats")),
                LensDraftAction.SetQuery(SourceIds.SEARCH, ""),
            )
        assertEquals("", cleared.queryFor(SourceIds.SEARCH))
    }

    @Test
    fun removingASourceDropsItsQuery() {
        val withQuery =
            reduce(LensDraft(sources = listOf(SourceIds.SEARCH)), LensDraftAction.SetQuery(SourceIds.SEARCH, "x"))

        assertEquals(emptyMap(), reduce(withQuery, LensDraftAction.ToggleSource(SourceIds.SEARCH)).parameters)
    }

    @Test
    fun theFlowAndTheBuilderAgreeOnEveryLensActionAtTheSourceStep() {
        val workspace =
            Workspace(
                WorkspaceId("w"),
                "W",
                listOf(boundPage("p")),
            )
        val context = BindingFlowContext(sources, workspace)
        val ids = listOf(APPS, CAL, SourceIds.SEARCH, NOTES, SourceId("unknown"))
        val filters = listOf(LensFilter.All, LensFilter.HasActions())
        val sorts = listOf(LensSort(), LensSort(LensSortField.TITLE))
        val groups = listOf(LensGroup.None, LensGroup.ByGroupKey, LensGroup.ByDay)
        for (seed in 0 until 200) {
            val random = Random(seed)
            var draft = LensDraft()
            var state = BindingFlowState()
            repeat(25) {
                val action: LensDraftAction =
                    when (random.nextInt(7)) {
                        0 -> LensDraftAction.ToggleSource(ids[random.nextInt(ids.size)])
                        1 -> LensDraftAction.ApplyPreset(LensPreset.entries[random.nextInt(LensPreset.entries.size)])
                        2 -> LensDraftAction.SetFilter(filters[random.nextInt(filters.size)])
                        3 -> LensDraftAction.SetGroup(groups[random.nextInt(groups.size)])
                        4 -> LensDraftAction.SetSort(sorts[random.nextInt(sorts.size)])
                        5 -> LensDraftAction.SetLimit(random.nextInt(-1, 8))
                        else -> LensDraftAction.SetQuery(ids[random.nextInt(ids.size)], "q${random.nextInt(3)}")
                    }
                draft = LensDraftReducer.reduce(draft, action, sources)
                state = BindingFlowReducer.reduce(state, action.toFlowAction(), context)
                assertEquals(draft, state.draft, "seed $seed after $action")
            }
        }
    }

    @Test
    fun everyLensActionRoundTripsThroughTheFlowAction() {
        val actions =
            listOf(
                LensDraftAction.ToggleSource(APPS),
                LensDraftAction.ApplyPreset(LensPreset.A_TO_Z),
                LensDraftAction.SetFilter(LensFilter.All),
                LensDraftAction.SetGroup(LensGroup.ByDay),
                LensDraftAction.SetSort(LensSort()),
                LensDraftAction.SetLimit(2),
                LensDraftAction.SetQuery(APPS, "x"),
            )

        actions.forEach { assertEquals(it, it.toFlowAction().toDraftAction()) }
        assertNull(BindingFlowAction.Next.toDraftAction())
        assertNull(BindingFlowAction.Back.toDraftAction())
        assertNotEquals(null, BindingFlowAction.SetLimit(1).toDraftAction())
        assertTrue(BindingFlowAction.PickExpression(ExpressionKind.LIST).toDraftAction() == null)
    }
}
