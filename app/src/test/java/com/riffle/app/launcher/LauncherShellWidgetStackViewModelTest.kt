package com.riffle.app.launcher

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.restacked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widget stacks through the shell (ADR 0003, Amendment 14): the edits, the host ids they give up and the sheets. */
class LauncherShellWidgetStackViewModelTest {
    private val span = GridSpan(2, 2)
    private val deleted = mutableListOf<HostedWidgetId>()

    private fun widget(
        host: Int,
        column: Int,
        row: Int,
        span: GridSpan = this.span,
    ) = WidgetItem(
        id = LauncherItemId("widget:$host"),
        appWidgetId = HostedWidgetId(host),
        label = "Widget $host",
        placement = GridPlacement(GridCell(column, row), span),
    )

    private fun layoutOf(vararg items: WidgetItem): HomeLayout =
        HomeLayoutDefaults.standard().let { standard ->
            val page =
                LauncherPage(id = LauncherPageId("home"), grid = GridDimensions(4, 4), items = items.toList())
            standard.copy(pages = listOf(page), selectedPageId = page.id)
        }

    private fun viewModel(layout: HomeLayout) =
        LauncherShellViewModel(
            firstRunRepository = FakeFirstRunRepository(),
            homeLayoutRepository = FakeHomeLayoutRepository(layout),
            platformDependencies = LauncherShellPlatformDependencies(deleteHostedWidgetId = deleted::add),
        )

    private fun stackOf(vararg members: StackedWidget): WidgetItem =
        widget(members.first().appWidgetId.value, 0, 0).restacked(members.toList(), shown = 0)

    private fun LauncherShellViewModel.items() = state.value.homeLayout.selectedPage.items.filterIsInstance<WidgetItem>()

    @Test
    fun aWidgetDroppedOnAnotherOfTheSameSpanMakesAStackAndDeletesNothing() {
        val vm = viewModel(layoutOf(widget(1, 0, 0), widget(2, 2, 0)))

        vm.onHomeShortcutEdited(LauncherShellAction.MoveHomeShortcutToCell(LauncherItemId("widget:2"), GridCell(0, 0)))

        val stack = vm.items().single()
        assertEquals(listOf(1, 2), stack.members.map { it.appWidgetId.value })
        assertEquals(emptyList<HostedWidgetId>(), deleted)
    }

    @Test
    fun aWidgetDroppedOnAWidgetOfAnotherSpanIsNotStacked() {
        val vm = viewModel(layoutOf(widget(1, 0, 0), widget(2, 2, 0, GridSpan(2, 1))))

        vm.onHomeShortcutEdited(LauncherShellAction.MoveHomeShortcutToCell(LauncherItemId("widget:2"), GridCell(0, 0)))

        assertEquals(2, vm.items().size)
        assertTrue(vm.items().none { it.isStack })
    }

    @Test
    fun removingAStackDeletesEveryPlatformWidgetInItButNotTheGlanceOne() {
        val stack =
            stackOf(
                StackedWidget(HostedWidgetId(1), "One"),
                StackedWidget(HostedWidgetId(0), "Now playing", glance = GlanceWidgetKind.NOW_PLAYING),
                StackedWidget(HostedWidgetId(3), "Three"),
            )
        val vm = viewModel(layoutOf(stack))

        vm.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(stack.id))

