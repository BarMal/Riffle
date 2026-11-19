package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The retirement of the library view mode on the stored set alone (ADR 0003, Amendment 10): which device classes it
 * converts, what becomes of every retired page type, and that it is idempotent. The pool side is in
 * `LibraryViewRetirementPoolTest`.
 */
class LibraryViewRetirementTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE
    private val landscape = HomeLayoutDeviceClass.PHONE_LANDSCAPE
    private val grid = GridDimensions(4, 2)

    private fun key(
        mode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass = phone,
    ) = HomeLayoutKey(mode, deviceClass)

    private fun app(
        name: String,
        index: Int = 0,
    ) = AppShortcutItem(
        id = LauncherItemId("generated:$name"),
        appIdentity = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("$name.Main")),
        label = name,
        placement = GridPlacement(GridCell(index % grid.columns, index / grid.columns)),
    )

    private fun page(
        id: String,
        type: LauncherPageType,
        vararg names: String,
        overflow: Int = 0,
        pinned: Boolean = false,
    ) = LauncherPage(
        id = LauncherPageId(id),
        type = type,
        grid = grid,
        items = names.mapIndexed { index, name -> app(name, index) },
        generatedContentOverflowCount = overflow,
        isPinned = pinned,
    )

    private fun layout(
        mode: LauncherViewMode,
        vararg pages: LauncherPage,
        selected: String = pages.first().id.value,
    ) = HomeLayout(
        viewMode = mode,
        pages = pages.toList(),
        selectedPageId = LauncherPageId(selected),
        dock = DockModel(capacity = 5),
    )

    private fun generated(kind: GeneratedLauncherPageKind) = LauncherPageType.Generated(kind)

    private fun HomeLayout.names() = pages.map { page -> page.items.map { (it as AppShortcutItem).label } }

    private fun retire(
        set: HomeLayoutSet,
        derived: Set<HomeLayoutDeviceClass> = emptySet(),
    ): HomeLayoutSet =
        LibraryViewRetirement.withStandardKeys(LibraryViewRetirement.withPlacedPages(set, derived), derived)

    @Test
    fun aLibraryLayoutMovesToTheStandardKeyWithItsPagesAndItsSelection() {
        val library =
            layout(
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                page("home", LauncherPageType.Home, "a", "b"),
                page("library:1", LauncherPageType.AllApps, "c"),
                selected = "library:1",
            )
        val set =
            HomeLayoutSet(
                activeKey = key(LauncherViewMode.HOME_SCREEN_LIBRARY),
                layouts =
                    mapOf(
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY) to library,
                    ),
            )

        val retired = retire(set)

        assertEquals(key(LauncherViewMode.STANDARD_APP_DRAWER), retired.activeKey)
        assertEquals(setOf(key(LauncherViewMode.STANDARD_APP_DRAWER)), retired.layouts.keys)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), retired.activeLayout.names())
        assertEquals(listOf(LauncherPageType.Home, LauncherPageType.Home), retired.activeLayout.pages.map { it.type })
        assertEquals(LauncherPageId("library:1"), retired.activeLayout.selectedPageId)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, retired.activeLayout.viewMode)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, retired.preferredModesByDeviceClass.getValue(phone))
        assertFalse(LibraryViewRetirement.isPending(retired))
        assertEquals(retired, retire(retired))
    }

    @Test
    fun theStandardLayoutItReplacesIsNotMergedIn() {
        val library = layout(LauncherViewMode.HOME_SCREEN_LIBRARY, page("home", LauncherPageType.Home, "a"))
        val standard = layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "x", "y"))
        val set =
            HomeLayoutSet(
                activeKey = key(LauncherViewMode.HOME_SCREEN_LIBRARY),
                layouts =
                    mapOf(
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY) to library,
                        key(LauncherViewMode.STANDARD_APP_DRAWER) to standard,
                    ),
            )

        val retired = retire(set)

        assertEquals(listOf(listOf("a")), retired.activeLayout.names())
    }

    @Test
    fun aDormantLibraryLayoutOfADeviceClassThatShowsStandardIsLeftAlone() {
        val library = layout(LauncherViewMode.HOME_SCREEN_LIBRARY, page("home", LauncherPageType.Home, "old"))
        val standard = layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "mine"))
        val set =
            HomeLayoutSet(
                activeKey = key(LauncherViewMode.STANDARD_APP_DRAWER),
                layouts =
                    mapOf(
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY) to library,
                        key(LauncherViewMode.STANDARD_APP_DRAWER) to standard,
                    ),
            )

        assertFalse(LibraryViewRetirement.isPending(set))
        assertEquals(set, retire(set))
        assertEquals(listOf(listOf("mine")), set.activeLayout.names())
    }

    @Test
    fun everyDeviceClassIsConvertedByItsOwnMode() {
        val phoneLibrary = layout(LauncherViewMode.HOME_SCREEN_LIBRARY, page("home", LauncherPageType.Home, "p"))
        val foldableLibrary =
            layout(
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                page("home", LauncherPageType.Home, "f"),
                page("library:1", LauncherPageType.AllApps, "g"),
            )
        val tabletStandard = layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "t"))
        val set =
            HomeLayoutSet(
                activeKey = key(LauncherViewMode.STANDARD_APP_DRAWER),
                layouts =
                    mapOf(
                        key(LauncherViewMode.STANDARD_APP_DRAWER) to
                            layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "s")),
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY, foldable) to foldableLibrary,
                        key(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.TABLET) to tabletStandard,
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY, landscape) to phoneLibrary,
                    ),
                preferredModesByDeviceClass =
                    mapOf(
                        phone to LauncherViewMode.STANDARD_APP_DRAWER,
                        foldable to LauncherViewMode.HOME_SCREEN_LIBRARY,
                        HomeLayoutDeviceClass.TABLET to LauncherViewMode.STANDARD_APP_DRAWER,
                        HomeLayoutDeviceClass.PHONE_LANDSCAPE to LauncherViewMode.HOME_SCREEN_LIBRARY,
                    ),
            )

        val retired = retire(set)

        assertEquals(listOf(listOf("s")), retired.layoutFor(key(LauncherViewMode.STANDARD_APP_DRAWER)).names())
        assertEquals(
            listOf(listOf("f"), listOf("g")),
            retired.layoutFor(key(LauncherViewMode.STANDARD_APP_DRAWER, foldable)).names(),
        )
        assertEquals(
            listOf(listOf("t")),
            retired.layoutFor(key(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.TABLET)).names(),
        )
        assertEquals(
            listOf(listOf("p")),
            retired.layoutFor(key(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE_LANDSCAPE)).names(),
        )
        assertTrue(retired.layouts.keys.none { it.viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY })
        assertEquals(retired, retire(retired))
    }

    @Test
    fun aWorkspaceThatBringsTheLibraryModeBackConvertsTheLibraryLayoutOfAStandardStoredClass() {
        val library = layout(LauncherViewMode.HOME_SCREEN_LIBRARY, page("home", LauncherPageType.Home, "kept"))
        val standard = layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "unused"))
        val set =
            HomeLayoutSet(
                activeKey = key(LauncherViewMode.STANDARD_APP_DRAWER),
                layouts =
                    mapOf(
                        key(LauncherViewMode.HOME_SCREEN_LIBRARY) to library,
                        key(LauncherViewMode.STANDARD_APP_DRAWER) to standard,
                    ),
            )
        val derived = setOf(phone)

        assertTrue(LibraryViewRetirement.isPending(set, derived))
        val retired = retire(set, derived)

        assertEquals(listOf(listOf("kept")), retired.activeLayout.names())
        assertEquals(retired, retire(retired, derived))
        assertFalse(LibraryViewRetirement.isPending(retired, derived))
    }

    @Test
    fun aWorkspaceThatBringsTheLibraryModeBackWithNoLibraryLayoutKeepsTheStandardLayout() {
        val standard = layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "mine"))
        val set = HomeLayoutSet.fromLayout(standard)
        val derived = setOf(phone)

        assertFalse(LibraryViewRetirement.isPending(set, derived))
        assertEquals(set, retire(set, derived))
    }

    @Test
    fun allAppsAndTheContentGeneratedPagesBecomePlacedPagesWithTheirCurrentContent() {
        val kinds =
            listOf(
                GeneratedLauncherPageKind.APP,
                GeneratedLauncherPageKind.CATEGORY,
                GeneratedLauncherPageKind.WORK,
                GeneratedLauncherPageKind.PERSONAL,
                GeneratedLauncherPageKind.FAVOURITES,
                GeneratedLauncherPageKind.FREQUENTLY_USED,
            )
        val pages =
            listOf(page("home", LauncherPageType.Home, "h"), page("all", LauncherPageType.AllApps, "a1", "a2")) +
                kinds.map {
                        kind ->
                    page("g-${kind.name}", generated(kind), "${kind.name}-1", overflow = 3, pinned = true)
                }
        val set = HomeLayoutSet.fromLayout(layout(LauncherViewMode.STANDARD_APP_DRAWER, *pages.toTypedArray()))

        val retired = retire(set)

        val converted = retired.activeLayout
        assertEquals(pages.map { it.id }, converted.pages.map { it.id })
        assertTrue(converted.pages.all { it.type == LauncherPageType.Home })
        assertEquals(pages.map { page -> page.items }, converted.pages.map { it.items })
        assertTrue(converted.pages.all { it.generatedContentOverflowCount == 0 && !it.isPinned })
        assertEquals(retired, retire(retired))
    }

    @Test
    fun anEmptyGeneratedPageIsDroppedAndTheSelectionMovesToAPageThatIsLeft() {
        val set =
            HomeLayoutSet.fromLayout(
                layout(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    page("home", LauncherPageType.Home, "h"),
                    page("work", generated(GeneratedLauncherPageKind.WORK)),
                    selected = "work",
                ),
            )

        val retired = retire(set).activeLayout

        assertEquals(listOf(LauncherPageId("home")), retired.pages.map { it.id })
        assertEquals(LauncherPageId("home"), retired.selectedPageId)
    }

    @Test
    fun todayAndCardsPagesAreDroppedBecauseTheStreamShowsThatNow() {
        val set =
            HomeLayoutSet.fromLayout(
                layout(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    page("home", LauncherPageType.Home, "h"),
                    page("today", generated(GeneratedLauncherPageKind.TODAY), "recent-1", "recent-2"),
                    page("cards", generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS), "group-1"),
                ),
            )

        val retired = retire(set).activeLayout

        assertEquals(listOf(LauncherPageId("home")), retired.pages.map { it.id })
    }

    @Test
    fun aLayoutWhosePagesAreAllRetiredAndEmptyKeepsOnePlacedPage() {
        val set =
            HomeLayoutSet.fromLayout(
                layout(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    page("today", generated(GeneratedLauncherPageKind.TODAY), "recent-1"),
                ),
            )

        val retired = retire(set).activeLayout

        assertEquals(1, retired.pages.size)
        assertEquals(LauncherPageType.Home, retired.pages.single().type)
        assertTrue(retired.pages.single().items.isEmpty())
        assertNotNull(retired.pages.firstOrNull { it.id == retired.selectedPageId })
    }

    @Test
    fun aLayoutThatIsAlreadyOneHomeIsReturnedUnchanged() {
        val set =
            HomeLayoutSet.fromLayout(
                layout(LauncherViewMode.STANDARD_APP_DRAWER, page("home", LauncherPageType.Home, "a")),
            )

        assertFalse(LibraryViewRetirement.isPending(set))
        assertEquals(set, retire(set))
        assertNull(set.layouts[key(LauncherViewMode.HOME_SCREEN_LIBRARY)])
    }
}
