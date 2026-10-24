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
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** A pool store on disk, for restarts: a new [CachedPoolRepository] over the same instance reads what was written. */
internal class DiskPoolStore(
    var stored: PoolStoreState? = null,
    var failWrite: Boolean = false,
    /** The stored value is there but cannot be decoded. */
    var corrupt: Boolean = false,
    var failRead: Boolean = false,
) : PoolStorePort {
    var writes = 0
    var quarantined = false

    override suspend fun read(): PoolStoreState? {
        if (failRead) error("disk")
        if (corrupt) throw UndecodablePoolException()
        return stored
    }

    override suspend fun write(state: PoolStoreState) {
        if (failWrite) error("full")
        writes++
        corrupt = false
        stored = state
    }

    override suspend fun quarantine() {
        quarantined = true
    }
}

/** The `HomeLayoutSet` store, with the cutover snapshot. */
internal class DiskHomeLayoutStore(
    var stored: HomeLayoutSet? = null,
    var snapshot: HomeLayoutSet? = null,
    /** A snapshot value is stored but cannot be decoded. */
    var snapshotUnreadable: Boolean = false,
) : HomeLayoutCutoverStore {
    var writes = 0
    var snapshotQuarantined = false

    override suspend fun read(): HomeLayoutSet? = stored

    override suspend fun write(layoutSet: HomeLayoutSet) {
        writes++
        stored = layoutSet
    }

    override suspend fun readPrePoolSnapshot(): PrePoolSnapshot =
        when {
            snapshotUnreadable -> PrePoolSnapshot.Unreadable
            else -> snapshot?.let { PrePoolSnapshot.Present(it) } ?: PrePoolSnapshot.Absent
        }

    override suspend fun writePrePoolSnapshot(snapshot: HomeLayoutSet?) {
        snapshotUnreadable = false
        this.snapshot = snapshot
    }

    override suspend fun quarantinePrePoolSnapshot() {
        snapshotQuarantined = true
    }
}

/** The legacy write-behind repository, writing straight through to [store]. */
internal class WriteThroughHomeLayouts(
    private val store: DiskHomeLayoutStore,
) : HomeLayoutRepository {
    val saved = mutableListOf<HomeLayoutSet>()

    override fun loadHomeLayout(): HomeLayout? = store.stored?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        saveHomeLayoutSet(HomeLayoutSet.fromLayout(layout))
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? = store.stored

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        saved += layoutSet
        store.stored = layoutSet
    }
}

/** A pool-backed home whose debounced writes never fire on their own: tests flush explicitly. */
internal fun poolHomeRepository(
    legacy: HomeLayoutRepository,
    pool: CachedPoolRepository,
    snapshots: DiskHomeLayoutStore? = null,
): PoolHomeLayoutRepository {
    var n = 0
    return PoolHomeLayoutRepository(
        legacy = legacy,
        pool = pool,
        scope = CoroutineScope(Job()),
        ids = WorkspaceIdFactory { "minted${++n}" },
        debounceMillis = Long.MAX_VALUE,
        writeSnapshot = { snapshot -> snapshots?.writePrePoolSnapshot(snapshot) },
    )
}

internal object PoolHomeFixture {
    val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    val home = LauncherPageId("home")
    val library = LauncherPageId("library:1")

    fun identity(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    fun at(
        column: Int,
        row: Int,
        columns: Int = 1,
        rows: Int = 1,
    ) = GridPlacement(GridCell(column, row), GridSpan(columns, rows))

    fun app(
        id: String,
        column: Int,
    ) = AppShortcutItem(LauncherItemId(id), identity(id), id, placement = at(column, 0))

    /** Library mode: a Home page with two apps and a widget, an All-apps page, a dock pin. */
    fun legacySet(): HomeLayoutSet {
        val page =
            LauncherPage(
                home,
                LauncherPageType.Home,
                GridDimensions(4, 5),
                listOf(
                    app("mail", 0),
                    app("maps", 1),
                    WidgetItem(LauncherItemId("clock"), HostedWidgetId(42), "Clock", placement = at(0, 1, 2, 2)),
                ),
            )
        val all = LauncherPage(library, LauncherPageType.AllApps, GridDimensions(4, 5), listOf(app("library-app:x", 0)))
        val dock = DockModel(capacity = 5, items = listOf(app("phone", 0).copy(placement = null)))
        val layout = HomeLayout(LauncherViewMode.HOME_SCREEN_LIBRARY, listOf(page, all), home, dock)
        return HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout))
    }

    fun HomeLayout.homeItems() = pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items }

    /** Ids as the legacy set had them (the migration prefix stripped). */
    fun String.legacyId() = replace(Regex("^pi:[a-z_]+:[a-z_]+:"), "")
}
