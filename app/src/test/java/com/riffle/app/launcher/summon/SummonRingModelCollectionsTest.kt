package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.summon.SummonCollection
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonSlotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SummonRingModelCollectionsTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private val work = SummonCollectionId("work")
    private val tools = SummonCollectionId("tools")
    private val loop = SummonCollectionId("loop")

    private fun model(collections: List<SummonCollection>) =
        SummonRingModel.from(
            settings =
                SummonSettings(
                    slots = listOf(SummonSlot.App(app("maps")), SummonSlot.Collection(work), SummonSlot.Favourites),
                    collections = collections,
                ),
            layout =
                HomeLayout(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    pages = listOf(LauncherPage(LauncherPageId("p"), grid = GridDimensions(4, 5))),
                    selectedPageId = LauncherPageId("p"),
                    dock = DockModel(capacity = 4),
                ),
            installed = listOf("maps", "mail", "files", "terminal").map { InstalledApp(app(it), it) },
            glanceLabel = { "Next event" },
            favouritesLabel = "Favourites",
        )

    @Test
    fun aCollectionOpensToItsItemsAndNestedCollectionsOpenFurther() {
        val ring =
            model(
                listOf(
                    SummonCollection(
                        work,
                        "Work",
                        listOf(
                            SummonSlot.App(app("mail")),
                            SummonSlot.Collection(tools),
                            SummonSlot.Glance(GlanceWidgetKind.NEXT_EVENT),
                        ),
                    ),
                    SummonCollection(
                        tools,
                        "Tools",
                        listOf(SummonSlot.App(app("files")), SummonSlot.App(app("terminal"))),
                    ),
                ),
            )
        assertEquals(listOf("maps", "Work", "Favourites"), ring.level(emptyList()).map { it.label })
        assertEquals(SummonSlotRole.FOLDER, ring.roles(emptyList())[1])
        assertEquals(listOf("mail", "Tools", "Next event"), ring.level(listOf(1)).map { it.label })
        assertEquals(SummonSlotRole.FOLDER, ring.roles(listOf(1))[1])
        assertEquals(listOf("files", "terminal"), ring.level(listOf(1, 1)).map { it.label })
        assertEquals(emptyList<SummonEntry>(), ring.level(listOf(1, 0)))
    }

    @Test
    fun aCollectionThatReachesItselfStopsInsteadOfLoopingForever() {
        val ring =
            model(
                listOf(
                    SummonCollection(work, "Work", listOf(SummonSlot.Collection(loop))),
                    SummonCollection(loop, "Loop", listOf(SummonSlot.Collection(work), SummonSlot.App(app("mail")))),
                ),
            )
        assertEquals(listOf("mail"), ring.level(listOf(1, 0)).map { it.label })
    }
}
