package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellPageMoveViewModelTest {
    @Test
    fun movesHomePageToTargetIndexAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(
            LauncherShellAction.MoveHomePage(
                pageId = LauncherPageId("home-3"),
                targetIndex = 0,
            ),
        )

        assertEquals(
            listOf(LauncherPageId("home-3"), LauncherPageId("home"), LauncherPageId("home-2")),
            viewModel.state.value.homeLayout.pageIds,
        )
        assertEquals(LauncherPageId("home-3"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = true

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

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }
}
