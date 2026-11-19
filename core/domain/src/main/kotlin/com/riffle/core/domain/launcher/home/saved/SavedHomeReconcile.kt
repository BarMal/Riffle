package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.holds
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.pool.ArrangementAdapter
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolFolder
import com.riffle.core.domain.launcher.workspace.pool.PoolStackMember
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget

/** What the reconcile rule needs to know about the device now. Plain values, so the rule is pure. */
data class SavedHomeContext(
    /** Launchable apps that are installed (and enabled) now. */
    val installed: Set<AppIdentity>,
    /** Hidden apps: Home does not show them (the same rule "Add all apps" and the Library use). */
    val hidden: Set<AppIdentity>,
    /** The Dock's apps: an app in the Dock is not also placed on Home (the Home/Dock rule of the pool). */
    val dockApps: Set<DockApp>,
    /** The grid a Home page has now (the layout's workspace grid). */
    val grid: GridDimensions,
    /** The Home pages as they are now: a widget that matches the saved one keeps its live host id. */
    val livePages: List<LauncherPage>,
    /** The provider of a live host id (null when unknown). */
    val liveProviderOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    /** A fresh id every call; never equal to a live id (the caller checks against [livePages]). */
    val newId: () -> String,
)

/** An app of the Dock, by identity and shortcut (a shortcut is not the app's own icon). */
data class DockApp(
    val identity: AppIdentity,
    val shortcut: AppShortcutId? = null,
)

/** A platform widget that must be bound before it shows: member [memberIndex] of item [itemId]. */
data class WidgetBind(
    val itemId: LauncherItemId,
    val memberIndex: Int,
    val provider: WidgetProviderIdentity,
)

data class ReconciledHome(
    /** The Home pages to show; every widget not reused from the live Home is unbound (host id 0). */
    val pages: List<LauncherPage>,
    /** The widgets the app layer binds next; one that cannot be bound stays a placeholder. */
    val binds: List<WidgetBind>,
    val droppedApps: Int,
    /** Live widgets kept as they are (same provider, same cell and span). */
    val keptWidgets: Int,
)

/**
 * The rule that turns a saved layout into Home pages that are valid today (ADR 0003, Amendment 17). Pure, never throws.
 *
 * - Apps that are no longer installed, hidden apps and apps now in the Dock are dropped; a folder keeps its remaining
 *   apps (one app stays a folder, as after an uninstall), an emptied folder is removed.
 * - Apps installed since are not added.
 * - Items never overlap and always fit the grid of today: whatever does not is moved to the first free cell, on its
 *   page first, then later pages, then earlier ones, then a new page at the end.
 * - A page the rule emptied is dropped, trailing empty pages are dropped, and one page always stays; a page the user
 *   pinned stays.
 * - Every item gets a new id (the saved one may mean something else in the live Home), except a widget kept live.
 */
object SavedHomeReconciler {
    fun reconcile(
        saved: SavedHomeLayout,
        context: SavedHomeContext,
    ): ReconciledHome {
        val run = Run(saved, context)
        val built = saved.pages.mapIndexed { index, page -> run.page(index, page) }
        val fitted = run.fit(built)
        val pages = run.trim(fitted)
        return ReconciledHome(pages, run.binds, run.dropped, run.kept)
    }

    /** [pages] with the host ids [bound] resolved: key is the item id and the member index. */
    fun withBoundHosts(
        pages: List<LauncherPage>,
        bound: Map<Pair<LauncherItemId, Int>, HostedWidgetId>,
    ): List<LauncherPage> =
        if (bound.isEmpty()) {
            pages
        } else {
            pages.map { page ->
                page.copy(
                    items =
                        page.items.map { item ->
                            if (item is WidgetItem) item.boundTo(bound) else item
                        },
                )
            }
        }

    /** [layout] with its Home pages replaced by [homePages], at the place of the first Home page. */
    fun replacingHomePages(
        layout: HomeLayout,
        homePages: List<LauncherPage>,
    ): HomeLayout {
        val firstHome = layout.pages.indexOfFirst { it.type == LauncherPageType.Home }
        val others = layout.pages.filter { it.type != LauncherPageType.Home }
        val at =
            if (firstHome < 0) {
                others.size
            } else {
                layout.pages.take(
                    firstHome,
                ).count { it.type != LauncherPageType.Home }
            }
        val pages = others.take(at) + homePages + others.drop(at)
        val selected = layout.selectedPageId.takeIf { id -> pages.any { it.id == id } } ?: pages.first().id
        return layout.copy(pages = pages, selectedPageId = selected)
    }

