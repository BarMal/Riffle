package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellDockViewModelTest {
    @Test
    fun updatesDockVisibilityAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockEnabled(enabled = false))

        assertEquals(false, viewModel.state.value.homeLayout.dock.isEnabled)
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
