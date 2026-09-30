package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = SourceId("a")
private val B = SourceId("b")
private val PIN = PINNED_EXT_KEY
private val KIND = ItemExtKey("demo.kind")
private const val HOUR = 3_600_000L
private const val NOW = 100 * HOUR

@Suppress("LongParameterList")
private fun item(
    id: String,
    source: SourceId = A,
    title: String? = id,
    time: Long? = null,
    groupKey: String? = null,
    privacy: ItemPrivacy = ItemPrivacy.VISIBLE,
    ext: Map<ItemExtKey, ItemExtValue> = emptyMap(),
    actions: List<ItemAction> = emptyList(),
) = Item(
    id = ItemId(id),
    sourceId = source,
    target = ItemTarget.None,
    title = title,
    timeEpochMillis = time,
    groupKey = groupKey,
    groupLabel = groupKey,
    privacy = privacy,
    ext = ext,
    actions = actions,
)

private fun eval(
    lens: Lens,
    items: List<Item>,
    context: LensEvaluationContext = LensEvaluationContext(nowEpochMillis = NOW),
) = DefaultLensEvaluator.evaluate(lens, items, context)

private fun LensResult.flatIds(): List<String> = (this as LensResult.Flat).items.map { it.id.value }

class LensEvaluatorTest {
    private val lens = Lens(sources = listOf(A))

    @Test
    fun defaultLensReturnsSourceOrderFlat() {
        val result = eval(lens, listOf(item("z"), item("a"), item("m")))
        assertEquals(listOf("z", "a", "m"), result.flatIds())
    }

    @Test
    fun filterAllOfAnyOfNot() {
        val items =
            listOf(
                item("1", groupKey = "x"),
                item("2", groupKey = "y", actions = listOf(ItemAction.Open())),
                item("3", groupKey = "x", actions = listOf(ItemAction.Open())),
            )

        fun ids(filter: LensFilter) = eval(lens.copy(filter = filter), items).flatIds()
        assertEquals(listOf("3"), ids(LensFilter.AllOf(listOf(LensFilter.GroupKeyIs("x"), LensFilter.HasActions()))))
        assertEquals(
            listOf("1", "2", "3"),
            ids(LensFilter.AnyOf(listOf(LensFilter.GroupKeyIs("x"), LensFilter.GroupKeyIs("y")))),
        )
        assertEquals(
            listOf("1"),
            ids(LensFilter.AllOf(listOf(LensFilter.GroupKeyIs("x"), LensFilter.Not(LensFilter.HasActions())))),
        )
        assertEquals(listOf("1"), ids(LensFilter.HasActions(required = false)))
        assertEquals(listOf("1", "2", "3"), ids(LensFilter.AllOf(emptyList())))
        assertEquals(emptyList(), ids(LensFilter.AnyOf(emptyList())))
    }

    @Test
    fun filterLeaves() {
        val items =
            listOf(
                item("a1", source = A, ext = mapOf(KIND to ItemExtValue.Text("k"))),
                item("b1", source = B, privacy = ItemPrivacy.SENSITIVE),
            )
        val both = Lens(sources = listOf(A, B))
        assertEquals(listOf("b1"), eval(both.copy(filter = LensFilter.FromSource(B)), items).flatIds())
        assertEquals(
            listOf("b1"),
            eval(both.copy(filter = LensFilter.PrivacyIs(ItemPrivacy.SENSITIVE)), items).flatIds(),
        )
        val kind = LensFilter.ExtEquals(KIND, ItemExtValue.Text("k"))
        assertEquals(listOf("a1"), eval(both.copy(filter = kind), items).flatIds())
        val other = LensFilter.ExtEquals(KIND, ItemExtValue.Text("other"))
        assertEquals(emptyList(), eval(both.copy(filter = other), items).flatIds())
    }

    @Test
    fun ageFiltersUseInjectedNowAndBoundaryIsInclusive() {
        val items =
            listOf(
                item("fresh", time = NOW - HOUR),
                item("old", time = NOW - 5 * HOUR),
                item("none", time = null),
                item("future", time = NOW + HOUR),
            )
        val atMost = eval(lens.copy(filter = LensFilter.AgeAtMost(HOUR)), items).flatIds()
        assertEquals(listOf("fresh", "future"), atMost)
        val atLeast = eval(lens.copy(filter = LensFilter.AgeAtLeast(5 * HOUR)), items).flatIds()
        assertEquals(listOf("old"), atLeast)
        val later = LensEvaluationContext(nowEpochMillis = NOW + 10 * HOUR)
        assertEquals(emptyList(), eval(lens.copy(filter = LensFilter.AgeAtMost(HOUR)), items, later).flatIds())
    }

