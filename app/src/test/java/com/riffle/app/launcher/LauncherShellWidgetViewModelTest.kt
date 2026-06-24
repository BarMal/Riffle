package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellWidgetViewModelTest {
    @Test
    fun addsHostedWidgetToHomeAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.AddHostedWidgetToHome(
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
            ),
        )

        assertEquals(
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            ),
            viewModel.state.value.homeLayout.selectedPage.items.single(),
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }
}
