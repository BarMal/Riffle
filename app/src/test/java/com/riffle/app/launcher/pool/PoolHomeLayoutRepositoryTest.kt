package com.riffle.app.launcher.pool

import com.riffle.app.launcher.pool.PoolHomeFixture.homeItems
import com.riffle.app.launcher.pool.PoolHomeFixture.legacyId
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PoolArrangements
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import com.riffle.core.domain.launcher.workspace.pool.PoolRemoval
import com.riffle.core.domain.launcher.workspace.pool.PoolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolHomeLayoutRepositoryTest {
    private val layouts = DiskHomeLayoutStore(stored = PoolHomeFixture.legacySet())
    private val pools = DiskPoolStore()

    /** One process: opens the stores as `HomeLayoutRepositories` does, returns the shell's repository. */
    private lateinit var currentPool: CachedPoolRepository

    private fun start(): Pair<PoolHomeLayoutRepository, WriteThroughHomeLayouts> {
        val pool = CachedPoolRepository(pools).also { currentPool = it }
        val opening = runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        assertTrue(opening.poolHome)
        val legacy = WriteThroughHomeLayouts(layouts)
        return poolHomeRepository(legacy, pool).also { it.open(opening.stored) } to legacy
    }

    private fun PoolHomeLayoutRepository.shown(): HomeLayoutSet = loadHomeLayoutSet()!!

    private fun PoolHomeLayoutRepository.edit(change: (HomeLayout) -> HomeLayout): HomeLayoutSet =
        commitHomeLayoutSet(shown().withActiveLayout(change(shown().activeLayout)))

    private fun HomeLayout.mapHome(change: (LauncherPage) -> LauncherPage): HomeLayout =
        copy(pages = pages.map { if (it.id == PoolHomeFixture.home) change(it) else it })

    @Test
    fun anEditIsKeptInMemoryLandsInThePoolAndSurvivesARestart() {
        val (repository, legacy) = start()
        val savesBefore = legacy.saved.size
        val moved =
            repository.edit { layout ->
                layout.mapHome { page ->
                    page.copy(
                        items =
                            page.items.map {
                                if (it.id.value.endsWith("mail")) it.withPlacement(PoolHomeFixture.at(3, 4)) else it
                            },
                    )
                }
            }
        assertEquals(moved, repository.shown())
        assertEquals("HomeLayoutSet is not written for a moved item", savesBefore, legacy.saved.size)
        runBlocking { repository.flushNow() }

        val (restarted, _) = start()
        assertEquals(moved.activeLayout.homeItems(), restarted.shown().activeLayout.homeItems())
        assertEquals(
            PoolHomeFixture.at(3, 4),
            restarted.shown().activeLayout.homeItems().first {
                it.id.value.endsWith("mail")
            }.placement,
        )
    }

    @Test
    fun withoutAFlushTheRestartShowsTheLastWrittenPool() {
        val (repository, _) = start()
        repository.edit { layout -> layout.mapHome { it.copy(items = emptyList()) } }
        val (restarted, _) = start()
        assertEquals(3, restarted.shown().activeLayout.homeItems().size)
    }

    @Test
    fun aRemovedWidgetsHostIdIsReleasedAfterTheWriteAndOnlyWhenNothingShowsIt() {
        val (repository, _) = start()
        val released = mutableListOf<Set<HostedWidgetId>>()
        repository.hostIdReleaser = { released += it }
        repository.edit {
                layout ->
            layout.mapHome { page -> page.copy(items = page.items.filterNot { it is WidgetItem }) }
        }
        assertFalse(repository.isHostIdReferenced(HostedWidgetId(42)))
        runBlocking { repository.flushNow() }
        assertEquals(listOf(setOf(HostedWidgetId(42))), released)
    }

    @Test
    fun aWidgetAnotherWorkspaceShowsIsNeverReleased() {
        start()
        // The preview forked the home into a workspace of its own: the same widget instance, two arrangements.
        val stored = pools.stored!!
        val forked =
            PoolArrangements.fork(
                stored.pools.getValue(HomeLayoutDeviceClass.PHONE),
                WorkspaceId("mine"),
                listOf(PoolHomeSync.workspaceOf(PoolHomeFixture.key)),
                WorkspaceIdFactory { "f" },
            ) as PoolResult.Done
        pools.stored = stored.copy(pools = stored.pools + (HomeLayoutDeviceClass.PHONE to forked.edit.pool))
        val (repository, _) = start()
        val released = mutableListOf<Set<HostedWidgetId>>()
        repository.hostIdReleaser = { released += it }
        repository.edit {
                layout ->
            layout.mapHome { page -> page.copy(items = page.items.filterNot { it is WidgetItem }) }
        }
        assertTrue(repository.isHostIdReferenced(HostedWidgetId(42)))
        runBlocking { repository.flushNow() }
        assertEquals(emptyList<Set<HostedWidgetId>>(), released)
    }

    @Test
    fun aConfirmedRemovalPrunesTheHomeAndTheDock() {
        val (repository, _) = start()
        val pruned =
            repository.withoutRemovedPackage(
                repository.shown(),
                PoolHomeFixture.identity("phone").packageName,
                AppProfile.personal(),
            )
        assertTrue(pruned.activeLayout.dock.items.isEmpty())
        val mail =
            repository.withoutRemovedPackage(
                pruned,
                PoolHomeFixture.identity("mail").packageName,
                AppProfile.personal(),
            )
        assertEquals(listOf("maps", "clock"), mail.activeLayout.homeItems().map { it.id.value.legacyId() })
    }

    @Test
    fun aPreviewEditOfTheSamePoolIsSeenAndProjectedAgain() {
        val pool = CachedPoolRepository(pools)
        val opening = runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(opening.stored) }
        val phone = pool.pool(HomeLayoutDeviceClass.PHONE)!!
        val ws = PoolHomeSync.workspaceOf(PoolHomeFixture.key)
        val emptied =
            phone.copy(
                arrangements =
                    phone.arrangements + (
                        ws to
                            phone.arrangements.getValue(ws).let {
                                    a ->
                                a.copy(pages = a.pages.map { it.copy(placements = emptyList()) })
                            }
                    ),
            )
        runBlocking {
            val change = async { withTimeout(5_000) { repository.externalChanges.first() } }
            yield()
            pool.update(HomeLayoutDeviceClass.PHONE, emptied)
            change.await()
        }
        assertTrue(repository.reproject(repository.shown()).activeLayout.homeItems().isEmpty())
    }

    @Test
    fun anOlderBackupIsImportedIntoThePool() {
        val (repository, _) = start()
        val older =
            PoolHomeFixture.legacySet().let { set ->
                set.withActiveLayout(
                    set.activeLayout.mapHome { it.copy(items = listOf(PoolHomeFixture.app("old", 2))) },
                )
            }
        val restored = repository.restoreFromBackup(older, backupPool = null, homeFromPool = false)
        assertEquals(listOf("old"), restored.activeLayout.homeItems().map { it.id.value.legacyId() })
        runBlocking { repository.flushNow() }
        assertEquals(listOf("old"), start().first.shown().activeLayout.homeItems().map { it.id.value.legacyId() })
    }

    @Test
    fun aNewWidgetKeepsItsEngineId() {
        val cell = PoolHomeFixture.at(2, 1, 2, 2)
        val notes = WidgetItem(LauncherItemId("widget-9"), HostedWidgetId(90), "Notes", placement = cell)
        val (repository, _) = start()
        val added =
            repository.edit { layout ->
                layout.mapHome { page ->
                    page.copy(
                        items = page.items + notes,
                    )
                }
            }
        assertTrue(added.activeLayout.homeItems().any { it.id.value == "widget-9" })
        assertTrue(repository.isHostIdReferenced(HostedWidgetId(90)))
    }

    @Test
    fun theFirstEditAfterAFreshInstallIsKeptAcrossARestart() {
        layouts.stored = null
        val pool = CachedPoolRepository(pools).also { currentPool = it }
        runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        // Nothing shown yet: the shell starts from its own default set.
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(null) }
        assertEquals(null, repository.loadHomeLayoutSet()?.activeLayout?.homeItems()?.takeIf { it.isNotEmpty() })
        val default = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
        val widget =
            WidgetItem(LauncherItemId("widget-1"), HostedWidgetId(71), "Notes", placement = PoolHomeFixture.at(0, 0))
        val added =
            repository.commitHomeLayoutSet(
                default.withActiveLayout(
                    default.activeLayout.let { l ->
                        l.copy(pages = l.pages.map { it.copy(items = listOf(PoolHomeFixture.app("first", 1), widget)) })
                    },
                ),
            )
        assertEquals(listOf("first", "widget-1"), added.activeLayout.homeItems().map { it.id.value })
        assertTrue("the widget's host id is held, not leaked", repository.isHostIdReferenced(HostedWidgetId(71)))
        runBlocking { repository.flushNow() }
        assertEquals(listOf("first", "widget-1"), start().first.shown().activeLayout.homeItems().map { it.id.value })
    }

    @Test
    fun theRecoverySnapshotIsRefreshedAfterAPoolWrite() {
        val pool = CachedPoolRepository(pools)
        val opening = runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        val repository =
            poolHomeRepository(
                WriteThroughHomeLayouts(layouts),
                pool,
                snapshots = layouts,
            ).also { it.open(opening.stored) }
        repository.edit {
                layout ->
            layout.mapHome { page -> page.copy(items = page.items + PoolHomeFixture.app("new", 3)) }
        }
        assertEquals("not before the pool write", 3, layouts.snapshot!!.activeLayout.homeItems().size)
        runBlocking { repository.flushNow() }
        assertEquals(repository.shown(), layouts.snapshot)
        assertEquals(4, layouts.snapshot!!.activeLayout.homeItems().size)
    }

    @Test
    fun aHomeCommitOnAStaleProjectionNeverRevertsAPreviewRemoval() {
        val pool = CachedPoolRepository(pools)
        val opening = runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        val repository = poolHomeRepository(WriteThroughHomeLayouts(layouts), pool).also { it.open(opening.stored) }
        val stale = repository.shown()
        // The preview's editor removes "maps" from the same arrangement; the shell has not reprojected yet.
        val phone = pool.pool(HomeLayoutDeviceClass.PHONE)!!
        val ws = PoolHomeSync.workspaceOf(PoolHomeFixture.key)
        val mapsId = phone.items.keys.first { it.value.endsWith("maps") }
        pool.update(HomeLayoutDeviceClass.PHONE, (PoolRemoval.remove(phone, ws, mapsId) as PoolResult.Done).edit.pool)
        val moved =
            repository.commitHomeLayoutSet(
                stale.withActiveLayout(
                    stale.activeLayout.mapHome { page ->
                        page.copy(
                            items =
                                page.items.map {
                                    if (it.id.value.endsWith("mail")) it.withPlacement(PoolHomeFixture.at(3, 4)) else it
                                },
                        )
                    },
                ),
            )
        assertEquals(listOf("mail", "clock"), moved.activeLayout.homeItems().map { it.id.value.legacyId() })
    }

    @Test
    fun aRestoreReleasesTheHostIdsOfWidgetsItDropped() {
        val (repository, _) = start()
        val released = mutableListOf<Set<HostedWidgetId>>()
        repository.hostIdReleaser = { released += it }
        val older =
            PoolHomeFixture.legacySet().let { set ->
                set.withActiveLayout(
                    set.activeLayout.mapHome { it.copy(items = listOf(PoolHomeFixture.app("old", 2))) },
                )
            }
        repository.restoreFromBackup(older, backupPool = null, homeFromPool = false)
        runBlocking { repository.flushNow() }
        assertEquals(listOf(setOf(HostedWidgetId(42))), released)
    }

    @Test
    fun releasedHostIdsGoAfterAnyoneElsesWriteToo() {
        val (repository, _) = start()
        val released = mutableListOf<Set<HostedWidgetId>>()
        repository.hostIdReleaser = { released += it }
        repository.edit {
                layout ->
            layout.mapHome { page -> page.copy(items = page.items.filterNot { it is WidgetItem }) }
        }
        assertEquals(emptyList<Set<HostedWidgetId>>(), released)
        // Another writer of the same repository (the preview's editor) flushes first.
        runBlocking { currentPool.flush() }
        assertEquals(listOf(setOf(HostedWidgetId(42))), released)
    }

    @Test
    fun theSnapshotCatchesUpWithAnotherWritersPoolWrite() {
        val pool = CachedPoolRepository(pools)
        val opening = runBlocking { openHomeLayouts(layouts, pool, enabled = true) }
        poolHomeRepository(WriteThroughHomeLayouts(layouts), pool, snapshots = layouts).also { it.open(opening.stored) }
        // The preview's editor removes "maps" and flushes; the home never commits.
        val phone = pool.pool(HomeLayoutDeviceClass.PHONE)!!
        val ws = PoolHomeSync.workspaceOf(PoolHomeFixture.key)
        val mapsId = phone.items.keys.first { it.value.endsWith("maps") }
        pool.update(HomeLayoutDeviceClass.PHONE, (PoolRemoval.remove(phone, ws, mapsId) as PoolResult.Done).edit.pool)
        runBlocking { pool.flush() }
        val deadline = System.currentTimeMillis() + 5_000
        while (layouts.snapshot!!.activeLayout.homeItems().size != 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(
            listOf("mail", "clock"),
            layouts.snapshot!!.activeLayout.homeItems().map { it.id.value.legacyId() },
        )
    }
}
