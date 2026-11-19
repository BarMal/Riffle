package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackResult

internal fun applyShortcutOrFolderDropEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
    shortcutEngine: HomeShortcutEngine,
    folderEngine: FolderEngine,
    installedApps: List<InstalledApp> = emptyList(),
): ShortcutOrFolderDropEditResult =
    when (action) {
        is LauncherShellAction.MoveHomeShortcutToCell ->
            stackedByDrop(layout, action)
                ?: folderEngine
                .createFolderFromDropOnSelectedPage(
                    layout = layout,
                    sourceItemId = action.itemId,
                    targetCell = action.cell,
                    installedApps = installedApps,
                    fallbackLabel = action.folderLabel,
                )
                ?.toShortcutOrFolderDropEditResult()
                ?: shortcutEngine.applyEdit(action = action, layout = layout).toShortcutOrFolderDropEditResult()

        is LauncherShellAction.MoveHomeItemToPage ->
            shortcutEngine
                .moveItemToPage(
                    layout = layout,
                    itemId = action.itemId,
                    sourcePageId = action.sourcePageId,
                    targetPageId = action.targetPageId,
                    cell = action.cell,
                ).toShortcutOrFolderDropEditResult()

        is LauncherShellAction.DropHomeItem ->
            if (action.dropOnItemId != null) {
                dropOnAnotherPage(action, layout, shortcutEngine, folderEngine, installedApps)
            } else {
                shortcutEngine
                    .dropWithPush(
                        layout = layout,
                        itemId = action.itemId,
                        sourcePageId = action.sourcePageId,
                        targetPageId = action.targetPageId,
                        cell = action.cell,
                        pushed = action.pushed,
                    ).toShortcutOrFolderDropEditResult()
            }

        else -> shortcutEngine.applyEdit(action = action, layout = layout).toShortcutOrFolderDropEditResult()
    }

/**
 * A widget dropped on top of a widget (or a stack) of the same span: it joins the stack and the stack shows it. Null
 * when the drop is not that (anything else is a folder or a move), so the ordinary rules decide.
 */
private fun stackedByDrop(
    layout: HomeLayout,
    action: LauncherShellAction.MoveHomeShortcutToCell,
): ShortcutOrFolderDropEditResult? {
    val items = layout.selectedPage.items
    val dragged = items.firstOrNull { it.id == action.itemId } as? WidgetItem ?: return null
    val target = items.firstOrNull { it.id != dragged.id && it.placement?.cell == action.cell } as? WidgetItem
    return target
        ?.takeIf { WidgetStackEngine().canStack(it, dragged) }
        ?.let { WidgetStackEngine().stackOnto(layout, dragged.id, it.id) }
        ?.let { result ->
            when (result) {
                is WidgetStackResult.Updated -> ShortcutOrFolderDropEditResult.Updated(result.layout)
                is WidgetStackResult.Rejected -> null
            }
        }
}

/**
 * A drop on top of an item of another page: the dragged item first takes the first free cell of that page, then the
 * ordinary same-page drop on top runs there (a folder of the two, or the item added to the folder), with that page
 * selected. A full page refuses, so the item stays where it was.
 */
private fun dropOnAnotherPage(
    action: LauncherShellAction.DropHomeItem,
    layout: HomeLayout,
    shortcutEngine: HomeShortcutEngine,
    folderEngine: FolderEngine,
    installedApps: List<InstalledApp>,
): ShortcutOrFolderDropEditResult {
    val target =
        layout.pages.firstOrNull { it.id == action.targetPageId }
            ?.items?.firstOrNull { it.id == action.dropOnItemId }?.placement?.cell
            ?: return ShortcutOrFolderDropEditResult.Rejected
    val moved =
        shortcutEngine.moveItemToFirstFreeCell(layout, action.itemId, action.sourcePageId, action.targetPageId)
    return when (moved) {
        is HomeShortcutResult.Rejected -> ShortcutOrFolderDropEditResult.Rejected
        is HomeShortcutResult.Updated ->
            applyShortcutOrFolderDropEdit(
                action = LauncherShellAction.MoveHomeShortcutToCell(action.itemId, target, action.folderLabel),
                layout = moved.layout.copy(selectedPageId = action.targetPageId),
                shortcutEngine = shortcutEngine,
                folderEngine = folderEngine,
                installedApps = installedApps,
            )
    }
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
