package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPoolRepositoryTest {
    private class FakeStore(
        var stored: PoolStoreState? = null,
        val failRead: Boolean = false,
        val failWrite: Boolean = false,
    ) : PoolStorePort {
        val writes = mutableListOf<PoolStoreState>()

        override suspend fun read(): PoolStoreState? {
            if (failRead) error("disk")
            return stored
        }

        override suspend fun write(state: PoolStoreState) {
            if (failWrite) error("full")
            writes += state
            stored = state
        }
    }

    private fun identity(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private fun layoutSet(vararg packages: String): HomeLayoutSet {
        val items =
            packages.mapIndexed { index, pkg ->
                AppShortcutItem(LauncherItemId("app:$pkg"), identity(pkg), pkg, placement = placement(index, 0))
            } +
                WidgetItem(LauncherItemId("widget:42"), HostedWidgetId(42), "Clock", placement = placement(0, 1))
        val page = LauncherPage(LauncherPageId("home"), LauncherPageType.Home, GridDimensions(4, 6), items)
        val layout = HomeLayout(LauncherViewMode.HOME_SCREEN_LIBRARY, listOf(page), page.id, DockModel(capacity = 5))
        val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
        return HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout))
    }

    private fun placement(
        column: Int,
        row: Int,
    ) = GridPlacement(GridCell(column, row), GridSpan(1, 1))

    private val provider = WidgetProviderIdentity(AppPackageName("w.pkg"), WidgetProviderClassName("w.Clock"))
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun poolIsNullUntilInitializedAndNothingIsReadOrWritten() {
        val store = FakeStore()
        val repository = CachedPoolRepository(store)
        assertNull(repository.pool(phone))
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun firstInitializeMigratesPersistsAndPublishes() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedPoolRepository(store)
            repository.initialize(layoutSet("com.a", "com.b"))
            val pool = repository.pool(phone)
            assertNotNull(pool)
            assertEquals(
                setOf("com.a", "com.b"),
                pool!!.items.values.filterIsInstance<PoolApp>().map { it.label }.toSet(),
            )
            assertEquals(1, store.writes.size)
            assertTrue(store.stored!!.migrated)
            assertEquals(1, repository.version.value)
        }

    @Test
    fun aSecondProcessFindsTheFlagAndDoesNotMigrateOrWriteAgain() =
        runBlocking {
            val store = FakeStore()
            CachedPoolRepository(store).initialize(layoutSet("com.a"))
            val writesAfterFirst = store.writes.size
            val second = CachedPoolRepository(store)
            second.initialize(layoutSet("com.a", "com.new"))
            assertEquals(writesAfterFirst, store.writes.size)
            val labels = second.pool(phone)!!.items.values.filterIsInstance<PoolApp>().map { it.label }
            assertEquals(listOf("com.a"), labels)
        }

    @Test
    fun initializeTwiceInOneProcessIsANoOp() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedPoolRepository(store)
            repository.initialize(layoutSet("com.a"))
            repository.initialize(layoutSet("com.b"))
            assertEquals(1, store.writes.size)
            assertEquals(1, repository.version.value)
        }

    @Test
    fun theLayoutSetIsNeverChanged() =
        runBlocking {
            val set = layoutSet("com.a")
            val before = set.copy()
            CachedPoolRepository(FakeStore()).initialize(set)
            assertEquals(before, set)
        }

    @Test
    fun widgetProvidersAreBackfilledFromThePlatformLookup() =
        runBlocking {
            val repository = CachedPoolRepository(FakeStore())
            repository.initialize(layoutSet("com.a")) { id -> provider.takeIf { id == HostedWidgetId(42) } }
            val widget = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>().single()
            assertEquals(provider, widget.provider)
        }

    @Test
    fun aReadThatThrowsStillPublishesButNeverWrites() =
        runBlocking {
            val store = FakeStore(failRead = true)
            val repository = CachedPoolRepository(store)
            repository.initialize(layoutSet("com.a"))
            assertNotNull(repository.pool(phone))
            assertTrue(store.writes.isEmpty())
        }

    @Test
    fun aWriteThatThrowsDoesNotFailInitialize() =
        runBlocking {
            val repository = CachedPoolRepository(FakeStore(failWrite = true))
            repository.initialize(layoutSet("com.a"))
            assertNotNull(repository.pool(phone))
        }

    @Test
    fun reimportCatchesUpWithTheLayoutAndPersists() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedPoolRepository(store)
            repository.initialize(layoutSet("com.a"))
            repository.reimport(layoutSet("com.a", "com.b"))
            val labels = repository.pool(phone)!!.items.values.filterIsInstance<PoolApp>().map { it.label }.toSet()
            assertEquals(setOf("com.a", "com.b"), labels)
            assertEquals(2, store.writes.size)
            assertEquals(2, repository.version.value)
        }

    @Test
    fun reimportBeforeInitializeDoesNothing() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedPoolRepository(store)
            repository.reimport(layoutSet("com.a"))
            assertNull(repository.pool(phone))
            assertTrue(store.writes.isEmpty())
            assertFalse(repository.version.value > 0)
        }
}
