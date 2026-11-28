package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
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
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SummonCollectionsTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private val work = SummonCollectionId("work")
    private val tools = SummonCollectionId("tools")

    private val settings =
        SummonSettings()
            .withNewCollection(work, "Work")
            .withNewCollection(tools, "Tools")

    @Test
    fun aNewCollectionIsEmptyAndCanBeRenamed() {
        assertEquals(SummonCollection(work, "Work"), settings.collection(work))
        assertEquals("Office", settings.renamedCollection(work, "Office").collection(work)?.name)
        assertEquals(settings, settings.withNewCollection(work, "Again"))
    }

    @Test
    fun itemsAreAddedOnceMovedAndRemoved() {
        val filled =
            settings
                .withCollectionItem(work, SummonSlot.App(app("mail")))
                .withCollectionItem(work, SummonSlot.Glance(GlanceWidgetKind.NEXT_EVENT))
                .withCollectionItem(work, SummonSlot.App(app("mail")))
                .withCollectionItem(work, SummonSlot.Collection(tools))
        assertEquals(
            listOf(
                SummonSlot.App(app("mail")),
                SummonSlot.Glance(GlanceWidgetKind.NEXT_EVENT),
                SummonSlot.Collection(tools),
            ),
            filled.collection(work)?.items,
        )
        val moved = filled.movedCollectionItem(work, from = 2, to = 0)
        assertEquals(SummonSlot.Collection(tools), moved.collection(work)?.items?.first())
        assertEquals(2, moved.withoutCollectionItem(work, 0).collection(work)?.items?.size)
    }

    @Test
    fun collectionsNestToAnyDepthButNeverInsideThemselves() {
        val deep = SummonCollectionId("deep")
        val nested =
            settings
                .withNewCollection(deep, "Deep")
                .withCollectionItem(work, SummonSlot.Collection(tools))
                .withCollectionItem(tools, SummonSlot.Collection(deep))
        assertEquals(listOf(SummonSlot.Collection(deep)), nested.collection(tools)?.items)
        // deep → work would make work contain itself through tools.
        assertEquals(nested, nested.withCollectionItem(deep, SummonSlot.Collection(work)))
        assertEquals(nested, nested.withCollectionItem(work, SummonSlot.Collection(work)))
    }

    @Test
    fun aCollectionsIconIsAnIconPackDrawableUntilItIsCleared() {
        val icon = IconPackDrawableRef("pack.icons", "folder_work")
        val set = settings.withCollectionIcon(work, icon)
        assertEquals(icon, set.collection(work)?.icon)
        assertNull(set.withCollectionIcon(work, null).collection(work)?.icon)
    }

    @Test
    fun favouritesStaysOnTheRingAndNeverGoesIntoACollection() {
        assertEquals(settings, settings.withCollectionItem(work, SummonSlot.Favourites))
    }

    @Test
    fun deletingACollectionTakesItOffTheRingAndOutOfOtherCollections() {
        val placed =
            settings
                .copy(slots = listOf(SummonSlot.Collection(tools), SummonSlot.Favourites))
                .withCollectionItem(work, SummonSlot.Collection(tools))
        val gone = placed.withoutCollection(tools)
        assertNull(gone.collection(tools))
        assertEquals(listOf<SummonSlot>(SummonSlot.Favourites), gone.slots)
        assertEquals(emptyList(), gone.collection(work)?.items)
    }

    @Test
    fun homeFoldersOnTheRingAreCopiedIntoCollectionsOnce() {
        val folder =
            FolderItem(
                LauncherItemId("f1"),
                "Social",
                listOf(
                    AppShortcutItem(LauncherItemId("a"), app("chat"), "Chat"),
                    AppShortcutItem(LauncherItemId("b"), app("chat"), "Chat", appShortcutId = AppShortcutId("x")),
                    AppShortcutItem(LauncherItemId("c"), app("photos"), "Photos"),
                ),
            )
        val layout =
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages = listOf(LauncherPage(LauncherPageId("p"), grid = GridDimensions(4, 5), items = listOf(folder))),
                selectedPageId = LauncherPageId("p"),
                dock = DockModel(capacity = 4),
            )
        val withFolders =
            SummonSettings(
                slots =
                    listOf(
                        SummonSlot.Folder(LauncherItemId("f1")),
                        SummonSlot.Folder(LauncherItemId("gone")),
                        SummonSlot.Favourites,
                    ),
            )
        val copied = withFolders.withHomeFoldersCopied(layout)
        val id = homeFolderCollectionId(LauncherItemId("f1"))
        assertEquals(listOf(SummonSlot.Collection(id), SummonSlot.Favourites), copied.slots)
        assertEquals(
            SummonCollection(id, "Social", listOf(SummonSlot.App(app("chat")), SummonSlot.App(app("photos")))),
            copied.collection(id),
        )
        assertEquals(copied, copied.withHomeFoldersCopied(layout))
    }
}
