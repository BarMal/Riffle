package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LibraryViewRetirement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stored pages of the retired types still decode, and each becomes what the retirement says it becomes (ADR 0003,
 * Amendment 10): All apps and the app-backed generated pages become placed pages with the items they held; Today and
 * Cards are dropped; an empty generated page is dropped.
 */
class LibraryViewRetirementCodecTest {
    private val grid = GridDimensions(4, 2)

    private fun item(name: String) =
        AppShortcutItem(
            id = LauncherItemId("generated:$name"),
            appIdentity = AppIdentity(AppPackageName("com.example.$name"), AppActivityName(".Main")),
            label = name,
            placement = GridPlacement(GridCell(0, 0)),
        )

    private fun page(
        id: String,
        type: LauncherPageType,
        vararg names: String,
    ) = LauncherPage(LauncherPageId(id), type, grid, names.map(::item))

    private fun roundTrip(vararg pages: LauncherPage): HomeLayoutSet {
        val layout =
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages = pages.toList(),
                selectedPageId = pages.first().id,
                dock = DockModel(capacity = 5),
            )
        return decodeHomeLayoutSet(encodeHomeLayoutSet(HomeLayoutSet.fromLayout(layout)))
    }

    private fun retired(set: HomeLayoutSet) =
        LibraryViewRetirement.withStandardKeys(LibraryViewRetirement.withPlacedPages(set))

    @Test
    fun everyGeneratedKindDecodesAndKeepsItsKind() {
        val kinds = GeneratedLauncherPageKind.entries
        val set =
            roundTrip(
                page("home", LauncherPageType.Home),
                *kinds.map { kind -> page(kind.name, LauncherPageType.Generated(kind), kind.name) }.toTypedArray(),
            )

        assertEquals(
            kinds.map { LauncherPageType.Generated(it) },
            set.activeLayout.pages.drop(1).map { it.type },
        )
    }

    @Test
    fun eachStoredPageTypeBecomesWhatTheAmendmentSays() {
        val kept =
            listOf(
                GeneratedLauncherPageKind.APP,
                GeneratedLauncherPageKind.CATEGORY,
                GeneratedLauncherPageKind.WORK,
                GeneratedLauncherPageKind.PERSONAL,
                GeneratedLauncherPageKind.FAVOURITES,
                GeneratedLauncherPageKind.FREQUENTLY_USED,
            )
        val set =
            roundTrip(
                page("home", LauncherPageType.Home, "h"),
                page("library:1", LauncherPageType.AllApps, "all"),
                *kept.map { page(it.name, LauncherPageType.Generated(it), it.name.lowercase()) }.toTypedArray(),
                page("today", LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY), "recent"),
                page("cards", LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS), "group"),
                page("empty", LauncherPageType.Generated(GeneratedLauncherPageKind.WORK)),
            )

        val layout = retired(set).activeLayout

        assertEquals(listOf("home", "library:1") + kept.map { it.name }, layout.pages.map { it.id.value })
        assertEquals(setOf(LauncherPageType.Home), layout.pages.map { it.type }.toSet())
        assertEquals(
            listOf("h", "all") + kept.map { it.name.lowercase() },
            layout.pages.flatMap { it.items }.map { (it as AppShortcutItem).label },
        )
        assertEquals(retired(set), retired(retired(set)))
    }

    @Test
    fun aPageStoredWithoutATypeButALibraryIdIsStillAllApps() {
        val set = roundTrip(page("home", LauncherPageType.Home, "h"), page("library:7", LauncherPageType.AllApps, "x"))
        assertEquals(LauncherPageType.AllApps, set.activeLayout.pages.last().type)
        assertEquals(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER), set.activeKey)
    }
}