        assertEquals(listOf(HostedWidgetId(1), HostedWidgetId(3)), deleted)
        assertTrue(vm.items().isEmpty())
    }

    @Test
    fun removingOneWidgetFromAStackDeletesOnlyItsHostIdAndTwoLeftStayAStack() {
        val stack =
            stackOf(StackedWidget(HostedWidgetId(1), "One"), StackedWidget(HostedWidgetId(2), "Two"), StackedWidget(HostedWidgetId(3), "Three"))
        val vm = viewModel(layoutOf(stack))

        vm.onHomeShortcutEdited(LauncherShellAction.RemoveStackedWidget(stack.id, 1))

        assertEquals(listOf(HostedWidgetId(2)), deleted)
        assertEquals(listOf(1, 3), vm.items().single().members.map { it.appWidgetId.value })
    }

    @Test
    fun aStackReducedToOneWidgetIsAPlainWidgetAgain() {
        val stack = stackOf(StackedWidget(HostedWidgetId(1), "One"), StackedWidget(HostedWidgetId(2), "Two"))
        val vm = viewModel(layoutOf(stack))

        vm.onHomeShortcutEdited(LauncherShellAction.RemoveStackedWidget(stack.id, 0))

        val left = vm.items().single()
        assertFalse(left.isStack)
        assertEquals(HostedWidgetId(2), left.appWidgetId)
        assertEquals(listOf(HostedWidgetId(1)), deleted)
    }

    @Test
    fun theShownWidgetIsRememberedAndAnIndexOutOfRangeIsKept() {
        val stack = stackOf(StackedWidget(HostedWidgetId(1), "One"), StackedWidget(HostedWidgetId(2), "Two"))
        val vm = viewModel(layoutOf(stack))

        vm.onHomeShortcutEdited(LauncherShellAction.ShowStackedWidget(stack.id, 1))
        assertEquals(1, vm.items().single().shownIndex)
        vm.onHomeShortcutEdited(LauncherShellAction.ShowStackedWidget(stack.id, 9))
        assertEquals(1, vm.items().single().shownIndex)
    }

    @Test
    fun movingAWidgetOutKeepsItsHostIdAndSaysNothingWhenThereIsRoom() {
        val stack = stackOf(StackedWidget(HostedWidgetId(1), "One"), StackedWidget(HostedWidgetId(2), "Two"))
        val vm = viewModel(layoutOf(stack))

        vm.onHomeShortcutEdited(LauncherShellAction.MoveStackedWidgetOut(stack.id, 1))

        assertEquals(2, vm.items().size)
        assertEquals(emptyList<HostedWidgetId>(), deleted)
        assertTrue(vm.items().none { it.isStack })
    }

    @Test
    fun aGlanceWidgetJoinsAStackAndAFullStackRefusesAnother() {
        val vm = viewModel(layoutOf(widget(1, 0, 0)))

        vm.onHomeShortcutEdited(
            LauncherShellAction.AddGlanceWidgetToStack(LauncherItemId("widget:1"), GlanceWidgetKind.TOP_STORY, "Top story"),
        )

        assertEquals(GlanceWidgetKind.TOP_STORY, vm.items().single().shownMember.glance)
        repeat(9) { n ->
            vm.onHomeShortcutEdited(
                LauncherShellAction.AddHostedWidgetToStack(LauncherItemId("widget:1"), HostedWidgetId(10 + n), "W$n"),
            )
        }
        assertEquals(10, vm.items().single().members.size)
        vm.onHomeShortcutEdited(
            LauncherShellAction.AddHostedWidgetToStack(LauncherItemId("widget:1"), HostedWidgetId(99), "Extra"),
        )
        assertEquals(10, vm.items().single().members.size)
    }

    @Test
    fun thePickerOpenedForAStackNamesItAndClosingForgetsIt() {
        val vm = viewModel(layoutOf(widget(1, 0, 0)))
        val id = LauncherItemId("widget:1")

        vm.onAppActionSelected(LauncherShellAction.OpenWidgetPickerForStack(id))
        assertEquals(id, vm.state.value.widgetPickerStackItemId)
        assertTrue(vm.state.value.isWidgetPickerOpen)

        vm.onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
        assertNull(vm.state.value.widgetPickerStackItemId)
        assertFalse(vm.state.value.isWidgetPickerOpen)
    }

    @Test
    fun theEditorAndTheRemovalQuestionOpenAndCloseOnTheirOwn() {
        val vm = viewModel(layoutOf(widget(1, 0, 0)))
        val id = LauncherItemId("widget:1")

        vm.onAppActionSelected(LauncherShellAction.OpenWidgetStackEditor(id))
        vm.onAppActionSelected(LauncherShellAction.ConfirmRemoveWidgetStack(id))
        assertEquals(id, vm.state.value.widgetStackEditorItemId)
        assertEquals(id, vm.state.value.widgetStackRemovalItemId)

        vm.onAppActionSelected(LauncherShellAction.CloseWidgetStackEditor)
        vm.onAppActionSelected(LauncherShellAction.DismissRemoveWidgetStack)
        assertNull(vm.state.value.widgetStackEditorItemId)
        assertNull(vm.state.value.widgetStackRemovalItemId)
        assertEquals(emptyList<HostedWidgetId>(), deleted)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        private var savedLayout: HomeLayout,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }
}