    private fun WidgetItem.boundTo(bound: Map<Pair<LauncherItemId, Int>, HostedWidgetId>): WidgetItem {
        val members =
            members.mapIndexed { index, member ->
                bound[id to index]?.let { member.copy(appWidgetId = it) } ?: member
            }
        return if (isStack) {
            copy(appWidgetId = members.first().appWidgetId, stack = members)
        } else {
            copy(appWidgetId = members.first().appWidgetId)
        }
    }

    @Suppress("TooManyFunctions") // One reconcile run: the item kinds, the fit and the trim.
    private class Run(
        private val saved: SavedHomeLayout,
        private val context: SavedHomeContext,
    ) {
        val binds = mutableListOf<WidgetBind>()
        var dropped = 0
        var kept = 0
        private val reused = HashSet<LauncherItemId>()
        private val engine = GridPlacementEngine()

        fun page(
            index: Int,
            page: ArrangementPage,
        ): LauncherPage =
            LauncherPage(
                id = LauncherPageId("page-${context.newId()}"),
                grid = context.grid,
                items =
                    page.placements.mapNotNull { placement ->
                        when (val item = saved.items[placement.item]) {
                            is PoolApp -> app(item, placement.at)
                            is PoolFolder -> folder(item, placement.at)
                            is PoolWidget -> widget(index, item, placement.at)
                            null -> null
                        }
                    },
                isPinned = page.isPinned,
            )

        private fun wanted(
            identity: AppIdentity,
            shortcut: AppShortcutId?,
        ): Boolean =
            identity in context.installed && identity !in context.hidden &&
                DockApp(identity, shortcut) !in context.dockApps

        private fun app(
            item: PoolApp,
            at: GridPlacement,
        ): LauncherItem? =
            if (wanted(item.appIdentity, item.appShortcutId)) {
                AppShortcutItem(
                    LauncherItemId(context.newId()),
                    item.appIdentity,
                    item.label,
                    item.appShortcutId,
                    at,
                )
            } else {
                dropped++
                null
            }

        private fun folder(
            item: PoolFolder,
            at: GridPlacement,
        ): LauncherItem? {
            val entries = item.entries.filter { wanted(it.appIdentity, it.appShortcutId) }
            dropped += item.entries.size - entries.size
            return entries.takeIf { it.isNotEmpty() }?.let {
                FolderItem(
                    id = LauncherItemId(context.newId()),
                    label = item.label,
                    items =
                        it.map { e ->
                            AppShortcutItem(LauncherItemId(e.entryId), e.appIdentity, e.label, e.appShortcutId)
                        },
                    placement = at,
                )
            }
        }

        private fun widget(
            pageIndex: Int,
            item: PoolWidget,
            at: GridPlacement,
        ): LauncherItem {
            val members = item.members()
            liveMatch(pageIndex, members, at)?.let {
                reused += it.id
                kept++
                return it
            }
            val id = LauncherItemId(context.newId())
            val stacked =
                members.map { m ->
                    StackedWidget(
                        ArrangementAdapter.UNBOUND_HOST_ID,
                        m.label,
                        m.resizeConstraints,
                        GlanceWidgets.kindOf(m.provider),
                        m.provider.takeIf { GlanceWidgets.kindOf(it) == null },
                    )
                }
            members.forEachIndexed { index, m ->
                val provider = m.provider
                if (provider != null && GlanceWidgets.kindOf(provider) == null) {
                    binds += WidgetBind(id, index, provider)
                }
            }
            val lead = stacked.first()
            return WidgetItem(
                id = id,
                appWidgetId = lead.appWidgetId,
                label = lead.label,
                resizeConstraints = item.resizeConstraints,
                placement = at,
                glance = lead.glance,
                provider = lead.provider,
                stack = if (stacked.size > 1) stacked else emptyList(),
                shownIndex = if (stacked.size > 1) item.shown.coerceIn(0, stacked.lastIndex) else 0,
            )
        }

        private fun PoolWidget.members(): List<PoolStackMember> =
            stack.ifEmpty { listOf(PoolStackMember(label, resizeConstraints, provider, null)) }

        /** The live widget at the same page, cell and span whose every member has the saved member's provider. */
        private fun liveMatch(
            pageIndex: Int,
            members: List<PoolStackMember>,
            at: GridPlacement,
        ): WidgetItem? {
            val page = context.livePages.getOrNull(pageIndex) ?: return null
            return page.items.filterIsInstance<WidgetItem>().firstOrNull { live ->
                live.id !in reused && live.placement == at && live.members.size == members.size &&
                    live.members.zip(members).all { (l, s) -> s.provider != null && providerOf(l) == s.provider }
            }
        }

        private fun providerOf(member: StackedWidget): WidgetProviderIdentity? =
            member.glance?.let(GlanceWidgets::identity)
                ?: member.appWidgetId.takeIf { it != ArrangementAdapter.UNBOUND_HOST_ID }
                    ?.let(context.liveProviderOf)

        /** Every item fits the grid and none overlaps; the misfits go to the first free cell, else a new page. */
        fun fit(pages: List<LauncherPage>): List<LauncherPage> {
            val kept =
                pages.map { page ->
                    val inside = mutableListOf<LauncherItem>()
                    val misfits = mutableListOf<LauncherItem>()
                    page.items.forEach { item ->
                        val clear =
                            context.grid.holds(item.placement) &&
                                inside.none { overlaps(it, item) }
                        if (clear) inside += item else misfits += item
                    }
                    page.copy(items = inside) to misfits
                }
            var result = kept.map { it.first }
            kept.forEachIndexed { index, (_, misfits) ->
                misfits.sortedBy { it.readingIndex() }.forEach { item -> result = place(result, index, item) }
            }
            return result
        }

        private fun place(
            pages: List<LauncherPage>,
            origin: Int,
            item: LauncherItem,
        ): List<LauncherPage> {
            val order = pages.indices.sortedBy { if (it >= origin) it - origin else pages.size + it }
            for (index in order) {
                placedOn(pages[index], item)?.let { return pages.toMutableList().apply { set(index, it) } }
            }
            val fresh =
                LauncherPage(LauncherPageId("page-${context.newId()}"), grid = context.grid)
            return pages + (placedOn(fresh, item) ?: fresh)
        }

        private fun placedOn(
            page: LauncherPage,
            item: LauncherItem,
        ): LauncherPage? =
            spans(item).firstNotNullOfOrNull { span ->
                (engine.placeItemInFirstAvailableCell(page, item, span) as? PlaceLauncherItemResult.Placed)?.page
            }

        private fun spans(item: LauncherItem): List<GridSpan> =
            if (item is WidgetItem) {
                val wanted = item.placement?.span ?: GridSpan()
                val clamped =
                    GridSpan(
                        wanted.columns.coerceAtMost(context.grid.columns),
                        wanted.rows.coerceAtMost(context.grid.rows),
                    )
                listOf(wanted, clamped).distinct()
            } else {
                listOf(GridSpan())
            }

        fun trim(pages: List<LauncherPage>): List<LauncherPage> {
            val kept =
                pages.filterIndexed { index, page ->
                    val emptyBySaving = saved.pages.getOrNull(index)?.placements?.isEmpty() == true
                    page.items.isNotEmpty() || page.isPinned || emptyBySaving
                }
            val trimmed = kept.dropLastWhile { it.items.isEmpty() && !it.isPinned }
            if (trimmed.isNotEmpty()) return trimmed
            val first =
                pages.firstOrNull() ?: LauncherPage(LauncherPageId("page-${context.newId()}"), grid = context.grid)
            return listOf(first.copy(items = emptyList()))
        }

        private fun overlaps(
            a: LauncherItem,
            b: LauncherItem,
        ): Boolean {
            val p = a.placement
            val q = b.placement
            return p != null && q != null &&
                p.cell.column < q.cell.column + q.span.columns && q.cell.column < p.cell.column + p.span.columns &&
                p.cell.row < q.cell.row + q.span.rows && q.cell.row < p.cell.row + p.span.rows
        }

        private fun LauncherItem.readingIndex(): Int =
            placement?.let { it.cell.row * context.grid.columns + it.cell.column } ?: Int.MAX_VALUE
    }
}
