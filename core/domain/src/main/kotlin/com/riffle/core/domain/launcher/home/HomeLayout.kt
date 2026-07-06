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

internal fun HomeLayout.withUpdatedSelectedPage(page: LauncherPage): HomeLayout =
    copy(
        pages =
            pages.map { existingPage ->
                when (existingPage.id) {
                    page.id -> page
                    else -> existingPage
                }
            },
    )

object HomeLayoutDefaults {
    fun standard(deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE): HomeLayout =
        HomeLayoutSettings.standard(deviceClass).let { settings ->
            LauncherPage(
                id = LauncherPageId("home"),
                grid = settings.grid.dimensions,
            ).let { firstPage ->
                HomeLayout(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    pages = listOf(firstPage),
                    selectedPageId = firstPage.id,
                    dock = DockModel(capacity = deviceClass.standardDockCapacity),
                    settings = settings,
                )
            }
        }
}

private val HomeLayoutDeviceClass.standardDockCapacity: Int
    get() =
        when (this) {
            HomeLayoutDeviceClass.PHONE -> 5
            HomeLayoutDeviceClass.FOLDABLE -> 6
            HomeLayoutDeviceClass.TABLET -> 7
        }
