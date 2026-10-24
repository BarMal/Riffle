package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * What a legacy fallback session (a process that ran without a usable pool) leaves behind, merged into the pool's
 * view. The pool is preferred: everything it holds stays as it is, however old the set the session started from
 * was. The session's additions (items whose id no pool item has) are added on top: on the page with the same id (else
 * the first `Home` page), in their own cell when it is free, else the first free cell; new dock pins are appended. A
 * removal or move made in the fallback session is not carried over (the pool may be newer than what that session saw).
 */
internal object PoolHomeFallbackMerge {
    private val engine = GridPlacementEngine()

    /** [stored] with each pooled key's `Home` pages and dock replaced by [projected]'s plus the session's additions. */
    fun merge(
        state: PoolStoreState,
        projected: HomeLayoutSet,
        stored: HomeLayoutSet,
    ): HomeLayoutSet {
        val layouts =
            stored.layouts.mapValues { (key, layout) ->
                val pool = state.pools[key.deviceClass.workspaceLayoutClass()]
                if (pool == null || key !in projected.layouts) {
                    layout
                } else {
                    withAdditions(projected.layoutFor(key), layout, pool)
                }
            }
        val docks =
            stored.docks.mapValues { (deviceClass, dock) ->
                val pool = state.pools[deviceClass.workspaceLayoutClass()]
                val poolDock = projected.docks[deviceClass]
                if (pool == null || poolDock == null) {
                    dock
                } else {
                    poolDock.copy(items = poolDock.items + dock.items.filter { PoolItemId(it.id.value) !in pool.items })
                }
            }
        return stored.copy(layouts = layouts, docks = docks)
    }

    private fun withAdditions(
        base: HomeLayout,
        session: HomeLayout,
        pool: PlacedItemPool,
    ): HomeLayout {
        var pages = base.pages
        session.pages.filter { it.type == LauncherPageType.Home }.forEach { page ->
            page.items.filter { PoolItemId(it.id.value) !in pool.items }.forEach { item ->
                pages = place(pages, page.id.value, item)
            }
        }
        val pins = session.dock.items.filter { PoolItemId(it.id.value) !in pool.items }
        return base.copy(pages = pages, dock = base.dock.copy(items = base.dock.items + pins))
    }

    private fun place(
        pages: List<LauncherPage>,
        pageId: String,
        item: LauncherItem,
    ): List<LauncherPage> {
        val home = pages.filter { it.type == LauncherPageType.Home }
        val target = home.firstOrNull { it.id.value == pageId } ?: home.firstOrNull() ?: return pages
        val placed =
            (engine.placeItem(target, item) as? PlaceLauncherItemResult.Placed)
                ?: (
                    engine.placeItemInFirstAvailableCell(target, item, item.placement?.span ?: GridSpan())
                        as? PlaceLauncherItemResult.Placed
                )
        return if (placed == null) pages else pages.map { if (it.id == target.id) placed.page else it }
    }
}
