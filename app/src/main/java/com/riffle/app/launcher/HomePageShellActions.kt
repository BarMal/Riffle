package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomePageEditRejectionReason
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine

fun HomePageEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.EnterHomeEditMode ->
            applyModeEdit(action = action, layout = layout)

        LauncherShellAction.ExitHomeEditMode ->
            applyModeEdit(action = action, layout = layout)

        LauncherShellAction.EnterHomePageOverview ->
            applyModeEdit(action = action, layout = layout)

        LauncherShellAction.AddHomePage ->
            applyPageCreationEdit(action = action, layout = layout)

        LauncherShellAction.DuplicateSelectedHomePage ->
            applyPageCreationEdit(action = action, layout = layout)

        LauncherShellAction.SelectPreviousHomePage ->
            applyPageSelectionEdit(action = action, layout = layout)

        LauncherShellAction.SelectNextHomePage ->
            applyPageSelectionEdit(action = action, layout = layout)

        is LauncherShellAction.SelectHomePage ->
            applyPageSelectionEdit(action = action, layout = layout)

        LauncherShellAction.MoveSelectedHomePageLeft ->
            applyPageMoveEdit(action = action, layout = layout)

        LauncherShellAction.MoveSelectedHomePageRight ->
            applyPageMoveEdit(action = action, layout = layout)

        LauncherShellAction.DeleteSelectedHomePage ->
            applyPageDeletionEdit(layout = layout)

        is LauncherShellAction.SelectHomeGridDimensions,
        is LauncherShellAction.SelectLibraryPageCompaction,
        is LauncherShellAction.SelectHomeLabelBackgroundAlpha,
        is LauncherShellAction.SelectHomeLabelTextSize,
        is LauncherShellAction.SelectHomeLabelTextVisible,
        is LauncherShellAction.SelectHomeLabelMaxWidth,
        is LauncherShellAction.SelectLauncherViewMode,
        -> applyHomeLayoutConfigurationEdit(action = action, layout = layout)

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.applyModeEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.EnterHomeEditMode ->
            enterPageEditMode(
                layout = layout,
                pageId = layout.selectedPageId,
            )

        LauncherShellAction.ExitHomeEditMode -> exitEditMode(layout = layout)
        LauncherShellAction.EnterHomePageOverview -> enterPageOverview(layout = layout)
        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.applyPageCreationEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.AddHomePage -> addAndSelectHomePage(layout = layout)
        LauncherShellAction.DuplicateSelectedHomePage -> duplicateSelectedHomePage(layout = layout)
        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.applyPageSelectionEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.SelectPreviousHomePage -> selectPageAtOffset(layout = layout, offset = -1)
        LauncherShellAction.SelectNextHomePage -> selectPageAtOffset(layout = layout, offset = 1)
        is LauncherShellAction.SelectHomePage -> selectPage(layout = layout, pageId = action.pageId)
        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.applyPageMoveEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.MoveSelectedHomePageLeft -> moveSelectedPageByOffset(layout = layout, offset = -1)
        LauncherShellAction.MoveSelectedHomePageRight -> moveSelectedPageByOffset(layout = layout, offset = 1)
        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.applyPageDeletionEdit(layout: HomeLayout): HomePageEditResult =
    deletePage(
        layout = layout,
        pageId = layout.selectedPageId,
    )

private fun HomePageEngine.addAndSelectHomePage(layout: HomeLayout): HomePageEditResult =
    layout.newHomePage().let { page ->
        when (val result = addPage(layout = layout, page = page)) {
            is HomePageEditResult.Updated -> selectPage(layout = result.layout, pageId = page.id)
            is HomePageEditResult.Rejected -> result
        }
    }

private fun HomePageEngine.duplicateSelectedHomePage(layout: HomeLayout): HomePageEditResult =
    layout.newHomePage().let { page ->
        duplicatePage(
            layout = layout,
            pageId = layout.selectedPageId,
            duplicatedPageId = page.id,
            itemIdProvider = layout.duplicatedItemIdProvider(page.id),
        )
    }

private fun HomePageEngine.selectPageAtOffset(
    layout: HomeLayout,
    offset: Int,
): HomePageEditResult =
    layout.pages.getOrNull(layout.selectedPageIndex + offset)
        ?.let { page -> selectPage(layout = layout, pageId = page.id) }
        ?: HomePageEditResult.Rejected(
            HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS,
        )

private fun HomePageEngine.moveSelectedPageByOffset(
    layout: HomeLayout,
    offset: Int,
): HomePageEditResult =
    (layout.selectedPageIndex + offset)
        .takeIf { targetIndex -> targetIndex in layout.pages.indices }
        ?.let { targetIndex ->
            movePage(
                layout = layout,
                pageId = layout.selectedPageId,
                targetIndex = targetIndex,
            )
        }
        ?: HomePageEditResult.Rejected(
            HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS,
        )
