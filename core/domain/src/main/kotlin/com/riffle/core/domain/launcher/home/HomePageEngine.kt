package com.riffle.core.domain.launcher.home

class HomePageEngine {
    fun addPage(
        layout: HomeLayout,
        page: LauncherPage,
    ): HomePageEditResult =
        when {
            layout.pages.any { existingPage -> existingPage.id == page.id } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.DUPLICATE_PAGE_ID)

            else ->
                HomePageEditResult.Updated(layout.copy(pages = layout.pages + page))
        }

    fun selectPage(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            else ->
                HomePageEditResult.Updated(layout.copy(selectedPageId = pageId))
        }

    fun deletePage(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            layout.pages.size == 1 ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.CANNOT_DELETE_LAST_PAGE)

            else -> {
                val deletedIndex = layout.pages.indexOfFirst { page -> page.id == pageId }
                val remainingPages = layout.pages.filterNot { page -> page.id == pageId }
                val selectedPageId =
                    when (layout.selectedPageId) {
                        pageId -> remainingPages[deletedIndex.coerceAtMost(remainingPages.lastIndex)].id
                        else -> layout.selectedPageId
                    }

                HomePageEditResult.Updated(
                    layout.copy(
                        pages = remainingPages,
                        selectedPageId = selectedPageId,
                    ),
                )
            }
        }

    fun movePage(
        layout: HomeLayout,
        pageId: LauncherPageId,
        targetIndex: Int,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            targetIndex !in layout.pages.indices ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(pages = layout.pages.moveItem(pageId = pageId, targetIndex = targetIndex)),
                )
        }

    private fun List<LauncherPage>.moveItem(
        pageId: LauncherPageId,
        targetIndex: Int,
    ): List<LauncherPage> =
        first { page -> page.id == pageId }.let { movingPage ->
            filterNot { page -> page.id == pageId }.toMutableList()
                .apply { add(targetIndex, movingPage) }
                .toList()
        }
}

sealed interface HomePageEditResult {
    data class Updated(val layout: HomeLayout) : HomePageEditResult

    data class Rejected(val reason: HomePageEditRejectionReason) : HomePageEditResult
}

enum class HomePageEditRejectionReason {
    PAGE_NOT_FOUND,
    DUPLICATE_PAGE_ID,
    CANNOT_DELETE_LAST_PAGE,
    INDEX_OUT_OF_BOUNDS,
}
