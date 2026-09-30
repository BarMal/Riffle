package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetGridPlannerTest {
    private val binding = LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST)

    private fun place(
        id: String,
        column: Int,
        row: Int,
        cols: Int = 1,
        rows: Int = 1,
    ) = WidgetPlacement(WidgetContainer(ContainerId(id), WidgetSpan(cols, rows), binding), column, row)

    private fun grid(
        columns: Int,
        rows: Int,
        vararg placements: WidgetPlacement,
    ) = PageContent.WidgetGrid(columns, rows, placements.toList())

    @Test
    fun validGridIsKeptInReadingOrder() {
        val plan = WidgetGridPlanner.plan(grid(4, 4, place("b", 2, 0), place("c", 0, 1), place("a", 0, 0)))

        assertEquals(listOf("a", "b", "c"), plan.widgets.map { it.key })
        assertTrue(plan.dropped.isEmpty())
    }

    @Test
    fun firstPlacementWinsAndOffendersAreReported() {
        val plan =
            WidgetGridPlanner.plan(
                grid(
                    2,
                    2,
                    place("a", 0, 0, 2, 1),
                    place("overlap", 1, 0),
                    place("outside", 1, 1, 2, 1),
                    place("negative", -1, 0),
                    place("a", 0, 1),
                    place("ok", 1, 1),
                ),
            )

        assertEquals(listOf("a", "ok"), plan.widgets.map { it.key })
        assertEquals(
            listOf(
                DroppedWidget(ContainerId("overlap"), WidgetDropReason.COLLISION),
                DroppedWidget(ContainerId("outside"), WidgetDropReason.OUT_OF_BOUNDS),
                DroppedWidget(ContainerId("negative"), WidgetDropReason.OUT_OF_BOUNDS),
                DroppedWidget(ContainerId("a"), WidgetDropReason.DUPLICATE_ID),
            ),
            plan.dropped,
        )
    }

    @Test
    fun widgetCountIsBounded() {
        val placements = List(WidgetGridPlanner.MAX_WIDGETS + 3) { place("w$it", it % 10, it / 10) }
        val plan = WidgetGridPlanner.plan(PageContent.WidgetGrid(10, 10, placements))

        assertEquals(WidgetGridPlanner.MAX_WIDGETS, plan.widgets.size)
        assertEquals(3, plan.dropped.count { it.reason == WidgetDropReason.TOO_MANY })
    }

    @Test
    fun rectsDivideTheAreaAndSpansIncludeTheirGaps() {
        val plan = WidgetGridPlanner.plan(grid(4, 2, place("a", 1, 0, 2, 2)))
        val rect = WidgetGridPlanner.rectFor(plan, plan.widgets.single(), width = 440f, height = 210f, gap = 10f)

        assertEquals(CellRect(left = 112.5f, top = 0f, width = 215f, height = 210f), rect)
    }

    @Test
    fun tinyAreasNeverProduceNegativeSizes() {
        val plan = WidgetGridPlanner.plan(grid(4, 4, place("a", 3, 3)))
        val rect = WidgetGridPlanner.rectFor(plan, plan.widgets.single(), width = 5f, height = 5f, gap = 10f)

        assertEquals(0f, rect.width)
        assertEquals(0f, rect.height)
    }

    @Test
    fun randomGridsAlwaysPlanInsideTheGridWithoutOverlapAndDeterministically() {
        repeat(400) { seed ->
            val random = Random(seed)
            val columns = random.nextInt(1, 7)
            val rows = random.nextInt(1, 7)
            val placements =
                List(random.nextInt(0, 14)) {
                    place(
                        id = "w${random.nextInt(0, 12)}",
                        column = random.nextInt(-1, columns + 1),
                        row = random.nextInt(-1, rows + 1),
                        cols = random.nextInt(1, 4),
                        rows = random.nextInt(1, 4),
                    )
                }
            val plan = WidgetGridPlanner.plan(PageContent.WidgetGrid(columns, rows, placements))

            val occupied = HashSet<Pair<Int, Int>>()
            plan.widgets.forEach { planned ->
                assertTrue(planned.column >= 0 && planned.row >= 0, "seed $seed")
                assertTrue(planned.column + planned.widget.span.columns <= columns, "seed $seed")
                assertTrue(planned.row + planned.widget.span.rows <= rows, "seed $seed")
                for (c in planned.column until planned.column + planned.widget.span.columns) {
                    for (r in planned.row until planned.row + planned.widget.span.rows) {
                        assertTrue(occupied.add(c to r), "overlap at $c,$r seed $seed")
                    }
                }
            }
            assertEquals(placements.size, plan.widgets.size + plan.dropped.size, "seed $seed")
            assertEquals(plan.widgets.map { it.key }.toSet().size, plan.widgets.size, "seed $seed")
            assertEquals(plan, WidgetGridPlanner.plan(PageContent.WidgetGrid(columns, rows, placements)), "seed $seed")
        }
    }

    @Test
    fun rectsOfAPlanNeverOverlapForRandomAreas() {
        repeat(200) { seed ->
            val random = Random(seed)
            val placements =
                List(8) {
                    place(
                        "w$it",
                        random.nextInt(0, 4),
                        random.nextInt(0, 4),
                        random.nextInt(1, 3),
                        random.nextInt(1, 3),
                    )
                }
            val plan = WidgetGridPlanner.plan(PageContent.WidgetGrid(4, 4, placements))
            val width = random.nextInt(100, 900).toFloat()
            val height = random.nextInt(100, 900).toFloat()
            val rects = plan.widgets.map { WidgetGridPlanner.rectFor(plan, it, width, height, 8f) }
            for (i in rects.indices) {
                for (j in i + 1 until rects.size) {
                    val a = rects[i]
                    val b = rects[j]
                    val disjoint =
                        a.left + a.width <= b.left + EPS || b.left + b.width <= a.left + EPS ||
                            a.top + a.height <= b.top + EPS || b.top + b.height <= a.top + EPS
                    assertTrue(disjoint, "seed $seed")
                }
            }
        }
    }

    private companion object {
        const val EPS = 0.001f
    }
}
