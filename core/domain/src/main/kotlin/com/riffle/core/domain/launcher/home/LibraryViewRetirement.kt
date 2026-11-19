package com.riffle.core.domain.launcher.home

/**
 * The library view mode is retired (ADR 0003, Amendment 10): there is one Home, a user-arranged grid of placed items,
 * and nothing re-fills, re-sorts or compacts it. What an older build stored is converted once, without changing what
 * the user sees:
 *
 * - A layout whose view mode is [LauncherViewMode.HOME_SCREEN_LIBRARY] (the stored mode, or the mode its workspace
 *   brought back) becomes the layout of [LauncherViewMode.STANDARD_APP_DRAWER] of the same device class. Its content as
 *   it is now (the apps the old auto-layout had filled in, in the same order and on the same pages) becomes ordinary
 *   placed items. Settings and the page order stay as they are. The Standard layout it replaces is kept by the
 *   caller's recovery copy and, for its placed items, by its own workspace arrangement in the pool.
 * - A page of the retired types becomes an ordinary placed page when it holds the user's content: "All apps" (what
 *   the library filled) and the generated App, Category, Work, Personal, Favourites and Frequent pages, each with the
 *   items it holds now; an empty one is dropped. The generated Today and Cards pages are dropped: the Stream shows
 *   recents and notifications now, and they hold nothing the user arranged (Today lists the same apps as App).
 *
 * Two steps, because the pool must see the page change before the key moves: [withPlacedPages] retypes the pages of
 * the layout each device class shows now (an ordinary commit puts the new pages' items into the pool), then
 * [withStandardKeys] moves a library layout to the Standard key (a projection only, nothing is ingested). Both are
 * pure, never throw and are idempotent: a set they have already converted is returned unchanged.
 */
object LibraryViewRetirement {
    /**
     * The device classes that show the library layout: those whose current mode is the library mode in [set], and
     * those in [derived] (the mode their active workspace brings back, see `WorkspaceHome.legacyHomeModeFor`).
     */
    fun libraryClasses(
        set: HomeLayoutSet,
        derived: Set<HomeLayoutDeviceClass> = emptySet(),
    ): Set<HomeLayoutDeviceClass> =
        classesOf(set)
            .filter { deviceClass ->
                deviceClass in derived || currentKey(set, deviceClass).viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY
            }.toSet()

    /** Whether [set] still holds anything this retirement converts (cheap: nothing is built). */
    fun isPending(
        set: HomeLayoutSet,
        derived: Set<HomeLayoutDeviceClass> = emptySet(),
    ): Boolean {
        val library = libraryClasses(set, derived)
        return library.any { set.holdsLibraryKey(it) } ||
            classesOf(set).any { deviceClass ->
                val key = sourceKey(set, deviceClass, deviceClass in library)
                set.layouts[key]?.pages.orEmpty().any { page -> page.type != LauncherPageType.Home }
            }
    }

    /** Step 1: the pages of the layout each device class shows now, retyped as described above. */
    fun withPlacedPages(
        set: HomeLayoutSet,
        derived: Set<HomeLayoutDeviceClass> = emptySet(),
    ): HomeLayoutSet {
        val library = libraryClasses(set, derived)
        return classesOf(set).fold(set) { current, deviceClass ->
            val key = sourceKey(current, deviceClass, deviceClass in library)
            val layout = current.layoutFor(key)
            val pages = layout.pagesAsPlaced()
            if (pages == layout.pages) {
                current
            } else {
                current.withLayout(key, layout.copy(pages = pages, selectedPageId = layout.selectedAmong(pages)))
            }
        }
    }

    /** Step 2: a library layout becomes the Standard layout of its device class; the library key is removed. */
    fun withStandardKeys(
        set: HomeLayoutSet,
        derived: Set<HomeLayoutDeviceClass> = emptySet(),
    ): HomeLayoutSet =
        libraryClasses(set, derived).fold(set) { current, deviceClass ->
            if (!current.holdsLibraryKey(deviceClass)) {
                current
            } else {
                val to = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, deviceClass)
                val layout = current.layoutFor(sourceKey(current, deviceClass, library = true))
                val standard =
                    layout.copy(
                        viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                        settings = layout.settings.copy(grid = layout.settings.grid.copy(compactLibraryPages = false)),
                    )
                val layouts = current.layouts - libraryKey(deviceClass) + (to to standard)
                current.copy(
                    activeKey = if (current.activeKey.deviceClass == deviceClass) to else current.activeKey,
                    layouts = layouts,
                    preferredModesByDeviceClass = current.preferredModesByDeviceClass + (deviceClass to to.viewMode),
                    modePairsByDeviceClass = current.modePairsShowing(deviceClass, to.viewMode),
                )
            }
        }

    private fun HomeLayoutSet.holdsLibraryKey(deviceClass: HomeLayoutDeviceClass): Boolean =
        libraryKey(deviceClass) in layouts ||
            activeKey == libraryKey(deviceClass) ||
            preferredModesByDeviceClass[deviceClass] == LauncherViewMode.HOME_SCREEN_LIBRARY

    private fun classesOf(set: HomeLayoutSet): List<HomeLayoutDeviceClass> =
        (
            set.layouts.keys.map { it.deviceClass } +
                set.activeKey.deviceClass +
                set.preferredModesByDeviceClass.keys
        ).distinct()

    private fun libraryKey(deviceClass: HomeLayoutDeviceClass) =
        HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, deviceClass)

    private fun currentKey(
        set: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
    ): HomeLayoutKey =
        if (set.activeKey.deviceClass == deviceClass) {
            set.activeKey
        } else {
            HomeLayoutKey(
                set.preferredModesByDeviceClass[deviceClass] ?: LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass,
            )
        }

    /** The key whose layout [deviceClass] shows: the library layout when it has one, else the current key. */
    private fun sourceKey(
        set: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
        library: Boolean,
    ): HomeLayoutKey =
        if (library && libraryKey(deviceClass) in set.layouts) libraryKey(deviceClass) else currentKey(set, deviceClass)

    private fun HomeLayout.selectedAmong(pages: List<LauncherPage>): LauncherPageId =
        pages.firstOrNull { it.id == selectedPageId }?.id ?: pages.first().id

    /**
     * [pages] with every retired type turned into a placed page (or dropped), the others as they are; at least one
     * placed page is always left.
     */
    private fun HomeLayout.pagesAsPlaced(): List<LauncherPage> {
        val converted = pages.mapNotNull { page -> page.asPlaced() }
        return converted.ifEmpty { listOf(LauncherPage(id = pages.first().id, grid = pages.first().grid)) }
    }

    private fun LauncherPage.asPlaced(): LauncherPage? =
        when (val pageType = type) {
            LauncherPageType.Home -> this
            LauncherPageType.AllApps -> placedCopy()
            is LauncherPageType.Generated ->
                when (pageType.kind) {
                    GeneratedLauncherPageKind.TODAY,
                    GeneratedLauncherPageKind.NOTIFICATION_CARDS,
                    -> null

                    else -> placedCopy().takeIf { items.isNotEmpty() }
                }
        }

    private fun LauncherPage.placedCopy(): LauncherPage =
        copy(type = LauncherPageType.Home, generatedContentOverflowCount = 0, isPinned = false)
}
