package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult

internal fun applyShortcutOrFolderDropEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
    shortcutEngine: HomeShortcutEngine,
    folderEngine: FolderEngine,
): ShortcutOrFolderDropEditResult =
    when (action) {
        is LauncherShellAction.MoveHomeShortcutToCell ->
            folderEngine
                .createFolderFromDropOnSelectedPage(
                    layout = layout,
                    sourceItemId = action.itemId,
                    targetCell = action.cell,
                )
                ?.toShortcutOrFolderDropEditResult()
                ?: shortcutEngine.applyEdit(action = action, layout = layout).toShortcutOrFolderDropEditResult()

        else -> shortcutEngine.applyEdit(action = action, layout = layout).toShortcutOrFolderDropEditResult()
    }

internal sealed interface ShortcutOrFolderDropEditResult {
    data class Updated(val layout: HomeLayout) : ShortcutOrFolderDropEditResult

    data object Rejected : ShortcutOrFolderDropEditResult
}

private fun FolderEditResult.toShortcutOrFolderDropEditResult(): ShortcutOrFolderDropEditResult =
    when (this) {
        is FolderEditResult.Updated -> ShortcutOrFolderDropEditResult.Updated(layout)
        is FolderEditResult.Rejected -> ShortcutOrFolderDropEditResult.Rejected
    }

private fun HomeShortcutResult.toShortcutOrFolderDropEditResult(): ShortcutOrFolderDropEditResult =
    when (this) {
        is HomeShortcutResult.Updated -> ShortcutOrFolderDropEditResult.Updated(layout)
        is HomeShortcutResult.Rejected -> ShortcutOrFolderDropEditResult.Rejected
    }
