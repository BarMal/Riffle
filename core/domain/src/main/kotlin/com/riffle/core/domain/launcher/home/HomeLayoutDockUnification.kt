@file:Suppress("TooManyFunctions")

package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId

/**
 * The positions a shared dock can take: the edges every view mode can draw it on.
 *
 * The dock is one dock across modes (#1205), so an edge only one mode can place -- the top edge,
 * which Cards draws and the home modes do not -- would leave it in a different place depending on
 * the mode, which is exactly what sharing it rules out.
 */
val sharedDockPositions: List<DockPosition> =
    LauncherViewMode.entries
        .map { mode -> mode.placeableDockPositions.toSet() }
        .reduce { shared, placeable -> shared intersect placeable }
        .let { shared -> DockPosition.entries.filter { position -> position in shared } }

/** Name of the folder that collects rescued dock pins when no home page has a free cell for them. */
const val FROM_DOCK_FOLDER_LABEL = "From dock"

/**
 * Which stored layout's dock becomes [deviceClass]'s shared dock: the mode that device class is
 * showing (the active mode for the active device class, its preferred mode otherwise), falling back
 * to any stored layout of that class.
 */
internal fun dockSourceKey(
    deviceClass: HomeLayoutDeviceClass,
    activeKey: HomeLayoutKey,
    layouts: Map<HomeLayoutKey, HomeLayout>,
    preferredModesByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode>,
): HomeLayoutKey? {
    val showing =
        if (deviceClass == activeKey.deviceClass) activeKey.viewMode else preferredModesByDeviceClass[deviceClass]
    return showing
        ?.let { mode -> HomeLayoutKey(viewMode = mode, deviceClass = deviceClass) }
        ?.takeIf { key -> key in layouts }
        ?: layouts.keys
            .filter { key -> key.deviceClass == deviceClass }
            .minByOrNull { key -> key.viewMode.ordinal }
}

/**
 * Stores [layout]'s pages, template and settings under [key] while keeping the shared dock.
 *
 * For a layout that was generated rather than edited -- a template seed -- whose dock is only a
 * default and must not replace the one the user has built up in every other mode.
 */
fun HomeLayoutSet.withLayoutKeepingDock(
    key: HomeLayoutKey,
    layout: HomeLayout,
): HomeLayoutSet =
    copy(
        layouts =
            layouts +
                (key to layout.copy(viewMode = key.viewMode).withSharedDock(dockFor(key.deviceClass))),
    )

/**
 * The dock every mode on [deviceClass] shows.
 *
 * A device class with no entry in [docks] -- one first reached by copying a layout across, say
 * -- falls back to whatever dock a stored layout of that class carries, then to the class's
 * default.
 */
fun HomeLayoutSet.dockFor(deviceClass: HomeLayoutDeviceClass): DockModel =
    docks[deviceClass]
        ?: layouts.entries.firstOrNull { (key, _) -> key.deviceClass == deviceClass }?.value?.dock
        ?: HomeLayoutDefaults.standard(deviceClass).dock

/** The shared dock of each device class [layouts] holds, taken from the mode each one shows. */
internal fun docksChosenFrom(
    activeKey: HomeLayoutKey,
    layouts: Map<HomeLayoutKey, HomeLayout>,
    preferredModesByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode>,
): Map<HomeLayoutDeviceClass, DockModel> =
    layouts.keys
        .map { key -> key.deviceClass }
        .distinct()
        .mapNotNull { deviceClass ->
            dockSourceKey(deviceClass, activeKey, layouts, preferredModesByDeviceClass)
                ?.let { key -> layouts.getValue(key).dock }
                ?.let { dock -> deviceClass to dock }
        }.toMap()

/**
 * This layout showing [dock] instead of whatever dock it was stored with.
 *
 * A side dock takes a column from the workspace and a horizontal one gives it back, so a layout
 * stored beside a different dock is re-fitted to the grid the shared one leaves -- otherwise a dock
 * moved in one mode would strand the edge column of every other mode's pages.
 */
internal fun HomeLayout.withSharedDock(dock: DockModel): HomeLayout {
    if (this.dock == dock) return this
    val docked = copy(dock = dock)
    return if (docked.workspaceGrid == workspaceGrid) docked else docked.reflowedToWorkspaceGrid()
}