    @Test
    fun overDeepFilterFailsClosed() {
        var filter: LensFilter = LensFilter.All
        repeat(100) { filter = LensFilter.Not(LensFilter.Not(filter)) }
        assertEquals(emptyList(), eval(lens.copy(filter = filter), listOf(item("a"))).flatIds())
        val shallow = LensFilter.Not(LensFilter.Not(LensFilter.All))
        assertEquals(listOf("a"), eval(lens.copy(filter = shallow), listOf(item("a"))).flatIds())
    }

    @Test
    fun sortByTitleIsCaseInsensitiveWithNullsLastInBothDirections() {
        val items =
            listOf(
                item("1", title = "banana"),
                item("2", title = "Apple"),
                item("3", title = null),
                item("4", title = "cherry"),
            )
        val asc = lens.copy(sort = LensSort(LensSortField.TITLE))
        assertEquals(listOf("2", "1", "4", "3"), eval(asc, items).flatIds())
        val desc = lens.copy(sort = LensSort(LensSortField.TITLE, SortDirection.DESCENDING))
        assertEquals(listOf("4", "1", "2", "3"), eval(desc, items).flatIds())
    }

    @Test
    fun sortByTimeAndTiesBreakByIdInBothDirections() {
        val items = listOf(item("c", time = 5), item("a", time = 5), item("b", time = 9), item("n", time = null))
        val asc = lens.copy(sort = LensSort(LensSortField.TIME))
        assertEquals(listOf("a", "c", "b", "n"), eval(asc, items).flatIds())
        val desc = lens.copy(sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING))
        assertEquals(listOf("b", "a", "c", "n"), eval(desc, items).flatIds())
    }

    @Test
    fun sourceOrderDescendingReversesMergedOrder() {
        val desc = lens.copy(sort = LensSort(LensSortField.SOURCE_ORDER, SortDirection.DESCENDING))
        assertEquals(listOf("c", "b", "a"), eval(desc, listOf(item("a"), item("b"), item("c"))).flatIds())
    }

    @Test
    fun pinnedFirstUsesNamespacedFlagAndKeepsFieldOrderWithinBands() {
        val pinned = mapOf(PIN to ItemExtValue.Flag(true))
        val notPinned = mapOf(PIN to ItemExtValue.Flag(false))
        val items =
            listOf(
                item("a", title = "a"),
                item("b", title = "b", ext = pinned),
                item("c", title = "c", ext = notPinned),
                item("d", title = "d", ext = pinned),
            )
        val asc = lens.copy(sort = LensSort(LensSortField.TITLE, pinnedFirst = true))
        assertEquals(listOf("b", "d", "a", "c"), eval(asc, items).flatIds())
        val desc = lens.copy(sort = LensSort(LensSortField.TITLE, SortDirection.DESCENDING, pinnedFirst = true))
        assertEquals(listOf("d", "b", "c", "a"), eval(desc, items).flatIds())
        assertEquals(listOf("a", "b", "c", "d"), eval(lens.copy(sort = LensSort(LensSortField.TITLE)), items).flatIds())
    }

    @Test
    fun sensitiveItemsAreNeverPinnedAndSortAsTitleless() {
        val items =
            listOf(
                item("s", title = "AAA", privacy = ItemPrivacy.SENSITIVE, ext = mapOf(PIN to ItemExtValue.Flag(true))),
                item("v", title = "zzz"),
            )
        val sorted = lens.copy(sort = LensSort(LensSortField.TITLE, pinnedFirst = true))
        assertEquals(listOf("v", "s"), eval(sorted, items).flatIds())
    }

    @Test
    fun limitAppliesAfterSortAndToTotal() {
        val items = (1..5).map { item("i$it", time = it.toLong()) }
        val newest = lens.copy(sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING), limit = 2)
        assertEquals(listOf("i5", "i4"), eval(newest, items).flatIds())
        assertEquals(5, eval(lens.copy(limit = 50), items).flatIds().size)
    }

    @Test
    fun limitOnGroupedResultIsTotalAcrossGroups() {
        val items =
            listOf(
                item("1", groupKey = "g1"),
                item("2", groupKey = "g1"),
                item("3", groupKey = "g2"),
                item("4", groupKey = "g2"),
            )
        val result = eval(lens.copy(group = LensGroup.ByGroupKey, limit = 3), items) as LensResult.Grouped
        assertEquals(listOf(listOf("1", "2"), listOf("3")), result.groups.map { g -> g.items.map { it.id.value } })
    }

    @Test
    fun groupByGroupKeyOrdersByFirstAppearanceAndLabelsFallBackToKey() {
        val items =
            listOf(
                item("1", groupKey = "g2").copy(groupLabel = null),
                item("2", groupKey = "g1").copy(groupLabel = "Group One"),
                item("3", groupKey = "g2").copy(groupLabel = null),
                item("4", groupKey = null),
                item("5", groupKey = "  "),
            )
        val result = eval(lens.copy(group = LensGroup.ByGroupKey), items) as LensResult.Grouped
        assertEquals(listOf("g2", "g1", ""), result.groups.map { it.key })
        assertEquals(listOf("g2", "Group One", null), result.groups.map { it.label })
        assertEquals(listOf("4", "5"), result.groups.last().items.map { it.id.value })
    }

    @Test
    fun groupByDayUsesInjectedZone() {
        val t = 1_700_000_000_000L // 2023-11-14T22:13:20Z
        val items = listOf(item("late", time = t), item("next", time = t + 3 * HOUR), item("none"))
        val utc = eval(lens.copy(group = LensGroup.ByDay), items) as LensResult.Grouped
        assertEquals(listOf("2023-11-14", "2023-11-15", ""), utc.groups.map { it.key })
        assertEquals("2023-11-14", utc.groups.first().label)
        val sydney = LensEvaluationContext(NOW, ZoneDayBucketer(ZoneId.of("Australia/Sydney")))
        val shifted = eval(lens.copy(group = LensGroup.ByDay), items, sydney) as LensResult.Grouped
        assertEquals(listOf("2023-11-15", ""), shifted.groups.map { it.key })
    }

    @Test
    fun groupByDayDescendingPutsNewestDayFirst() {
        val day = 24 * HOUR
        val items = listOf(item("old", time = day), item("new", time = 3 * day))
        val newest = lens.copy(group = LensGroup.ByDay, sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING))
        val result = eval(newest, items) as LensResult.Grouped
        assertEquals(listOf("1970-01-04", "1970-01-02"), result.groups.map { it.key })
    }

    @Test
    fun groupByExtStringifiesValuesAndSkipsSensitiveItems() {
        val items =
            listOf(
                item("1", ext = mapOf(KIND to ItemExtValue.Text("x"))),
                item("2", ext = mapOf(KIND to ItemExtValue.Number(7))),
                item("3", ext = mapOf(KIND to ItemExtValue.Flag(true))),
                item("4", ext = mapOf(KIND to ItemExtValue.Text("x")), privacy = ItemPrivacy.SENSITIVE),
                item("5"),
            )
        val result = eval(lens.copy(group = LensGroup.ByExt(KIND)), items) as LensResult.Grouped
        assertEquals(listOf("x", "7", "true", ""), result.groups.map { it.key })
        assertEquals(listOf("4", "5"), result.groups.last().items.map { it.id.value })
    }

    @Test
    fun projectStripsUnprojectedFieldsButKeepsIdentity() {
        val full =
            item("1", title = "t", time = 5, groupKey = "g", actions = listOf(ItemAction.Open()))
                .copy(
                    subtitle = "s",
                    body = "b",
                    icon = ItemImageHandle("i"),
                    ext = mapOf(KIND to ItemExtValue.Flag(true)),
                )
        val only = lens.copy(project = setOf(ItemField.TITLE))
        val out = (eval(only, listOf(full)) as LensResult.Flat).items.single()
        assertEquals(
            full.copy(
                subtitle = null,
                body = null,
                icon = null,
                timeEpochMillis = null,
                groupKey = null,
                groupLabel = null,
                actions = emptyList(),
                ext = emptyMap(),
            ),
            out,
        )
        assertEquals(full, (eval(lens, listOf(full)) as LensResult.Flat).items.single())
    }

    @Test
    fun sensitiveItemsLoseSensitiveFieldsEvenWhenProjected() {
        val secret =
            item(
                "1",
                title = "secret",
                privacy = ItemPrivacy.SENSITIVE,
                time = 5,
                groupKey = "g",
                actions = listOf(ItemAction.Reply()),
            )
                .copy(
                    subtitle = "s",
                    body = "b",
                    image = ItemImageHandle("img"),
                    icon = ItemImageHandle("ic"),
                    ext = mapOf(KIND to ItemExtValue.Text("x")),
                )
        val out = (eval(lens, listOf(secret)) as LensResult.Flat).items.single()
        assertNull(out.title)
        assertNull(out.subtitle)
        assertNull(out.body)
        assertNull(out.image)
        assertTrue(out.actions.isEmpty())
        assertTrue(out.ext.isEmpty())
        // Non-sensitive fields, identity and privacy survive.
        assertEquals(ItemImageHandle("ic"), out.icon)
        assertEquals(5L, out.timeEpochMillis)
        assertEquals("g", out.groupKey)
        assertEquals(secret.id, out.id)
        assertEquals(secret.target, out.target)
        assertEquals(ItemPrivacy.SENSITIVE, out.privacy)
    }

    @Test
    fun multiSourceMergeOrdersBySourceThenEmissionAndIgnoresOtherSources() {
        val items =
            listOf(
                item("b1", source = B),
                item("a1", source = A),
                item("x1", source = SourceId("x")),
                item("b2", source = B),
                item("a2", source = A),
            )
        assertEquals(listOf("a1", "a2", "b1", "b2"), eval(Lens(sources = listOf(A, B)), items).flatIds())
        assertEquals(listOf("b1", "b2", "a1", "a2"), eval(Lens(sources = listOf(B, A)), items).flatIds())
        assertEquals(listOf("a1", "a2", "b1", "b2"), eval(Lens(sources = listOf(A, B, A)), items).flatIds())
    }

    @Test
    fun duplicateIdsKeepTheEarlierSourceInTheLensRegardlessOfInputOrder() {
        val fromA = item("dup", source = A, title = "from a")
        val fromB = item("dup", source = B, title = "from b")
        val both = Lens(sources = listOf(A, B))
        val expected = listOf("from a")
        assertEquals(expected, (eval(both, listOf(fromA, fromB)) as LensResult.Flat).items.map { it.title })
        assertEquals(expected, (eval(both, listOf(fromB, fromA)) as LensResult.Flat).items.map { it.title })
        val reversed = Lens(sources = listOf(B, A))
        assertEquals(listOf("from b"), (eval(reversed, listOf(fromA, fromB)) as LensResult.Flat).items.map { it.title })
    }

    @Test
    fun duplicateIdWithinOneSourceKeepsFirstEmitted() {
        val out = eval(lens, listOf(item("d", title = "first"), item("d", title = "second")))
        assertEquals(listOf("first"), (out as LensResult.Flat).items.map { it.title })
    }

    @Test
    fun dedupeHappensBeforeFilter() {
        val items = listOf(item("d", source = A), item("d", source = B))
        val fromB = Lens(sources = listOf(A, B), filter = LensFilter.FromSource(B))
        assertEquals(emptyList(), eval(fromB, items).flatIds())
    }

    @Test
    fun inputIsBoundedByMaxInputItems() {
        val items = (1..50).map { item("i$it") }
        val ctx = LensEvaluationContext(nowEpochMillis = NOW, maxInputItems = 10)
        assertEquals((1..10).map { "i$it" }, eval(lens, items, ctx).flatIds())
    }

    @Test
    fun emptyInputGivesEmptyResults() {
        assertEquals(emptyList(), eval(lens, emptyList()).flatIds())
        assertEquals(LensResult.Grouped(emptyList()), eval(lens.copy(group = LensGroup.ByGroupKey), emptyList()))
    }

    @Test
    fun evaluationDoesNotMutateInput() {
        val items = mutableListOf(item("b", title = "b"), item("a", title = "a"))
        val snapshot = items.toList()
        eval(lens.copy(sort = LensSort(LensSortField.TITLE)), items)
        assertEquals(snapshot, items)
    }

    @Test
    fun asyncEvaluatorRunsOnExecutorAndHonoursCancel() {
        val queue = ArrayDeque<Runnable>()
        val executor = Executor { queue.addLast(it) }
        val async = AsyncLensEvaluator(executor)
        val results = mutableListOf<LensResult>()
        val ctx = LensEvaluationContext(NOW)
        async.evaluate(lens, listOf(item("a")), ctx, onResult = { results += it })
        assertTrue(results.isEmpty(), "nothing runs until the executor does")
        queue.removeFirst().run()
        assertEquals(1, results.size)

        val handle = async.evaluate(lens, listOf(item("a")), ctx, onResult = { results += it })
        handle.cancel()
        queue.removeFirst().run()
        assertEquals(1, results.size)
    }

    @Test
    fun asyncEvaluatorReportsErrors() {
        val boom = LensEvaluator { _, _, _ -> error("boom") }
        val errors = mutableListOf<Throwable>()
        AsyncLensEvaluator({ it.run() }, boom)
            .evaluate(lens, emptyList(), LensEvaluationContext(NOW), onError = { errors += it }, onResult = {})
        assertEquals(listOf("boom"), errors.map { it.message })
    }
}
