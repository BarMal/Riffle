package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HostedWidgetAddCompletionTest {
    @Test
    fun completesHostedWidgetAddAndClosesWidgetPicker() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())
        runBlocking {
            viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)?.join()
        }

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(7),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 2),
                ),
            )

        val widget = viewModel.state.value.homeLayout.selectedPage.items.single() as WidgetItem
        assertEquals(HostedWidgetId(7), widget.appWidgetId)
        assertEquals(GridSpan(columns = 2, rows = 2), widget.placement?.span)
        assertFalse(viewModel.state.value.isWidgetPickerOpen)
        assertEquals(HostedWidgetAddCompletionResult.Placed(message = null), result)
    }

    @Test
    fun returnsAdjustmentMessageWhenWidgetIsShrunkFromPreferredSpan() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(9),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 6, rows = 1),
                ),
            )

        assertEquals(
            HostedWidgetAddCompletionResult.Placed("Calendar ideal size is 6x1; added as 4x1"),
            result,
        )
    }

    @Test
    fun fitsOversizedPreferredSpanToSelectedGridBeforeCompletingAdd() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(10),
                    label = "Agenda",
                    preferredSpan = GridSpan(columns = 9, rows = 8),
                ),
            )

        val widget = viewModel.state.value.homeLayout.selectedPage.items.single() as WidgetItem
        assertEquals(GridSpan(columns = 4, rows = 5), widget.placement?.span)
        assertEquals(
            HostedWidgetAddCompletionResult.Placed("Agenda ideal size is 9x8; added as 4x5"),
            result,
        )
    }

    @Test
    fun reportsRejectionWhenSelectedPageHasNoAvailableCell() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = FakeHomeLayoutRepository(fullOneCellLayout()),
            )
        runBlocking {
            viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)?.join()
        }

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(12),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 1, rows = 1),
                ),
            )

        assertEquals(HostedWidgetAddCompletionResult.Rejected, result)
        assertEquals(
            HostedWidgetId(11),
            (viewModel.state.value.homeLayout.selectedPage.items.single() as WidgetItem).appWidgetId,
        )
        assertFalse(viewModel.state.value.isWidgetPickerOpen)
    }

    @Test
    fun reportsRejectionWhenHostedWidgetAlreadyExistsOnAnotherPage() {
        val existingWidget =
            WidgetItem(
                id = LauncherItemId("widget:12"),
                appWidgetId = HostedWidgetId(12),
                label = "Existing",
                placement = GridPlacement(GridCell(column = 0, row = 0)),
            )
        val existingPage = HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(existingWidget))
        val targetPage =
            HomeLayoutDefaults.standard().selectedPage.copy(
                id = com.riffle.core.domain.launcher.home.LauncherPageId("widgets"),
            )
        val initialLayout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(existingPage, targetPage),
                selectedPageId = targetPage.id,
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = FakeHomeLayoutRepository(initialLayout),
            )

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(12),
                    label = "Weather",
                ),
            )

        assertEquals(HostedWidgetAddCompletionResult.Rejected, result)
        assertEquals(initialLayout, viewModel.state.value.homeLayout)
    }

    @Test
    fun completesHostedWidgetAddToDockAndClosesWidgetPicker() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())
        runBlocking {
            viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)?.join()
        }

        val result =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(14),
                    label = "Weather",
                ),
            )

        val widget = viewModel.state.value.homeLayout.dock.items.single() as WidgetItem
        assertEquals(HostedWidgetId(14), widget.appWidgetId)
        assertFalse(viewModel.state.value.isWidgetPickerOpen)
        assertEquals(HostedWidgetAddCompletionResult.Placed(message = null), result)
    }

    @Test
    fun completingHostedWidgetAddToDockSavesLayout() {
        val repository = RecordingHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.completeWidgetAdd(
            LauncherShellAction.AddHostedWidgetToDock(
                hostedWidgetId = HostedWidgetId(16),
                label = "Weather",
            ),
        )

        val widget = repository.savedLayoutSet?.activeLayout?.dock?.items?.single() as WidgetItem
        assertEquals(HostedWidgetId(16), widget.appWidgetId)
    }

    @Test
    fun rejectedCompletionDeletesHostedWidgetId() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val action =
            LauncherShellAction.AddHostedWidgetToHome(
                hostedWidgetId = HostedWidgetId(13),
                label = "Weather",
            )

        val result =
            HostedWidgetAddCompletionResult.Rejected.deleteHostedWidgetIdWhenRejected(
                action,
                deletedHostedWidgetIds::add,
            )

        assertEquals(HostedWidgetAddCompletionResult.Rejected, result)
        assertEquals(listOf(HostedWidgetId(13)), deletedHostedWidgetIds)
    }

    @Test
    fun rejectedDockCompletionDeletesHostedWidgetId() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val action =
            LauncherShellAction.AddHostedWidgetToDock(
                hostedWidgetId = HostedWidgetId(15),
                label = "Weather",
            )

        val result =
            HostedWidgetAddCompletionResult.Rejected.deleteHostedWidgetIdWhenRejected(
                action,
                deletedHostedWidgetIds::add,
            )

        assertEquals(HostedWidgetAddCompletionResult.Rejected, result)
        assertEquals(listOf(HostedWidgetId(15)), deletedHostedWidgetIds)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        private val layout: HomeLayout,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = layout

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private class RecordingHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private companion object {
        fun fullOneCellLayout(): HomeLayout {
            val page =
                HomeLayoutDefaults.standard().selectedPage.copy(
                    grid = GridDimensions(columns = 1, rows = 1),
                    items =
                        listOf(
                            WidgetItem(
                                id = LauncherItemId("widget:11"),
                                appWidgetId = HostedWidgetId(11),
                                label = "Existing",
                                placement = GridPlacement(GridCell(column = 0, row = 0)),
                            ),
                        ),
                )
            return HomeLayoutDefaults.standard().copy(
                pages = listOf(page),
                selectedPageId = page.id,
            )
        }
    }
}
