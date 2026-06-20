package com.riffle.core.domain.launcher.home

data class HomeLayout(
    val viewMode: LauncherViewMode,
    val pages: List<LauncherPage>,
    val selectedPageId: LauncherPageId,
    val dock: DockModel,
    val settings: HomeLayoutSettings = HomeLayoutSettings.standardPhone(),
    val editMode: HomeEditMode = HomeEditMode.Browsing,
) {
    val selectedPage: LauncherPage =
        pages.first { page -> page.id == selectedPageId }

    val selectedPageIndex: Int =
        pages.indexOfFirst { page -> page.id == selectedPageId }
}

object HomeLayoutDefaults {
    fun standard(): HomeLayout =
        HomeLayoutSettings.standardPhone().let { settings ->
            LauncherPage(
                id = LauncherPageId("home"),
                grid = settings.grid.dimensions,
            ).let { firstPage ->
                HomeLayout(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    pages = listOf(firstPage),
                    selectedPageId = firstPage.id,
                    dock = DockModel(capacity = 5),
                    settings = settings,
                )
            }
        }
}
