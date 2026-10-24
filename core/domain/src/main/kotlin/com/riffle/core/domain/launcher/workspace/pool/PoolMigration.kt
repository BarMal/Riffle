package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.dockFor
import com.riffle.core.domain.launcher.workspace.HomeLayoutWorkspaceMapper
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration

/** The outcome of [PoolMigration.importDock]: the pool with its dock, and what could not be carried over. */
data class PoolDockImport(
    val pool: PlacedItemPool,
    val issues: List<PoolIssue>,
)

data class PoolMigrationResult(
    val pools: Map<HomeLayoutDeviceClass, PlacedItemPool>,
    /** What could not be carried over (ids only). Empty for well-formed data. */
    val issues: List<PoolIssue>,
)

/**
 * One-time, pure migration of [HomeLayoutSet] placed items into a pool per device class. Each stored mode layout
 * becomes the arrangement of its migrated workspace (`ws:<deviceclass>:<mode>`, the same ids
 * [WorkspaceMigration] uses); only `Home` pages carry placed items. Today every item has exactly one reference and
 * nothing is shared across modes, so none is created: `PoolItemId` is `pi:<deviceclass>:<mode>:<LauncherItemId>`,
 * deterministic, so running it twice gives the same value. Never reads or changes [HomeLayoutSet] beyond reading it.
 * The device class's shared dock (`dockFor` plus Library's edge) becomes the pool's dock ([importDock]).
 */
object PoolMigration {
    fun migrate(layoutSet: HomeLayoutSet): PoolMigrationResult {
        val issues = mutableListOf<PoolIssue>()
        val pools =
            WorkspaceMigration.migrate(layoutSet).layouts.mapValues { (deviceClass, workspaces) ->
                val raw = PlacedItemBuilder()
                workspaces.workspaces.forEach { workspace ->
                    LauncherViewMode.entries
                        .firstOrNull { HomeLayoutWorkspaceMapper.workspaceId(deviceClass, it) == workspace.id }
                        ?.let {
                                mode ->
                            raw.add(
                                deviceClass,
                                mode,
                                workspace.id,
                                layoutSet.layoutFor(HomeLayoutKey(mode, deviceClass)),
                            )
                        }
                }
                val docked = importDock(raw.build(), layoutSet, deviceClass)
                val repaired = PoolValidation.repair(docked.pool)
                issues += raw.issues() + docked.issues + repaired.issues
                repaired.pool
            }
        return PoolMigrationResult(pools, issues)
    }

    /**
     * Gives [pool] the shared dock of [deviceClass] in [layoutSet] (`HomeLayoutSet.dockFor`, which falls back to the
     * stored layouts' dock and then the class default, plus Library's edge): pins and panel items become pool items
     * `pi:<deviceclass>:dock:<LauncherItemId>` (deterministic; a repeat or an id [pool] already uses gets a `#n`
     * suffix). Nothing in [pool] is changed or removed, so this is safe on a pool that was edited after its migration;
     * a pool that already has a dock is returned as it is (idempotent). Panel items without a cell are reported as
     * [PoolIssueKind.UNPLACED_ITEM] and dropped. The result is not repaired; callers repair the whole pool.
     */
    fun importDock(
        pool: PlacedItemPool,
        layoutSet: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
    ): PoolDockImport {
        if (pool.dock != null) return PoolDockImport(pool, emptyList())
        val dock = layoutSet.dockFor(deviceClass)
        val prefix = "pi:${deviceClass.name.lowercase()}:dock:"
        val items = LinkedHashMap(pool.items)
        val issues = mutableListOf<PoolIssue>()

        fun mint(item: LauncherItem): PoolItemId {
            val base = prefix + item.id.value
            var candidate = base
            var n = 1
            while (PoolItemId(candidate) in items) candidate = "$base#${++n}"
            return PoolItemId(candidate).also { items[it] = ArrangementIngest.create(it, item) }
        }
        val pins = dock.items.map(::mint)
        val panel =
            dock.panel?.let { page ->
                val placements =
                    page.items.mapNotNull { item ->
                        val at = item.placement
                        if (at == null) {
                            issues += PoolIssue(PoolIssueKind.UNPLACED_ITEM, pageId = page.id)
                            null
                        } else {
                            Placement(mint(item), at)
                        }
                    }
                ArrangementPage(page.id, page.grid, placements, page.generatedContentOverflowCount, page.isPinned)
            }
        val settings = PoolDockSettings.of(dock, layoutSet.libraryDockEdgesByDeviceClass[deviceClass])
        return PoolDockImport(pool.copy(items = items, dock = PoolDock(pins, panel, settings)), issues)
    }

    /**
     * The inverse for the one-time migration check and for importing old backups, never a live mirror: the Home pages
     * of [deviceClass]'s [mode] layout as `LauncherPage`s with the original `LauncherItemId`s (the migration prefix
     * is stripped). Equals the original Home pages for well-formed data.
     */
    fun homePages(
        pool: PlacedItemPool,
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ): List<LauncherPage> {
        val prefix = prefix(deviceClass, mode)
        val arrangement = pool.arrangements[HomeLayoutWorkspaceMapper.workspaceId(deviceClass, mode)]
        return arrangement?.let { ArrangementAdapter.toLauncherPages(it, pool) }.orEmpty().map { page ->
            page.copy(items = page.items.map { it.withId(LauncherItemId(it.id.value.removePrefix(prefix))) })
        }
    }

    private fun prefix(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ) = "pi:${deviceClass.name.lowercase()}:${mode.name.lowercase()}:"

    private fun LauncherItem.withId(id: LauncherItemId): LauncherItem =
        when (this) {
            is AppShortcutItem -> copy(id = id)
            is FolderItem -> copy(id = id)
            is WidgetItem -> copy(id = id)
        }

    private class PlacedItemBuilder {
        private val items = LinkedHashMap<PoolItemId, PoolItem>()
        private val arrangements = LinkedHashMap<WorkspaceId, Arrangement>()
        private val unplaced = mutableListOf<PoolIssue>()

        fun issues(): List<PoolIssue> = unplaced

        fun build() = PlacedItemPool(items, arrangements)

        fun add(
            deviceClass: HomeLayoutDeviceClass,
            mode: LauncherViewMode,
            workspaceId: WorkspaceId,
            layout: HomeLayout,
        ) {
            val prefix = prefix(deviceClass, mode)
            val pages = layout.pages.filter { it.type == LauncherPageType.Home }.map { page(workspaceId, prefix, it) }
            arrangements[workspaceId] = Arrangement(pages)
        }

        private fun page(
            workspaceId: WorkspaceId,
            prefix: String,
            page: LauncherPage,
        ): ArrangementPage {
            val placements =
                page.items.mapNotNull { item ->
                    val at = item.placement
                    if (at == null) {
                        unplaced += PoolIssue(PoolIssueKind.UNPLACED_ITEM, workspaceId, page.id)
                        null
                    } else {
                        val id = uniqueId(prefix + item.id.value)
                        items[id] = ArrangementIngest.create(id, item)
                        Placement(id, at)
                    }
                }
            return ArrangementPage(page.id, page.grid, placements, page.generatedContentOverflowCount, page.isPinned)
        }

        /** A legacy layout may repeat a LauncherItemId; the repeat gets a deterministic suffix. */
        private fun uniqueId(base: String): PoolItemId {
            var candidate = base
            var n = 1
            while (PoolItemId(candidate) in items) candidate = "$base#${++n}"
            return PoolItemId(candidate)
        }
    }
}