/**
 * Migrates a set decoded from the per-mode dock format to one dock per device class (#1205).
 *
 * For each device class the dock of the mode it is showing wins outright -- its pins, edge, size,
 * appearance and budgets -- so a conflicting setting takes that mode's value. A top edge, which only
 * Cards could place, comes down to the bottom edge every mode can.
 *
 * Nothing pinned is dropped. An item that only another mode's dock (or dock panel) held is rescued
 * onto that mode's own home pages, where the user curated it; an item that only the Cards dock
 * held goes to the showing mode's pages instead, because Cards draws no home grid. It takes the
 * first free cell, and whatever finds none is collected into a [FROM_DOCK_FOLDER_LABEL] folder.
 */
fun HomeLayoutSet.withLegacyDocksUnified(): HomeLayoutSet =
    layouts.keys
        .map { key -> key.deviceClass }
        .distinct()
        .fold(this) { set, deviceClass -> set.withLegacyDocksUnified(deviceClass) }

private fun HomeLayoutSet.withLegacyDocksUnified(deviceClass: HomeLayoutDeviceClass): HomeLayoutSet {
    val sourceKey = dockSourceKey(deviceClass, activeKey, layouts, preferredModesByDeviceClass) ?: return this
    val sharedDock = layouts.getValue(sourceKey).dock.onSharedEdge()
    val otherKeys =
        layouts.keys
            .filter { key -> key.deviceClass == deviceClass && key != sourceKey }
            .sortedBy { key -> key.viewMode.ordinal }
    val rescuedLayouts =
        otherKeys.fold(layouts) { current, otherKey ->
            val targetKey = if (otherKey.viewMode == LauncherViewMode.CARD_INTERFACE) sourceKey else otherKey
            val target = current.getValue(targetKey).withSharedDock(sharedDock)
            current + (targetKey to target.withRescued(layouts.getValue(otherKey).dock.pinnedItems()))
        }
    return copy(layouts = rescuedLayouts, docks = docks + (deviceClass to sharedDock))
}

private fun DockModel.onSharedEdge(): DockModel =
    if (position == null || position in sharedDockPositions) this else copy(position = DockPosition.BOTTOM)

private fun DockModel.pinnedItems(): List<LauncherItem> = items + panel?.items.orEmpty()

/** [candidates] that this layout does not already hold anywhere, placed on its pages. */
private fun HomeLayout.withRescued(candidates: List<LauncherItem>): HomeLayout =
    candidates.fold(this) { layout, candidate ->
        layout.missing(candidate)?.let { missing -> layout.placingRescued(missing) } ?: layout
    }

/**
 * [item] reduced to what this layout lacks: `null` when it already holds all of it, and a folder
 * trimmed to the apps it is missing.
 */
private fun HomeLayout.missing(item: LauncherItem): LauncherItem? {
    val held = heldIdentities()
    return when (item) {
        is FolderItem ->
            item.items
                .filterNot { app -> app.identity in held }
                .takeIf { apps -> apps.isNotEmpty() }
                ?.let { apps -> item.copy(items = apps) }

        else -> item.takeUnless { item.identities().all { identity -> identity in held } }
    }
}

private fun HomeLayout.heldIdentities(): Set<Any> =
    (pages.flatMap { page -> page.items } + dock.pinnedItems())
        .flatMap { item -> item.identities() }
        .toSet()

private data class PinnedApp(
    val appIdentity: AppIdentity,
    val appShortcutId: AppShortcutId?,
)

private val AppShortcutItem.identity: Any
    get() = PinnedApp(appIdentity = appIdentity, appShortcutId = appShortcutId)

private fun LauncherItem.identities(): List<Any> =
    when (this) {
        is AppShortcutItem -> listOf(identity)
        is FolderItem -> items.map { app -> app.identity }
        is WidgetItem -> listOf(appWidgetId)
    }

/**
 * Puts [item] in the first free cell of this layout's home pages; failing that, an app joins the
 * [FROM_DOCK_FOLDER_LABEL] folder (created in the first free cell, or on a new page when there is
 * none), and anything else goes on a new page.
 */
