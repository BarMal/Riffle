package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonSlotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SummonRingModelTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private fun installed(vararg names: String) =
        names.map {
            InstalledApp(
                app(it),
                it.replaceFirstChar(Char::uppercase),
            )
        }

    private fun shortcut(name: String) = AppShortcutItem(LauncherItemId("item-$name"), app(name), name)

    private val layout =
        HomeLayout(
            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            pages =
                listOf(
                    LauncherPage(
                        LauncherPageId("p0"),
                        grid = GridDimensions(4, 5),
                        items =
                            listOf(
                                FolderItem(
                                    LauncherItemId("work"),
                                    "Work",
                                    listOf(shortcut("files"), shortcut("mail"), shortcut("gone")),
                                ),
                            ),
                    ),
                ),
            selectedPageId = LauncherPageId("p0"),
            dock = DockModel(capacity = 4, items = listOf(shortcut("phone"), shortcut("gone"), shortcut("music"))),
        )

    private val apps = installed("maps", "files", "mail", "phone", "music", "camera")

    private fun model(settings: SummonSettings) =
        SummonRingModel.from(settings, layout, apps, glanceLabel = { it.storedId }, favouritesLabel = "Favourites")

    @Test
    fun slotsResolveToWhatIsInstalledAndOnHomeSkippingWhatIsGone() {
        val settings =
            SummonSettings(
                slots =
                    listOf(
                        SummonSlot.App(app("maps")),
                        SummonSlot.App(app("uninstalled")),
                        SummonSlot.Folder(LauncherItemId("work")),
                        SummonSlot.Folder(LauncherItemId("deleted-folder")),
                        SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING),
                        SummonSlot.Favourites,
                    ),
            )
        val root = model(settings).level(emptyList())
        assertEquals(listOf("Maps", "Work", "now-playing", "Favourites"), root.map { it.label })
        assertEquals(
            listOf(SummonSlotRole.APP, SummonSlotRole.FOLDER, SummonSlotRole.APP, SummonSlotRole.FAVOURITES),
            model(settings).roles(emptyList()),
        )
    }

    @Test
    fun aFolderOpensToItsInstalledAppsAndFavouritesAreTheDocksInstalledApps() {
        val settings = SummonSettings(slots = listOf(SummonSlot.Folder(LauncherItemId("work")), SummonSlot.Favourites))
        val ring = model(settings)
        assertEquals(listOf("Files", "Mail"), ring.level(listOf(0)).map { it.label })
        val favourites = ring.level(emptyList())[1] as SummonEntry.Favourites
        assertEquals(listOf("Phone", "Music"), favourites.apps.map { it.label })
    }

    @Test
    fun onlyTheRingsCountOfSlotsIsShownAndAFolderFitsTheGridLessItsOwnPlace() {
        val many = (1..8).map { SummonSlot.App(app("a$it")) }
        val ring =
            SummonRingModel.from(
                SummonSettings(slotCount = 4, slots = many),
                layout,
                (1..8).flatMap { installed("a$it") },
                glanceLabel = { it.storedId },
                favouritesLabel = "Favourites",
            )
        assertEquals(4, ring.level(emptyList()).size)

        val bigFolder =
            FolderItem(LauncherItemId("big"), "Big", (1..9).map { shortcut("a$it") })
        val bigLayout = layout.copy(pages = listOf(layout.pages.first().copy(items = listOf(bigFolder))))
        val folderRing =
            SummonRingModel.from(
                SummonSettings(slotCount = 4, slots = listOf(SummonSlot.Folder(LauncherItemId("big")))),
                bigLayout,
                (1..9).flatMap { installed("a$it") },
                glanceLabel = { it.storedId },
                favouritesLabel = "Favourites",
            )
        assertEquals(3, folderRing.level(listOf(0)).size)
    }

    @Test
    fun pathsThatDoNotLeadToAFolderAreEmpty() {
        val ring = model(SummonSettings(slots = listOf(SummonSlot.App(app("maps")))))
        assertEquals(emptyList<SummonEntry>(), ring.level(listOf(0)))
        assertEquals(emptyList<SummonEntry>(), ring.level(listOf(5)))
    }
}
