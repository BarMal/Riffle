package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspaceStorePort
import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.exclusions.ExclusionStorePort
import com.riffle.app.launcher.homeLayoutDeviceClassFromConfiguration
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.pool.PoolStorePort
import com.riffle.app.launcher.workspaceSettingsLayoutDeviceTabs
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner decision N10: rotating a phone changes geometry only. The window's device class flips between `PHONE` and
 * `PHONE_LANDSCAPE`, yet every Workspaces read (workspace set, hidden items, placed items) resolves the same value.
 */
class WorkspaceRotationTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val landscape = HomeLayoutDeviceClass.PHONE_LANDSCAPE
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val portraitWindow = checkNotNull(homeLayoutDeviceClassFromConfiguration(411, 891))
    private val rotatedWindow = checkNotNull(homeLayoutDeviceClassFromConfiguration(891, 411))

    private fun workspace(id: String) =
        Workspace(
            WorkspaceId(id),
            id,
            listOf(
                PageContainer(
                    ContainerId("$id-page"),
                    PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST)),
                ),
            ),
        )

    private class WorkspaceStore(var stored: WorkspaceSet?) : WorkspaceStorePort {
        override suspend fun read(): WorkspaceSet? = stored

        override suspend fun write(set: WorkspaceSet) {
            stored = set
        }
    }

    private class PoolStore(var stored: PoolStoreState?) : PoolStorePort {
        val writes = mutableListOf<PoolStoreState>()

        override suspend fun read(): PoolStoreState? = stored

        override suspend fun write(state: PoolStoreState) {
            writes += state
            stored = state
        }
    }

    private class ExclusionStore(var stored: LayoutExclusionRules?) : ExclusionStorePort {
        override suspend fun read(): LayoutExclusionRules? = stored

        override suspend fun write(rules: LayoutExclusionRules) {
            stored = rules
        }
    }

    private fun pool(vararg workspaces: String) =
        PlacedItemPool(
            arrangements =
                workspaces.associate {
                    WorkspaceId(it) to Arrangement(listOf(ArrangementPage(LauncherPageId("p"), GridDimensions(4, 4))))
                },
        )

    @Test
    fun theWindowFlipsDeviceClassButNotTheWorkspacesLayout() {
        assertEquals(phone, portraitWindow)
        assertEquals(landscape, rotatedWindow)
        assertEquals(portraitWindow.workspaceLayoutClass(), rotatedWindow.workspaceLayoutClass())
    }

    @Test
    fun aRotatedWindowResolvesTheSameWorkspaceSet() =
        runBlocking {
            val mine = LayoutWorkspaces(listOf(workspace("a"), workspace("b")), WorkspaceId("b"), WorkspaceId("a"))
            // Stored before N10: the phone and a separate landscape layout.
            val stored = WorkspaceSet(mapOf(phone to mine, landscape to LayoutWorkspaces.single(workspace("l"))))
            val repository = CachedWorkspaceRepository(WorkspaceStore(stored), scope)

            repository.initialize(WorkspaceBootstrap::seed)
            val set = checkNotNull(repository.load())

            assertFalse(landscape in set.layouts)
            assertEquals(set.workspacesFor(portraitWindow), set.workspacesFor(rotatedWindow))
            assertEquals(set.resolveActive(portraitWindow), set.resolveActive(rotatedWindow))
            assertEquals(WorkspaceId("b"), set.resolveActive(rotatedWindow).workspace.id)
        }

    @Test
    fun aLandscapeOnlyStoreSeedsNoLandscapeLayoutAndKeepsItsWorkspaces() =
        runBlocking {
            val stored = WorkspaceSet(mapOf(landscape to LayoutWorkspaces.single(workspace("l"))))
            val repository = CachedWorkspaceRepository(WorkspaceStore(stored), scope)

            repository.initialize(WorkspaceBootstrap::seed)
            val set = checkNotNull(repository.load())

            assertFalse(landscape in set.layouts)
            assertEquals(WorkspaceId("l"), set.resolveActive(portraitWindow).workspace.id)
        }

    @Test
    fun aRotatedWindowShowsTheSamePlacedItemsAndEditsThePhonePool() =
        runBlocking {
            val store = PoolStore(PoolStoreState(migrated = true, pools = mapOf(landscape to pool("w"))))
            val repository = CachedPoolRepository(store)

            repository.initialize(HomeLayoutSet.standard())

            assertEquals(pool("w"), repository.pool(portraitWindow))
            assertEquals(repository.pool(portraitWindow), repository.pool(rotatedWindow))
            // The fold is written once so the next start reads the phone pool directly.
            assertEquals(setOf(phone), store.writes.single().pools.keys)

            assertTrue(repository.update(rotatedWindow, pool("w", "v")))
            assertEquals(pool("w", "v"), repository.pool(portraitWindow))
            assertEquals(setOf(phone), repository.current()?.pools?.keys)
        }

    @Test
    fun aRestoredOlderPoolBackupIsFolded() =
        runBlocking {
            val repository = CachedPoolRepository(PoolStore(PoolStoreState(migrated = true)))
            repository.initialize(HomeLayoutSet.standard())

            repository.replaceFromBackup(
                PoolStoreState(migrated = true, pools = mapOf(landscape to pool("l"), phone to pool("p"))),
            )

            assertEquals(mapOf(phone to pool("p")), repository.current()?.pools)
        }

    @Test
    fun aHiddenAppStaysHiddenAfterRotation() =
        runBlocking {
            val rule = SourceExclusionRule(ExclusionRuleId("r"), SourceIds.ALL_APPS, ExclusionMatcher.App("chat"))
            val stored = LayoutExclusionRules(mapOf(phone to ExclusionRuleSet(listOf(rule))), legacyMigrated = true)
            val repository = CachedExclusionRepository(ExclusionStore(stored), scope)

            repository.initialize(emptySet(), emptyList())

            assertEquals(listOf(rule), repository.rules(rotatedWindow).rules)
            assertEquals(repository.rules(portraitWindow), repository.rules(rotatedWindow))
        }

    @Test
    fun theWorkspacesSettingsTabsOfferNoSeparateLandscapeLayout() {
        val tabs = workspaceSettingsLayoutDeviceTabs(setOf(phone, landscape, HomeLayoutDeviceClass.TABLET))
        assertFalse(tabs.any { it.deviceClass == landscape })
        assertNotNull(tabs.firstOrNull { it.deviceClass == phone })
        assertFalse(workspaceSettingsLayoutDeviceTabs(setOf(landscape)).any { it.deviceClass == landscape })
    }
}