private fun HomeLayout.placingRescued(item: LauncherItem): HomeLayout {
    val unique = item.withIdsUniqueIn(this)
    // Once one pin has overflowed into the folder, the rest follow it rather than scattering over
    // whatever page the folder itself had to open.
    if (unique is AppShortcutItem && fromDockFolder() != null) return inFromDockFolder(unique)
    return placedOnExistingPage(unique)
        ?: (unique as? AppShortcutItem)?.let(::inFromDockFolder)
        ?: (unique as? FolderItem)?.let { folder ->
            folder.items.fold(this) { layout, app -> layout.inFromDockFolder(app) }
        }
        ?: placedOnNewPage(unique)
        ?: this
}

private fun HomeLayout.inFromDockFolder(app: AppShortcutItem): HomeLayout {
    val existing = fromDockFolder()
    if (existing != null) {
        val grown = existing.copy(items = existing.items + app)
        return copy(
            pages =
                pages.map { page ->
                    page.copy(items = page.items.map { item -> if (item.id == existing.id) grown else item })
                },
        )
    }
    val folder = FolderItem(id = FROM_DOCK_FOLDER_ID, label = FROM_DOCK_FOLDER_LABEL, items = listOf(app))
    return placedOnExistingPage(folder) ?: placedOnNewPage(folder) ?: this
}

private fun HomeLayout.fromDockFolder(): FolderItem? =
    pages.firstNotNullOfOrNull { page ->
        page.items.filterIsInstance<FolderItem>().firstOrNull { folder -> folder.id == FROM_DOCK_FOLDER_ID }
    }

private fun HomeLayout.placedOnExistingPage(item: LauncherItem): HomeLayout? =
    pages.indices
        .filter { index -> pages[index].type !is LauncherPageType.Generated }
        .firstNotNullOfOrNull { index ->
            pages[index].placing(item)?.let { placed ->
                copy(pages = pages.toMutableList().apply { set(index, placed) })
            }
        }

private fun HomeLayout.placedOnNewPage(item: LauncherItem): HomeLayout? {
    val ids = pages.map { page -> page.id.value }.toSet()
    val id =
        generateSequence(0) { it + 1 }
            .map { index -> if (index == 0) FROM_DOCK_PAGE_ID else "$FROM_DOCK_PAGE_ID-$index" }
            .first { candidate -> candidate !in ids }
    return LauncherPage(id = LauncherPageId(id), grid = workspaceGrid)
        .placing(item)
        ?.let { page -> copy(pages = pages + page) }
}

private fun LauncherPage.placing(item: LauncherItem): LauncherPage? =
    item.spanCandidatesOn(grid).firstNotNullOfOrNull { span ->
        (
            GridPlacementEngine().placeItemInFirstAvailableCell(page = this, item = item, span = span)
                as? PlaceLauncherItemResult.Placed
        )?.page
    }

private fun LauncherItem.spanCandidatesOn(grid: GridDimensions): List<GridSpan> =
    when (this) {
        is WidgetItem ->
            (placement?.span ?: GridSpan())
                .placementCandidates(resizeConstraints)
                .filter { span -> span.columns <= grid.columns && span.rows <= grid.rows }

        is AppShortcutItem, is FolderItem -> listOf(GridSpan())
    }

/**
 * [this] with any id the layout already uses replaced, so a rescued pin can never collide with the
 * home item or dock entry that the per-mode copies were made from.
 */
private fun LauncherItem.withIdsUniqueIn(layout: HomeLayout): LauncherItem {
    val used =
        (layout.pages.flatMap { page -> page.items } + layout.dock.pinnedItems())
            .flatMap { item -> listOf(item.id) + (item as? FolderItem)?.items?.map { app -> app.id }.orEmpty() }
            .map { id -> id.value }
            .toSet()
    return when (this) {
        is AppShortcutItem -> copy(id = id.uniqueIn(used))
        is WidgetItem -> copy(id = id.uniqueIn(used))
        is FolderItem ->
            copy(id = id.uniqueIn(used), items = items.map { app -> app.copy(id = app.id.uniqueIn(used)) })
    }
}

private fun LauncherItemId.uniqueIn(used: Set<String>): LauncherItemId =
    if (value !in used) {
        this
    } else {
        generateSequence(1) { it + 1 }
            .map { index -> "$value-from-dock-$index" }
            .first { candidate -> candidate !in used }
            .let(::LauncherItemId)
    }

private val FROM_DOCK_FOLDER_ID = LauncherItemId("from-dock-folder")
private const val FROM_DOCK_PAGE_ID = "from-dock"
