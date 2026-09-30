package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageSetPlannerTest {
    private fun item(id: String) = Item(ItemId(id), SourceId("s"), ItemTarget.None, title = id)

    private fun group(
        key: String,
        count: Int = 1,
        label: String? = key,
    ) = ItemGroup(key, label, List(count) { item("$key-$it") })

    @Test
    fun oneNonEmptyPagePerGroupInOrderWithKeysFromGroupKeys() {
        val plan = PageSetPlanner.plan(LensResult.Grouped(listOf(group("b", 2), group("a"))))

        assertEquals(listOf("group:b", "group:a"), plan.pages.map { it.key })
        assertEquals(listOf("b", "a"), plan.pages.map { it.groupKey })
        assertEquals(2, plan.pages[0].items.size)
        assertEquals(0, plan.truncatedGroupCount)
        assertFalse(plan.shapeMismatch)
    }

    @Test
    fun emptyAndDuplicateGroupsAreSkipped() {
        val plan =
            PageSetPlanner.plan(
                LensResult.Grouped(listOf(group("a"), group("empty", 0), group("a", 3), group("b"))),
            )

        assertEquals(listOf("group:a", "group:b"), plan.pages.map { it.key })
        assertEquals(1, plan.pages[0].items.size)
    }

    @Test
    fun ungroupedTrailingGroupKeepsItsKeyAndNullLabel() {
        val plan = PageSetPlanner.plan(LensResult.Grouped(listOf(group("a"), group("", label = null))))

        assertEquals("group:", plan.pages.last().key)
        assertEquals(null, plan.pages.last().label)
    }

    @Test
    fun flatResultIsAShapeMismatchWithNoPages() {
        val plan = PageSetPlanner.plan(LensResult.Flat(listOf(item("x"))))

        assertTrue(plan.isEmpty)
        assertTrue(plan.shapeMismatch)
    }

    @Test
    fun pageCountIsBoundedAndTheRestCounted() {
        val groups = List(PageSetPlanner.MAX_PAGES + 5) { group("g$it") }

        val plan = PageSetPlanner.plan(LensResult.Grouped(groups))

        assertEquals(PageSetPlanner.MAX_PAGES, plan.pages.size)
        assertEquals(5, plan.truncatedGroupCount)
        assertEquals("group:g0", plan.pages.first().key)

        val custom = PageSetPlanner.plan(LensResult.Grouped(groups), maxPages = 3)
        assertEquals(3, custom.pages.size)
    }

    @Test
    fun selectionFollowsTheKeyThenTheIndex() {
        val before = PageSetPlanner.plan(LensResult.Grouped(listOf(group("a"), group("b"), group("c"))))
        val after = PageSetPlanner.plan(LensResult.Grouped(listOf(group("c"), group("b"))))

        // "b" moved: still selected by key.
        assertEquals(1, after.reconcileSelection("group:b", 1))
        // "a" vanished: the page now at the old index, clamped.
        assertEquals(1, after.reconcileSelection("group:a", 5))
        assertEquals(0, after.reconcileSelection(null, -3))
        assertEquals(0, PageSetPlanner.plan(LensResult.Grouped(emptyList())).reconcileSelection("group:a", 2))
        assertEquals(1, before.indexOfKey("group:b"))
    }

    @Test
    fun pageKeysAreUniqueStableAndIndependentOfPositionForRandomGroupings() {
        repeat(300) { seed ->
            val random = Random(seed)
            val keys = List(random.nextInt(0, 40)) { "k${random.nextInt(0, 25)}" }
            val groups = keys.map { group(it, random.nextInt(0, 3)) }
            val plan = PageSetPlanner.plan(LensResult.Grouped(groups))

            assertEquals(plan.pages.size, plan.pages.map { it.key }.toSet().size, "seed $seed")
            assertTrue(plan.pages.size <= PageSetPlanner.MAX_PAGES, "seed $seed")
            assertTrue(plan.pages.all { it.items.isNotEmpty() }, "seed $seed")

            // Reordering groups never changes which key a group's page has, only its position.
            val shuffled = PageSetPlanner.plan(LensResult.Grouped(groups.distinctBy { it.key }.shuffled(random)))
            val unique = PageSetPlanner.plan(LensResult.Grouped(groups.distinctBy { it.key }))
            if (unique.truncatedGroupCount == 0 && shuffled.truncatedGroupCount == 0) {
                assertEquals(unique.pages.map { it.key }.toSet(), shuffled.pages.map { it.key }.toSet(), "seed $seed")
            }
            // Planning is deterministic.
            assertEquals(plan, PageSetPlanner.plan(LensResult.Grouped(groups)), "seed $seed")
        }
    }

    @Test
    fun selectionNeverPointsOutsideThePlanForRandomChanges() {
        repeat(300) { seed ->
            val random = Random(seed)
            val groups = List(random.nextInt(0, 10)) { group("g${random.nextInt(8)}") }
            val plan = PageSetPlanner.plan(LensResult.Grouped(groups))
            val index = plan.reconcileSelection("group:g${random.nextInt(12)}", random.nextInt(-2, 15))
            assertTrue(index == 0 || index in plan.pages.indices, "seed $seed")
        }
    }
}
