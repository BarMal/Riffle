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
import kotlin.test.assertNull

class PageSetSelectionTest {
    private fun plan(vararg keys: String) = planOf(keys.toList())

    private fun planOf(keys: List<String>) =
        PageSetPlanner.plan(
            LensResult.Grouped(
                keys.map { key ->
                    ItemGroup(key, key, listOf(Item(ItemId("$key-1"), SourceId("s"), ItemTarget.None, title = key)))
                },
            ),
        )

    @Test
    fun selectionFollowsItsPageWhenGroupsReorderOrAppear() {
        val selection = PageSetSelection()
        selection.settled(plan("a", "b", "c"), 1)
        assertEquals("group:b", selection.selectedKey)

        assertEquals(2, selection.reconcile(plan("c", "x", "b"), currentIndex = 1))
        assertEquals("group:b", selection.selectedKey)
    }

    @Test
    fun aRemovedPageFallsBackToTheClampedIndexAndTheSelectionFollows() {
        val selection = PageSetSelection()
        selection.settled(plan("a", "b", "c"), 2)

        assertEquals(1, selection.reconcile(plan("a", "b"), currentIndex = 2))
        assertEquals("group:b", selection.selectedKey)
    }

    @Test
    fun anEmptyPlanKeepsTheRememberedKeyAndShowsIndexZero() {
        val selection = PageSetSelection()
        selection.settled(plan("a", "b"), 1)

        assertEquals(0, selection.reconcile(plan(), currentIndex = 1))
        assertEquals(1, selection.reconcile(plan("a", "b"), currentIndex = 0))
    }

    @Test
    fun outOfRangeSettleIsIgnored() {
        val selection = PageSetSelection()
        selection.settled(plan("a"), 5)
        assertNull(selection.selectedKey)
    }

    @Test
    fun seededRandomReshufflesKeepTheSelectedGroupWhileItExists() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val selection = PageSetSelection()
            val universe = List(UNIVERSE) { "g$it" }
            val current = universe.shuffled(random).take(random.nextInt(2, UNIVERSE))
            var index = random.nextInt(current.size)
            selection.settled(planOf(current), index)
            repeat(STEPS) {
                val next = universe.shuffled(random).take(random.nextInt(1, UNIVERSE + 1))
                val selected = selection.selectedKey
                index = selection.reconcile(planOf(next), index)
                if (selected != null && selected.removePrefix("group:") in next) {
                    assertEquals(selected, "group:" + next[index], "seed $seed")
                }
            }
        }
    }

    private companion object {
        const val SEEDS = 40
        const val UNIVERSE = 8
        const val STEPS = 20
    }
}
