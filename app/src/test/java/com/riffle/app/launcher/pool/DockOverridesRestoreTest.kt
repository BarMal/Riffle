package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dock size a workspace held before the cut-over comes back once, from the stored workspaces. */
class DockOverridesRestoreTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private class Flag(var done: Boolean = false) : DockOverridesRestoredFlag {
        override fun dockOverridesRestored() = done

        override fun markDockOverridesRestored() {
            done = true
        }
    }

    private fun workspaces(sizeDp: Int): WorkspaceSet {
        val stored = WorkspaceMigration.defaultFor(phone)
        val active = stored.active.copy(dock = WorkspaceDock(presentation = DockPresentation(iconSizeDp = sizeDp)))
        return WorkspaceSet(mapOf(phone to stored.copy(workspaces = listOf(active))))
    }

    private fun openedPool(pools: DiskPoolStore): CachedPoolRepository {
        val pool = CachedPoolRepository(pools)
        runBlocking { openHomeLayouts(DiskHomeLayoutStore(stored = PoolHomeFixture.legacySet()), pool) }
        return pool
    }

    private fun CachedPoolRepository.dockIconSize() = checkNotNull(pool(phone)?.dock).settings.iconSizeDp

    @Test
    fun theStoredWorkspaceSizeIsRestoredPersistedAndRemembered() {
        val pools = DiskPoolStore()
        val pool = openedPool(pools)
        val flag = Flag()

        runBlocking { restoreDockOverridesOnce(pool, flag) { workspaces(56) } }

        assertEquals(56, pool.dockIconSize())
        assertEquals(56, checkNotNull(pools.stored?.pools?.getValue(phone)?.dock).settings.iconSizeDp)
        assertTrue(flag.done)
    }

    @Test
    fun aSizeSetAfterTheRepairHasRunIsNeverTouchedAgain() {
        val pool = openedPool(DiskPoolStore())
        val flag = Flag(done = true)

        runBlocking { restoreDockOverridesOnce(pool, flag) { workspaces(56) } }

        assertFalse(pool.dockIconSize() == 56)
    }

    @Test
    fun anUnreadableWorkspaceStoreLeavesTheDockAndTriesAgainNextStart() {
        val pool = openedPool(DiskPoolStore())
        val before = pool.dockIconSize()
        val flag = Flag()

        runBlocking { restoreDockOverridesOnce(pool, flag) { error("unreadable") } }

        assertEquals(before, pool.dockIconSize())
        assertFalse(flag.done)
    }
}
