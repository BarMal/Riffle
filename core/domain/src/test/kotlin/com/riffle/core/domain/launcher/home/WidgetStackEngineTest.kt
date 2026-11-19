package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetStackEngineTest {
    private val engine = WidgetStackEngine()
    private val grid = GridDimensions(columns = 4, rows = 4)
    private val two = GridSpan(2, 2)

    private fun widget(
        host: Int,
        column: Int,
        row: Int,
        span: GridSpan = two,
        constraints: WidgetResizeConstraints = WidgetResizeConstraints(),
        label: String = "Widget $host",
    ) = WidgetItem(
        id = LauncherItemId("widget:$host"),
        appWidgetId = HostedWidgetId(host),
        label = label,
        resizeConstraints = constraints,
        placement = GridPlacement(GridCell(column, row), span),
    )

    private fun layoutOf(vararg items: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().let { standard ->
            val page = LauncherPage(id = LauncherPageId("home"), grid = grid, items = items.toList())
            standard.copy(pages = listOf(page), selectedPageId = page.id)
        }

    private fun HomeLayout.widget(id: String) = pages.flatMap { it.items }.first { it.id.value == id } as WidgetItem

    private fun Any.updated() = assertIs<WidgetStackResult.Updated>(this).layout

    private fun stackOf(count: Int): HomeLayout {
        var layout = layoutOf(widget(1, 0, 0))
        for (host in 2..count) {
            layout =
                engine.addMember(layout, LauncherItemId("widget:1"), widget(host, 0, 0).members.single()).updated()
        }
        return layout
    }

    @Test
    fun aWidgetDroppedOnAnotherOfTheSameSpanMakesAStackThatShowsTheNewcomer() {
        val layout = layoutOf(widget(1, 0, 0), widget(2, 2, 0))

        val stacked = engine.stackOnto(layout, LauncherItemId("widget:2"), LauncherItemId("widget:1")).updated()

        val stack = stacked.widget("widget:1")
        assertTrue(stack.isStack)
        assertEquals(listOf(1, 2), stack.members.map { it.appWidgetId.value })
        assertEquals(1, stack.shownIndex)
        assertEquals(listOf("widget:1"), stacked.selectedPage.items.map { it.id.value })
        assertEquals(GridPlacement(GridCell(0, 0), two), stack.placement)
    }

    @Test
    fun aWidgetOfAnotherSpanNeverStacks() {
        val layout = layoutOf(widget(1, 0, 0), widget(2, 2, 0, span = GridSpan(2, 1)))

        assertFalse(engine.canStack(layout.widget("widget:1"), layout.widget("widget:2")))
        assertEquals(
            WidgetStackRejection.DIFFERENT_SPAN,
            assertIs<WidgetStackResult.Rejected>(
                engine.stackOnto(layout, LauncherItemId("widget:2"), LauncherItemId("widget:1")),
            ).reason,
        )
    }

    @Test
    fun aWidgetDroppedOnAnExistingStackExtendsIt() {
        val layout =
            layoutOf(widget(3, 2, 2)).let { base ->
                val page = base.selectedPage.copy(items = stackOf(2).selectedPage.items + base.selectedPage.items)
                base.copy(pages = listOf(page))
            }

        val result = engine.stackOnto(layout, LauncherItemId("widget:3"), LauncherItemId("widget:1")).updated()

        assertEquals(listOf(1, 2, 3), result.widget("widget:1").members.map { it.appWidgetId.value })
        assertEquals(1, result.selectedPage.items.size)
    }

    @Test
    fun aStackCannotBeDroppedOnAnotherWidget() {
        val stack = stackOf(2).selectedPage.items.single() as WidgetItem
        val other = widget(9, 2, 0)

        assertFalse(engine.canStack(other, stack.copy(id = LauncherItemId("stack-2"))))
    }

    @Test
    fun theSameWidgetCannotJoinTwiceAndNothingStacksOntoItself() {
        val a = widget(1, 0, 0)
        val twin = widget(1, 2, 0).copy(id = LauncherItemId("twin"))

        assertFalse(engine.canStack(a, twin))
        assertFalse(engine.canStack(a, a))
    }

    @Test
    fun aStackHoldsAtMostTenWidgets() {
        val full = stackOf(WidgetStackEngine.MAX_MEMBERS)
        assertEquals(10, full.widget("widget:1").members.size)

        val extra = widget(99, 0, 0).members.single()
        assertEquals(
            WidgetStackRejection.STACK_FULL,
            assertIs<WidgetStackResult.Rejected>(engine.addMember(full, LauncherItemId("widget:1"), extra)).reason,
        )
        val dropped =
            layoutOf(widget(50, 2, 2)).let { base ->
                base.copy(
                    pages = listOf(base.selectedPage.copy(items = full.selectedPage.items + base.selectedPage.items)),
                )
            }
        assertFalse(engine.canStack(dropped.widget("widget:1"), dropped.widget("widget:50")))
    }

    @Test
    fun addingToALoneWidgetMakesAStackAndOnlyAMemberThatPermitsTheSpanJoins() {
        val layout = layoutOf(widget(1, 0, 0))
        val tooBig = StackedWidget(HostedWidgetId(2), "Big", WidgetResizeConstraints(minSpan = GridSpan(3, 3)))
        val fine = StackedWidget(HostedWidgetId(3), "Fine")

        assertEquals(
            WidgetStackRejection.SPAN_NOT_SUPPORTED,
            assertIs<WidgetStackResult.Rejected>(engine.addMember(layout, LauncherItemId("widget:1"), tooBig)).reason,
        )
        val stacked = engine.addMember(layout, LauncherItemId("widget:1"), fine).updated().widget("widget:1")
        assertEquals(listOf(1, 3), stacked.members.map { it.appWidgetId.value })
        assertEquals(1, stacked.shownIndex)
    }

    @Test
    fun aGlanceWidgetStacksLikeAnAppWidget() {
        val layout = layoutOf(widget(1, 0, 0))
        val glance =
            StackedWidget(
                GlanceWidgets.UNBOUND_HOST_ID,
                "Now playing",
                GlanceWidgetKind.NOW_PLAYING.resizeConstraints,
                GlanceWidgetKind.NOW_PLAYING,
            )

        val stacked = engine.addMember(layout, LauncherItemId("widget:1"), glance).updated().widget("widget:1")

        assertEquals(listOf(1), stacked.hostIds.map { it.value })
        assertEquals(GlanceWidgetKind.NOW_PLAYING, stacked.shownMember.glance)
        // The stack's own widget is still the first one, and it is the kind-less app widget.
        assertNull(stacked.glance)
    }

    @Test
    fun theStacksConstraintsAreWhatEveryMemberPermits() {
        val a = widget(1, 0, 0, constraints = WidgetResizeConstraints(GridSpan(1, 1), GridSpan(4, 4)))
        val layout = layoutOf(a)
        val b =
            StackedWidget(
                HostedWidgetId(2),
                "B",
                WidgetResizeConstraints(
                    minSpan = GridSpan(2, 1),
                    maxSpan = GridSpan(3, 4),
                    supportsVerticalResize = false,
                ),
            )

        val stack = engine.addMember(layout, LauncherItemId("widget:1"), b).updated().widget("widget:1")

        assertEquals(GridSpan(2, 1), stack.resizeConstraints.minSpan)
        assertEquals(GridSpan(3, 4), stack.resizeConstraints.maxSpan)
        assertFalse(stack.resizeConstraints.supportsVerticalResize)
        assertTrue(stack.resizeConstraints.supportsHorizontalResize)
        assertTrue(stack.resizeConstraints.permits(GridSpan(3, 2)))
        assertFalse(stack.resizeConstraints.permits(GridSpan(4, 2)))
    }

    @Test
    fun resizingAStackAppliesToTheWholeStackAndOnlyToSizesEveryMemberSupports() {
        val a = widget(1, 0, 0, constraints = WidgetResizeConstraints(GridSpan(1, 1), GridSpan(4, 4)))
        val b = StackedWidget(HostedWidgetId(2), "B", WidgetResizeConstraints(GridSpan(1, 1), GridSpan(3, 3)))
        val layout = engine.addMember(layoutOf(a), LauncherItemId("widget:1"), b).updated()
        val widgets = WidgetEngine()

        val ok = widgets.resizeWidgetOnSelectedPage(layout, LauncherItemId("widget:1"), GridSpan(3, 3))
        val tooBig = widgets.resizeWidgetOnSelectedPage(layout, LauncherItemId("widget:1"), GridSpan(4, 4))

        val resized = assertIs<WidgetEditResult.Updated>(ok).layout.widget("widget:1")
        assertEquals(GridSpan(3, 3), resized.placement?.span)
        assertEquals(2, resized.members.size)
        assertIs<WidgetEditResult.Rejected>(tooBig)
    }

    @Test
    fun reorderingKeepsTheShownWidgetShownAndTheFirstBecomesTheItemsOwn() {
        val layout = stackOf(3)
        val shown = engine.show(layout, LauncherItemId("widget:1"), 1).updated()

        val moved = engine.reorder(shown, LauncherItemId("widget:1"), from = 1, to = 0).updated().widget("widget:1")

        assertEquals(listOf(2, 1, 3), moved.members.map { it.appWidgetId.value })
        assertEquals(0, moved.shownIndex)
        assertEquals(HostedWidgetId(2), moved.appWidgetId)
        assertEquals("Widget 2", moved.label)
        assertEquals(LauncherItemId("widget:1"), moved.id)
    }

    @Test
    fun showClampsTheIndexAndAPlainWidgetHasNothingToShow() {
        assertEquals(2, engine.show(stackOf(3), LauncherItemId("widget:1"), 99).updated().widget("widget:1").shownIndex)
        assertEquals(0, engine.show(stackOf(3), LauncherItemId("widget:1"), -4).updated().widget("widget:1").shownIndex)
        assertEquals(
            WidgetStackRejection.NOT_A_STACK,
            assertIs<WidgetStackResult.Rejected>(
                engine.show(layoutOf(widget(1, 0, 0)), LauncherItemId("widget:1"), 0),
            ).reason,
        )
    }

    @Test
    fun reorderRejectsAnIndexOutOfRange() {
        assertEquals(
            WidgetStackRejection.INDEX_OUT_OF_RANGE,
            assertIs<WidgetStackResult.Rejected>(engine.reorder(stackOf(2), LauncherItemId("widget:1"), 0, 2)).reason,
        )
    }

    @Test
    fun removingAMemberReturnsItAndAStackOfOneBecomesAPlainWidget() {
        val result = assertIs<WidgetStackResult.Updated>(engine.remove(stackOf(2), LauncherItemId("widget:1"), 0))

        assertEquals(HostedWidgetId(1), result.member?.appWidgetId)
        val left = result.layout.widget("widget:1")
        assertFalse(left.isStack)
        assertEquals(emptyList(), left.stack)
        assertEquals(0, left.shownIndex)
        assertEquals(HostedWidgetId(2), left.appWidgetId)
        assertEquals(listOf(2), left.hostIds.map { it.value })
    }

    @Test
    fun removingEarlierThanTheShownMemberKeepsTheSameWidgetShown() {
        val layout = engine.show(stackOf(4), LauncherItemId("widget:1"), 2).updated()

        val after = assertIs<WidgetStackResult.Updated>(engine.remove(layout, LauncherItemId("widget:1"), 0)).layout

        val stack = after.widget("widget:1")
        assertEquals(listOf(2, 3, 4), stack.members.map { it.appWidgetId.value })
        assertEquals(3, stack.shownMember.appWidgetId.value)
    }

    @Test
    fun removingTheLastWidgetOfAnItemIsRemovingTheItemNotAStackEdit() {
        assertEquals(
            WidgetStackRejection.NOT_A_STACK,
            assertIs<WidgetStackResult.Rejected>(
                engine.remove(layoutOf(widget(1, 0, 0)), LauncherItemId("widget:1"), 0),
            ).reason,
        )
    }

    @Test
    fun movingOutPlacesTheWidgetInTheNearestFreeCellsWithTheStacksSpan() {
        val layout = stackOf(3)

        val result = assertIs<WidgetStackResult.Updated>(engine.moveOut(layout, LauncherItemId("widget:1"), 2))

        val moved = result.layout.widget(result.newItemId!!.value)
        assertEquals(HostedWidgetId(3), moved.appWidgetId)
        assertFalse(moved.isStack)
        assertEquals(two, moved.placement?.span)
        // Nearest to the stack at (0, 0) that clears its 2x2 area: the column to its right.
        assertEquals(GridCell(2, 0), moved.placement?.cell)
        assertEquals(2, result.layout.widget("widget:1").members.size)
    }

    @Test
    fun movingOutWithNoRoomRejectsAndChangesNothing() {
        var layout = layoutOf(widget(1, 0, 0), widget(20, 2, 0), widget(21, 0, 2), widget(22, 2, 2))
        layout = engine.addMember(layout, LauncherItemId("widget:1"), StackedWidget(HostedWidgetId(2), "B")).updated()

        val result = engine.moveOut(layout, LauncherItemId("widget:1"), 1)

        assertEquals(WidgetStackRejection.NO_ROOM, assertIs<WidgetStackResult.Rejected>(result).reason)
        assertEquals(2, layout.widget("widget:1").members.size)
    }

    @Test
    fun aMovedOutWidgetKeepsItsOwnIdEvenWhenTheStackAlreadyUsesThatId() {
        // After a reorder the stack's id (widget:1) is no longer its first member's, so moving widget 1 out must not
        // reuse the id.
        val layout = engine.reorder(stackOf(2), LauncherItemId("widget:1"), 0, 1).updated()

        val result = assertIs<WidgetStackResult.Updated>(engine.moveOut(layout, LauncherItemId("widget:1"), 1))

        assertEquals("widget:1:2", result.newItemId?.value)
        assertEquals(2, result.layout.selectedPage.items.map { it.id.value }.toSet().size)
    }

    @Test
    fun aGeneratedPageTakesNoStack() {
        val layout = layoutOf(widget(1, 0, 0), widget(2, 2, 0))
        val generated =
            layout.copy(
                pages =
                    listOf(
                        layout.selectedPage.copy(type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY)),
                    ),
            )

        assertEquals(
            WidgetStackRejection.GENERATED_PAGE,
            assertIs<WidgetStackResult.Rejected>(
                engine.stackOnto(generated, LauncherItemId("widget:2"), LauncherItemId("widget:1")),
            ).reason,
        )
    }

    @Test
    fun theLayoutHostsEveryMembersHostId() {
        val layout = stackOf(3)

        assertTrue(layout.hostsWidget(HostedWidgetId(3)))
        assertFalse(layout.hostsWidget(HostedWidgetId(4)))
    }
}
