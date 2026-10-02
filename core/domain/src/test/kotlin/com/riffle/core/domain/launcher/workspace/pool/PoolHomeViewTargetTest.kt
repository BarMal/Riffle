package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The edit target is exactly what the read-only view draws, so an edit changes the page the user sees. */
class PoolHomeViewTargetTest {
    private val pool =
        PoolCutover.ensureMigrated(null, cutoverLayoutSet()).state.pools.getValue(HomeLayoutDeviceClass.PHONE)
    private val library = WorkspaceId("ws:phone:home_screen_library")
    private val preset = WorkspaceId("preset:nova:compact")
    private val candidates =
        PoolHomeView.candidates(preset, HomeLayoutDeviceClass.PHONE, LauncherViewMode.HOME_SCREEN_LIBRARY)

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

    @Test
    fun aPresetTargetsTheMigratedArrangementItShows() {
        assertEquals(library, PoolHomeView.arrangementOf(pool, candidates))
        val target = PoolHomeView.resolveTarget(pool, homePage("second"), candidates)
        assertEquals(PoolHomeTarget(library, LauncherPageId("second")), target)
    }

    @Test
    fun resolveDrawsTheTargetPage() {
        val target = PoolHomeView.resolveTarget(pool, homePage("home"), candidates)!!
        val drawn = PoolHomeView.resolve(pool, homePage("home"), candidates)!!
        assertEquals(target.pageId, drawn.id)
    }

    @Test
    fun noArrangementMeansNoTarget() {
        assertNull(PoolHomeView.arrangementOf(PlacedItemPool(), candidates))
        assertNull(PoolHomeView.resolveTarget(PlacedItemPool(), homePage("home"), candidates))
    }

    @Test
    fun anEditAtTheTargetChangesTheDrawnPage() {
        val target = PoolHomeView.resolveTarget(pool, homePage("home"), candidates)!!
        val item =
            pool.arrangements.getValue(target.workspaceId).pages.first { it.id == target.pageId }
                .placements.first().item
        val edited = PoolRemoval.remove(pool, target.workspaceId, item).done().pool
        val before = PoolHomeView.resolve(pool, homePage("home"), candidates)!!.items.size
        val after = PoolHomeView.resolve(edited, homePage("home"), candidates)!!.items.size
        assertEquals(before - 1, after)
    }
}
