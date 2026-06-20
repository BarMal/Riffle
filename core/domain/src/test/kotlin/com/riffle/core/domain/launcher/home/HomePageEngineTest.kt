package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomePageEngineTest {
    private val engine = HomePageEngine()
    private val layout = HomeLayoutDefaults.standard()

    @Test
    fun addsPageWithoutMutatingOriginalLayout() {
        val page = page(id = "widgets")

        val result = engine.addPage(layout = layout, page = page)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), layout.pages.map { existingPage -> existingPage.id })
        assertEquals(
            listOf(pageId("home"), pageId("widgets")),
            updated.layout.pages.map { existingPage -> existingPage.id },
        )
        assertEquals(pageId("home"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsDuplicatePageIds() {
        val result = engine.addPage(layout = layout, page = page(id = "home"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.DUPLICATE_PAGE_ID, rejected.reason)
    }

    @Test
    fun selectsExistingPage() {
        val layoutWithPages = layout.copy(pages = layout.pages + page(id = "widgets"))

        val result = engine.selectPage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsSelectingMissingPage() {
        val result = engine.selectPage(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun deletesUnselectedPageWithoutChangingSelection() {
        val layoutWithPages =
            layout.copy(
                pages = layout.pages + page(id = "widgets"),
                selectedPageId = pageId("home"),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("home"), updated.layout.selectedPageId)
    }

    @Test
    fun deletingSelectedMiddlePageSelectsNextPage() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets"), page(id = "search")),
                selectedPageId = pageId("widgets"),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home"), pageId("search")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("search"), updated.layout.selectedPageId)
    }

    @Test
    fun deletingSelectedLastPageSelectsPreviousPage() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets")),
                selectedPageId = pageId("widgets"),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("home"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsDeletingMissingPage() {
        val result = engine.deletePage(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsDeletingLastPage() {
        val result = engine.deletePage(layout = layout, pageId = pageId("home"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.CANNOT_DELETE_LAST_PAGE, rejected.reason)
    }

    @Test
    fun movesPageToTargetIndex() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets"), page(id = "search")),
                selectedPageId = pageId("widgets"),
            )

        val result = engine.movePage(layout = layoutWithPages, pageId = pageId("search"), targetIndex = 0)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(
            listOf(pageId("search"), pageId("home"), pageId("widgets")),
            updated.layout.pages.map { page -> page.id },
        )
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsMovingMissingPage() {
        val result = engine.movePage(layout = layout, pageId = pageId("missing"), targetIndex = 0)

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsMovingPageOutsideBounds() {
        val result = engine.movePage(layout = layout, pageId = pageId("home"), targetIndex = 1)

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun entersPageEditModeForExistingPage() {
        val layoutWithPages =
            layout.copy(
                pages = layout.pages + page(id = "widgets"),
                selectedPageId = pageId("home"),
            )

        val result = engine.enterPageEditMode(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("widgets")), updated.layout.editMode)
    }

    @Test
    fun rejectsPageEditModeForMissingPage() {
        val result = engine.enterPageEditMode(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun entersPageOverviewWithoutChangingSelection() {
        val result = engine.enterPageOverview(layout = layout)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("home"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.ManagingPages, updated.layout.editMode)
    }

    @Test
    fun exitsEditModeWithoutChangingSelection() {
        val editingLayout =
            layout.copy(
                editMode = HomeEditMode.EditingPage(pageId = pageId("home")),
            )

        val result = engine.exitEditMode(layout = editingLayout)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("home"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.Browsing, updated.layout.editMode)
    }

    private fun page(id: String): LauncherPage =
        LauncherPage(
            id = pageId(id),
            grid = GridDimensions(columns = 4, rows = 5),
        )

    private fun pageId(value: String): LauncherPageId = LauncherPageId(value)
}
