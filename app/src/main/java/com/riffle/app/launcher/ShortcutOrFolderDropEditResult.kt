package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeAppPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeSelection
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeSpill
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.containsHomeApp
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
            } else if (action.spill) {
                HomeSpill.dropMoved(
                    layout = layout,
                    itemId = action.itemId,
                    sourcePageId = action.sourcePageId,
                    targetPageId = action.targetPageId,
                    cell = action.cell,
                    pushed = action.pushed,
                ).toShortcutOrFolderDropEditResult()
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

        is LauncherShellAction.DropExternalHomeItem ->
            dropExternalItem(action, layout, shortcutEngine, folderEngine, installedApps)

        is LauncherShellAction.DropHomeGroup ->
            (
                if (action.dropOnItemId != null) {
                    HomeSelection.dropGroupOnFolder(layout, action.targetPageId, action.dropOnItemId)
                } else {
                    HomeSelection.dropGroup(layout, action.targetPageId, action.cell)
                }
            ).toShortcutOrFolderDropEditResult()

        else -> shortcutEngine.applyEdit(action = action, layout = layout).toShortcutOrFolderDropEditResult()
    }

/**
 * A drop of an item that is not on a page yet: an app of the Library becomes a new Home item (rejected when Home
 * shows the app already, as "Add to Home" is), an item of the Dock moves to Home. It lands with the push and the
 * spill rule ([HomeSpill]), or, dropped on top of an app or folder, joins it in a folder there.
 */
private fun dropExternalItem(
    action: LauncherShellAction.DropExternalHomeItem,
    layout: HomeLayout,
    shortcutEngine: HomeShortcutEngine,
    folderEngine: FolderEngine,
    installedApps: List<InstalledApp>,
): ShortcutOrFolderDropEditResult {
    val rejected = ShortcutOrFolderDropEditResult.Rejected
    val (base, item) =
        when (val source = action.source) {
            is ExternalHomeDragSource.Library -> {
                if (layout.containsHomeApp(source.app.identity)) return rejected
                layout to HomeAppPlacement.itemFor(layout, source.app)
            }

            is ExternalHomeDragSource.Dock -> {
                val pinned = layout.dock.items.firstOrNull { it.id == source.itemId } ?: return rejected
                if (pinned !is AppShortcutItem && pinned !is FolderItem) return rejected
                layout.copy(dock = layout.dock.copy(items = layout.dock.items.filterNot { it.id == pinned.id })) to
                    pinned.withPlacement(GridPlacement(action.cell))
            }
        }
    if (action.dropOnItemId == null) {
        return HomeSpill
            .drop(base, item.withPlacement(GridPlacement(action.cell)), action.targetPageId, action.cell, action.pushed)
            .toShortcutOrFolderDropEditResult()
    }
    val target =
        base.pages.firstOrNull { it.id == action.targetPageId }
            ?.items?.firstOrNull { it.id == action.dropOnItemId }?.placement?.cell ?: return rejected
    val placed =
        (HomeSpill.dropGroup(base, listOf(item), action.targetPageId, GridCell(0, 0)) as? HomeShortcutResult.Updated)
            ?.layout ?: return rejected
    // The item joins the folder only when it found room on that page; otherwise it simply landed, nothing lost.
    if (placed.pages.first { it.id == action.targetPageId }.items.none { it.id == item.id }) {
        return ShortcutOrFolderDropEditResult.Updated(placed)
    }
    return applyShortcutOrFolderDropEdit(
        action = LauncherShellAction.MoveHomeShortcutToCell(item.id, target, action.folderLabel),
        layout = placed.copy(selectedPageId = action.targetPageId),
        shortcutEngine = shortcutEngine,
        folderEngine = folderEngine,
        installedApps = installedApps,
    )
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
