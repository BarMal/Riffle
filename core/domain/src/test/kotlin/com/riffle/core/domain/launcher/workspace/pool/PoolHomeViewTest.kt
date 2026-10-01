package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolHomeViewTest {
    private val pool =
        PoolCutover.ensureMigrated(
            null,
            cutoverLayoutSet(),
        ).state.pools.getValue(HomeLayoutDeviceClass.PHONE)
    private val libraryId = WorkspaceId("ws:phone:home_screen_library")
    private val presetId = WorkspaceId("preset:nova:compact")

    private fun homePage(key: String?) =
        PageContainer(
            ContainerId("home"),
            PageContent.Bound(
                LensBinding(
                    Lens(
                        sources = listOf(WorkspaceSourceIds.HOME_GRID),
                        filter = key?.let(LensFilter::GroupKeyIs) ?: LensFilter.All,
                    ),
                    ExpressionKind.ICON_GRID,
                ),
            ),
        )

    private fun candidates(active: WorkspaceId) =
        PoolHomeView.candidates(active, HomeLayoutDeviceClass.PHONE, LauncherViewMode.HOME_SCREEN_LIBRARY)

    @Test
    fun aPresetHomePageFallsBackToTheMigratedLibraryArrangement() {
        val page = PoolHomeView.resolve(pool, homePage("home"), candidates(presetId))!!
        assertEquals(LauncherPageId("home"), page.id)
        val items = page.items
        assertEquals(setOf("com.a", "com.b"), items.filterIsInstance<AppShortcutItem>().map { it.label }.toSet())
        assertEquals(listOf("Social"), items.filterIsInstance<FolderItem>().map { it.label })
        assertEquals(listOf("Clock"), items.filterIsInstance<WidgetItem>().map { it.label })
    }

    @Test
    fun theNamedPageIsChosenOverTheFirst() {
        assertEquals(
            LauncherPageId("second"),
            PoolHomeView.resolve(pool, homePage("second"), candidates(presetId))!!.id,
        )
    }

    @Test
    fun anUnknownOrAbsentKeyUsesTheFirstPage() {
        assertEquals(LauncherPageId("home"), PoolHomeView.resolve(pool, homePage("zzz"), candidates(presetId))!!.id)
        assertEquals(LauncherPageId("home"), PoolHomeView.resolve(pool, homePage(null), candidates(presetId))!!.id)
    }

    @Test
    fun theActiveWorkspacesOwnArrangementWinsWhenItHasOne() {
        val own = pool.arrangements.getValue(libraryId)
        val mine = pool.copy(arrangements = pool.arrangements + (presetId to own.copy(pages = own.pages.takeLast(1))))
        assertEquals(LauncherPageId("second"), PoolHomeView.resolve(mine, homePage(null), candidates(presetId))!!.id)
    }

    @Test
    fun anEmptyPoolResolvesToNothing() {
        assertNull(PoolHomeView.resolve(PlacedItemPool(), homePage("home"), candidates(presetId)))
    }

    @Test
    fun candidatesAreDistinctAndShownModeFirstAfterTheActiveWorkspace() {
        val list = PoolHomeView.candidates(libraryId, HomeLayoutDeviceClass.PHONE, LauncherViewMode.CARD_INTERFACE)
        assertEquals(list.size, list.toSet().size)
        assertEquals(libraryId, list.first())
        assertEquals(WorkspaceId("ws:phone:card_interface"), list[1])
    }

    @Test
    fun onlyASingleBoundHomeGridPageIsADrawablePlacedPage() {
        assertTrue(PoolHomeView.isPlacedHomePage(homePage("home")))
        val other =
            PageContainer(
                ContainerId("apps"),
                PageContent.Bound(LensBinding(Lens(sources = listOf(SourceIds.ALL_APPS)), ExpressionKind.ICON_GRID)),
            )
        assertFalse(PoolHomeView.isPlacedHomePage(other))
        val set =
            PageSetContainer(
                ContainerId("set"),
                LensBinding(Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID)), ExpressionKind.CARD_STACK),
            )
        assertFalse(PoolHomeView.isPlacedHomePage(set))
        assertEquals("home", PoolHomeView.pageKey(homePage("home")))
        assertNull(PoolHomeView.pageKey(homePage(null)))
    }
}
