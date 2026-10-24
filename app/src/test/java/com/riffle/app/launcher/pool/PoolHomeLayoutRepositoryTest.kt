package com.riffle.app.launcher.pool

import com.riffle.app.launcher.pool.PoolHomeFixture.homeItems
import com.riffle.app.launcher.pool.PoolHomeFixture.legacyId
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayout
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
    private fun start(): Pair<PoolHomeLayoutRepository, WriteThroughHomeLayouts> {
        val pool = CachedPoolRepository(pools)
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
}
