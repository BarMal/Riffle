package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellHomeAppearanceViewModelTest {
    @Test
    fun updatesHomeLabelBackgroundAlphaAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelBackgroundAlpha(alphaPercent = 75))

        assertEquals(75, viewModel.state.value.homeLayout.settings.labels.backgroundAlphaPercent)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresInvalidHomeLabelBackgroundAlpha() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val originalLayout = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelBackgroundAlpha(alphaPercent = -1))

        assertEquals(originalLayout, viewModel.state.value.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun updatesHomeLabelTextSizeAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelTextSize(textSizeSp = 14))

        assertEquals(14, viewModel.state.value.homeLayout.settings.labels.textSizeSp)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresInvalidHomeLabelTextSize() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val originalLayout = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelTextSize(textSizeSp = 0))

        assertEquals(originalLayout, viewModel.state.value.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun updatesHomeLabelVisibilityAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelTextVisible(visible = false))

        assertEquals(false, viewModel.state.value.homeLayout.settings.labels.showText)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesHomeLabelMaxWidthAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelMaxWidth(maxWidthDp = 112))

        assertEquals(112, viewModel.state.value.homeLayout.settings.labels.maxWidthDp)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresInvalidHomeLabelMaxWidth() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val originalLayout = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelMaxWidth(maxWidthDp = 0))

        assertEquals(originalLayout, viewModel.state.value.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun updatesHomeLabelMaxLinesAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelMaxLines(maxLines = 2))

        assertEquals(2, viewModel.state.value.homeLayout.settings.labels.maxLines)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresInvalidHomeLabelMaxLines() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val originalLayout = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelMaxLines(maxLines = 0))

        assertEquals(originalLayout, viewModel.state.value.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun updatesHomeLabelSizingAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.SelectHomeLabelSizing(HomeLabelSizing.DYNAMIC))

        assertEquals(HomeLabelSizing.DYNAMIC, viewModel.state.value.homeLayout.settings.labels.sizing)
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
