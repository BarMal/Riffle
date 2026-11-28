package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.summon.SummonCollection
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.homeFolderCollectionId
import org.junit.Assert.assertEquals
import org.junit.Test

class SummonSettingsChoicesTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

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
                                FolderItem(LauncherItemId("work"), "Work", emptyList()),
                                WidgetItem(LauncherItemId("w1"), HostedWidgetId(7), "Weather"),
                            ),
                    ),
                ),
            selectedPageId = LauncherPageId("p0"),
            dock = DockModel(capacity = 4),
        )

    private val installed =
        listOf(InstalledApp(app("zed"), "Zed"), InstalledApp(app("alpha"), "alpha"), InstalledApp(app("maps"), "Maps"))

    @Test
    fun whatCanBeAddedIsEverythingNotAlreadyOnTheRingWithAppsAToZ() {
        val settings =
            SummonSettings(slots = listOf(SummonSlot.App(app("maps")), SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING)))
        val choices = summonAddChoices(settings, layout, installed)
        assertEquals(listOf(SummonSlot.Favourites, SummonSlot.Folder(LauncherItemId("work"))), choices.places)
        assertEquals(
            listOf(SummonSlot.Glance(GlanceWidgetKind.NEXT_EVENT), SummonSlot.Glance(GlanceWidgetKind.TOP_STORY)),
            choices.glances,
        )
        assertEquals(listOf("alpha", "Zed"), choices.apps.map { it.label })
    }

    @Test
    fun collectionsAreOfferedAndAHomeFolderAlreadyCopiedOntoTheRingIsNot() {
        val mine = SummonCollectionId("mine")
        val settings =
            SummonSettings(
                slots = listOf(SummonSlot.Collection(homeFolderCollectionId(LauncherItemId("work")))),
                collections =
                    listOf(
                        SummonCollection(homeFolderCollectionId(LauncherItemId("work")), "Work"),
                        SummonCollection(mine, "Mine"),
                    ),
            )
        assertEquals(
            listOf(SummonSlot.Favourites, SummonSlot.Collection(mine)),
            summonAddChoices(settings, layout, installed).places,
        )
    }

    @Test
    fun aCollectionOffersAppsGlancesAndOtherCollectionsItDoesNotHoldOrSitInside() {
        val outer = SummonCollectionId("outer")
        val inner = SummonCollectionId("inner")
        val spare = SummonCollectionId("spare")
        val settings =
            SummonSettings(
                collections =
                    listOf(
                        SummonCollection(outer, "Outer", listOf(SummonSlot.Collection(inner))),
                        SummonCollection(inner, "Inner", listOf(SummonSlot.App(app("maps")))),
                        SummonCollection(spare, "Spare"),
                    ),
            )
        val choices = summonCollectionAddChoices(settings, inner, installed)
        assertEquals(listOf<SummonSlot>(SummonSlot.Collection(spare)), choices.places)
        assertEquals(GlanceWidgetKind.entries.map(SummonSlot::Glance), choices.glances)
        assertEquals(listOf("alpha", "Zed"), choices.apps.map { it.label })
    }

    @Test
    fun theHeaderCanBeAClockAGlanceOrAnyWidgetOnHome() {
        val headers = summonHeaderChoices(layout)
        assertEquals(
            listOf(
                SummonHeader.ClockAndDate,
                SummonHeader.Clock,
                SummonHeader.Glance(GlanceWidgetKind.NOW_PLAYING),
                SummonHeader.Glance(GlanceWidgetKind.NEXT_EVENT),
                SummonHeader.Glance(GlanceWidgetKind.TOP_STORY),
                SummonHeader.HomeWidget(LauncherItemId("w1")),
                SummonHeader.None,
            ),
            headers.map { it.header },
        )
        assertEquals("Weather", headers.single { it.header is SummonHeader.HomeWidget }.widgetLabel)
    }
}
