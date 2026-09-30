package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.ExpressionCatalog
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
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Seeded, dependency-free property tests. A failing seed is printed in the assertion message. */
class LensEvaluatorPropertyTest {
    private val sources = listOf(SourceId("s0"), SourceId("s1"), SourceId("s2"))
    private val extKeys = listOf(ItemExtKey("demo.kind"), ItemExtKey("demo.rank"), PINNED_EXT_KEY)
    private val secretMarker = "SECRET"
    private val context = LensEvaluationContext(nowEpochMillis = 10_000_000L)

    private fun randomItem(
        random: Random,
        index: Int,
    ): Item {
        val sensitive = random.nextInt(3) == 0
        val tag = if (sensitive) secretMarker else "plain"
        val ext =
            buildMap {
                if (random.nextBoolean()) {
                    put(
                        extKeys[0],
                        ItemExtValue.Text(
                            if (sensitive) "$secretMarker-${random.nextInt(3)}" else "k${random.nextInt(3)}",
                        ),
                    )
                }
                if (random.nextBoolean()) put(extKeys[1], ItemExtValue.Number(random.nextLong(5)))
                if (random.nextBoolean()) put(PINNED_EXT_KEY, ItemExtValue.Flag(random.nextBoolean()))
            }
        return Item(
            id = ItemId("item-$index"),
            sourceId = sources[random.nextInt(sources.size)],
            target = ItemTarget.App("pkg.$index"),
            title = if (random.nextInt(6) == 0) null else "$tag-title-${random.nextInt(8)}",
            subtitle = "$tag-subtitle",
            body = "$tag-body",
            icon = ItemImageHandle("icon-$index"),
            image = ItemImageHandle("$tag-image-$index"),
            timeEpochMillis = if (random.nextInt(5) == 0) null else random.nextLong(0, 20_000_000L),
            groupKey = if (random.nextInt(5) == 0) null else "g${random.nextInt(4)}",
            groupLabel = "Group label",
            actions = if (random.nextBoolean()) listOf(ItemAction.Custom("act", "$tag-action")) else emptyList(),
            privacy = if (sensitive) ItemPrivacy.SENSITIVE else ItemPrivacy.VISIBLE,
            ext = ext,
        )
    }

    private fun randomItems(random: Random): List<Item> = List(random.nextInt(0, 40)) { randomItem(random, it) }

    private fun randomFilter(
        random: Random,
        depth: Int = 0,
    ): LensFilter =
        when (random.nextInt(if (depth > 2) 8 else 11)) {
            0 -> LensFilter.All
            1 -> LensFilter.FromSource(sources[random.nextInt(sources.size)])
            2 -> LensFilter.GroupKeyIs("g${random.nextInt(4)}")
            3 -> LensFilter.HasActions(random.nextBoolean())
            4 -> LensFilter.AgeAtMost(random.nextLong(0, 20_000_000L))
            5 -> LensFilter.AgeAtLeast(random.nextLong(0, 20_000_000L))
            6 -> LensFilter.PrivacyIs(ItemPrivacy.entries[random.nextInt(2)])
            7 -> LensFilter.ExtEquals(extKeys[0], ItemExtValue.Text("k${random.nextInt(3)}"))
            8 -> LensFilter.AllOf(List(random.nextInt(3)) { randomFilter(random, depth + 1) })
            9 -> LensFilter.AnyOf(List(random.nextInt(3)) { randomFilter(random, depth + 1) })
            else -> LensFilter.Not(randomFilter(random, depth + 1))
        }

    private fun randomGroup(random: Random): LensGroup =
        when (random.nextInt(4)) {
            0 -> LensGroup.None
            1 -> LensGroup.ByGroupKey
            2 -> LensGroup.ByDay
            else -> LensGroup.ByExt(extKeys[random.nextInt(2)])
        }

    private fun randomLens(
        random: Random,
        allowSourceOrder: Boolean = true,
    ): Lens {
        val fields =
            if (allowSourceOrder) LensSortField.entries else LensSortField.entries - LensSortField.SOURCE_ORDER
        return Lens(
            sources = sources.shuffled(random).take(random.nextInt(1, sources.size + 1)),
            filter = randomFilter(random),
            group = randomGroup(random),
            sort =
                LensSort(
                    fields[random.nextInt(fields.size)],
                    SortDirection.entries[random.nextInt(2)],
                    random.nextBoolean(),
                ),
            limit = if (random.nextBoolean()) null else random.nextInt(1, 12),
            project = ItemField.entries.filter { random.nextBoolean() }.toSet(),
        )
    }

    private fun LensResult.allItems(): List<Item> =
        when (this) {
            is LensResult.Flat -> items
            is LensResult.Grouped -> groups.flatMap { it.items }
        }

    private fun forEachCase(
        runs: Int = 300,
        block: (seed: Int, random: Random) -> Unit,
    ) = repeat(runs) { seed -> block(seed, Random(seed)) }

    @Test
    fun evaluationIsDeterministicAndOrderStableUnderInputPermutation() =
        forEachCase { seed, random ->
            val items = randomItems(random)
            val lens = randomLens(random, allowSourceOrder = false)
            val expected = DefaultLensEvaluator.evaluate(lens, items, context)
            assertEquals(expected, DefaultLensEvaluator.evaluate(lens, items, context), "seed $seed")
            repeat(3) {
                val shuffled = items.shuffled(random)
                assertEquals(expected, DefaultLensEvaluator.evaluate(lens, shuffled, context), "seed $seed")
            }
        }

