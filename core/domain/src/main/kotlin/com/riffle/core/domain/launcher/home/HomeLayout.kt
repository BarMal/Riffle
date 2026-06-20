package com.riffle.core.domain.launcher.home

data class HomeLayout(
    val viewMode: LauncherViewMode,
    val pages: List<LauncherPage>,
    val selectedPageId: LauncherPageId,
    val dock: DockModel,
) {
    val selectedPage: LauncherPage =
        pages.first { page -> page.id == selectedPageId }

    val selectedPageIndex: Int =
        pages.indexOfFirst { page -> page.id == selectedPageId }
}

object HomeLayoutDefaults {
    fun standard(): HomeLayout {
        val firstPage =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 4, rows = 5),
            )

        return HomeLayout(
            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            pages = listOf(firstPage),
            selectedPageId = firstPage.id,
            dock = DockModel(capacity = 5),
        )
    }
}
