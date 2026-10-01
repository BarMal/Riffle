package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem

/**
 * Pure joins between an arrangement and the `LauncherPage` values the existing engines edit
 * (`GridPlacementEngine`, `FolderEngine`, ...). `LauncherItemId` is the `PoolItemId` text, which is unique in an
 * arrangement by invariant 2. The way back, [ArrangementIngest], never trusts an engine-minted id as pool-global.
 */
object ArrangementAdapter {
    /** The id the host draws for an unbound widget (Android's `INVALID_APPWIDGET_ID`). */
    val UNBOUND_HOST_ID = HostedWidgetId(0)

    fun toItem(
        item: PoolItem,
        at: GridPlacement?,
    ): LauncherItem =
        when (item) {
            is PoolApp ->
                AppShortcutItem(LauncherItemId(item.id.value), item.appIdentity, item.label, item.appShortcutId, at)
            is PoolFolder ->
                FolderItem(
                    id = LauncherItemId(item.id.value),
                    label = item.label,
                    items =
                        item.entries.map {
                            AppShortcutItem(LauncherItemId(it.entryId), it.appIdentity, it.label, it.appShortcutId)
                        },
                    placement = at,
                )
            is PoolWidget ->
                WidgetItem(
                    id = LauncherItemId(item.id.value),
                    appWidgetId = item.hostedId ?: UNBOUND_HOST_ID,
                    label = item.label,
                    resizeConstraints = item.resizeConstraints,
                    placement = at,
                )
        }

    /** A placement whose item is missing from [pool] is skipped (it draws as an empty cell). */
    fun toLauncherPage(
        page: ArrangementPage,
        pool: PlacedItemPool,
    ): LauncherPage =
        LauncherPage(
            id = page.id,
            type = LauncherPageType.Home,
            grid = page.grid,
            items =
                page.placements.mapNotNull {
                        placement ->
                    pool.items[placement.item]?.let { toItem(it, placement.at) }
                },
            generatedContentOverflowCount = page.generatedContentOverflowCount,
            isPinned = page.isPinned,
        )

    fun toLauncherPages(
        arrangement: Arrangement,
        pool: PlacedItemPool,
    ): List<LauncherPage> = arrangement.pages.map { toLauncherPage(it, pool) }

    /** The placements of an engine-edited page, in item order. Unplaced items have no cell and are skipped. */
    fun placementsOf(page: LauncherPage): List<Placement> =
        page.items.mapNotNull { item -> item.placement?.let { Placement(PoolItemId(item.id.value), it) } }

    fun entriesOf(folder: FolderItem): List<FolderEntry> =
        folder.items.map { FolderEntry(it.id.value, it.appIdentity, it.label, it.appShortcutId) }

    fun hostedIdOrNull(id: HostedWidgetId): HostedWidgetId? = id.takeIf { it != UNBOUND_HOST_ID }
}