    @Test
    fun sourceOrderIsStableWhenOnlyCrossSourceOrderIsPermuted() =
        forEachCase { seed, random ->
            val items = randomItems(random)
            val lens = randomLens(random).copy(sort = LensSort(LensSortField.SOURCE_ORDER))
            val expected = DefaultLensEvaluator.evaluate(lens, items, context)
            // Interleave sources differently while keeping each source's own emission order.
            val bySource = items.groupBy { it.sourceId }.mapValues { it.value.toMutableList() }
            val interleaved = mutableListOf<Item>()
            val pending = bySource.values.filter { it.isNotEmpty() }.toMutableList()
            while (pending.isNotEmpty()) {
                val pick = pending[random.nextInt(pending.size)]
                interleaved += pick.removeAt(0)
                pending.removeAll { it.isEmpty() }
            }
            assertEquals(expected, DefaultLensEvaluator.evaluate(lens, interleaved, context), "seed $seed")
        }

    @Test
    fun limitNeverExceedsN() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val result = DefaultLensEvaluator.evaluate(lens, randomItems(random), context)
            lens.limit?.let { assertTrue(result.allItems().size <= it, "seed $seed") }
        }

    @Test
    fun resultShapeMatchesTheLensAndNoGroupIsEmpty() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val result = DefaultLensEvaluator.evaluate(lens, randomItems(random), context)
            assertTrue(result.shape in lens.resultShapes, "seed $seed")
            if (result is LensResult.Grouped) assertTrue(result.groups.none { it.items.isEmpty() }, "seed $seed")
        }

    @Test
    fun resultItemsAreUniqueAndComeFromTheLensSources() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val items = DefaultLensEvaluator.evaluate(lens, randomItems(random), context).allItems()
            assertEquals(items.size, items.map { it.id }.toSet().size, "seed $seed")
            assertTrue(items.all { it.sourceId in lens.sources }, "seed $seed")
        }

    @Test
    fun noSensitiveContentEverAppearsInAnyResult() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val result = DefaultLensEvaluator.evaluate(lens, randomItems(random), context)
            assertFalse(result.toString().contains(secretMarker), "seed $seed lens $lens")
        }

    @Test
    fun redactionHoldsEvenWhenEveryFieldIsProjected() =
        forEachCase { seed, random ->
            val lens = randomLens(random).copy(project = ItemField.ALL)
            val result = DefaultLensEvaluator.evaluate(lens, randomItems(random), context)
            assertFalse(result.toString().contains(secretMarker), "seed $seed")
            result.allItems().filter { it.privacy == ItemPrivacy.SENSITIVE }.forEach {
                assertTrue(it.title == null && it.subtitle == null && it.body == null && it.image == null, "seed $seed")
                assertTrue(it.actions.isEmpty() && it.ext.isEmpty(), "seed $seed")
            }
        }

    @Test
    fun projectionNeverLeaksUnprojectedFields() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val originals = randomItems(random)
            val byId = originals.associateBy { it.id }
            DefaultLensEvaluator.evaluate(lens, originals, context).allItems().forEach { out ->
                val original = byId.getValue(out.id)
                assertEquals(original.sourceId, out.sourceId, "seed $seed")
                assertEquals(original.target, out.target, "seed $seed")
                val p = lens.project
                if (ItemField.TITLE !in p) assertEquals(null, out.title, "seed $seed")
                if (ItemField.SUBTITLE !in p) assertEquals(null, out.subtitle, "seed $seed")
                if (ItemField.BODY !in p) assertEquals(null, out.body, "seed $seed")
                if (ItemField.ICON !in p) assertEquals(null, out.icon, "seed $seed")
                if (ItemField.IMAGE !in p) assertEquals(null, out.image, "seed $seed")
                if (ItemField.TIME !in p) assertEquals(null, out.timeEpochMillis, "seed $seed")
                if (ItemField.GROUP !in p) assertEquals(null, out.groupKey ?: out.groupLabel, "seed $seed")
                if (ItemField.ACTIONS !in p) assertTrue(out.actions.isEmpty(), "seed $seed")
                if (ItemField.EXT !in p) assertTrue(out.ext.isEmpty(), "seed $seed")
                // Whatever survives is exactly the original value, never altered.
                out.title?.let { assertEquals(original.title, it, "seed $seed") }
                out.timeEpochMillis?.let { assertEquals(original.timeEpochMillis, it, "seed $seed") }
            }
        }

    @Test
    fun validPairingImpliesResultShapeIsAccepted() =
        forEachCase { seed, random ->
            val lens = randomLens(random)
            val result = DefaultLensEvaluator.evaluate(lens, randomItems(random), context)
            ExpressionCatalog.all.filter { LensExpressionValidity.check(lens, it).isValid }.forEach { expression ->
                // SINGLE is a lens-level shape (flat with limit 1); a Flat result satisfies it.
                val shapes =
                    if (result is LensResult.Flat) {
                        setOf(result.shape) + lens.resultShapes
                    } else {
                        setOf(
                            result.shape,
                        )
                    }
                assertTrue(shapes.any { it in expression.accepts }, "seed $seed ${expression.kind}")
                if (result is LensResult.Grouped) assertTrue(result.shape in expression.accepts, "seed $seed")
                if (lens.limit == 1 && result is LensResult.Flat) assertTrue(result.items.size <= 1, "seed $seed")
            }
        }
}
