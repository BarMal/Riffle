package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.layouts.AppLayout
import com.riffle.core.domain.launcher.workspace.pool.FolderEntry
import com.riffle.core.domain.launcher.workspace.pool.LegacyAppPair
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolDock
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Dock's former app pairs become layouts the first time the pool is read (ADR 0003, Amendment 7). */
class CachedPoolRepositoryPairMigrationTest {
    private fun identity(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private fun app(pkg: String) = PoolApp(PoolItemId("app:$pkg"), identity(pkg), pkg)

    private fun pair(
        id: String,
        first: String,
        second: String,
    ) = LegacyAppPair(
        PoolItemId(id),
        FolderEntry("e-$first", identity(first), first),
        FolderEntry("e-$second", identity(second), second),
    )

    private val withPairs =
        PoolStoreState(
            migrated = true,
            pools =
                mapOf(
                    HomeLayoutDeviceClass.PHONE to
                        PlacedItemPool(
                            items = listOf(app("mail")).associateBy { it.id },
                            dock = PoolDock(pins = listOf(PoolItemId("app:mail"))),
                            legacyPairs = listOf(pair("p1", "notes", "maps"), pair("p2", "maps", "chat")),
                        ),
                ),
        )

    private fun layoutSet() = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    private val received = mutableListOf<List<AppLayout>>()

    private fun repository(store: DiskPoolStore) = CachedPoolRepository(store, legacyPairs = { received += it })

    @Test
    fun pairsAmongAppsBecomeLayoutsInDockOrderAndLeaveTheDockAndTheStore() =
        runBlocking {
            val store = DiskPoolStore(stored = withPairs)

            val repository = repository(store)
            repository.initialize(layoutSet())

            assertEquals(
                listOf("notes" to "maps", "maps" to "chat"),
                received.single().map { it.first.packageName.value to it.second.packageName.value },
            )
            val pool = repository.pool(HomeLayoutDeviceClass.PHONE)!!
            assertEquals(listOf("app:mail"), pool.dock?.pins?.map { it.value })
            assertTrue(store.stored!!.pools.values.all { it.legacyPairs.isEmpty() })
        }

    @Test
    fun aSecondStartMigratesNothingAndWritesNoPair() =
        runBlocking {
            val store = DiskPoolStore(stored = withPairs)
            val set = layoutSet()
            repository(store).initialize(set)
            received.clear()

            repository(store).initialize(set)

            assertTrue(received.isEmpty())
        }

    @Test
    fun aSinkThatFailsKeepsThePairsForTheNextStart() =
        runBlocking {
            val store = DiskPoolStore(stored = withPairs)
            val set = layoutSet()

            CachedPoolRepository(store, legacyPairs = { error("full") }).initialize(set)

            assertTrue(store.stored!!.pools.values.any { it.legacyPairs.isNotEmpty() })
        }

    @Test
    fun aRestoredBackupWithPairsIsMigratedToo() =
        runBlocking {
            val store = DiskPoolStore()
            val repository = repository(store)
            repository.initialize(layoutSet())
            received.clear()

            repository.replaceFromBackup(withPairs)

            assertEquals(2, received.single().size)
            assertFalse(
                repository.pool(HomeLayoutDeviceClass.PHONE)!!.legacyPairs.isNotEmpty(),
            )
        }
}
