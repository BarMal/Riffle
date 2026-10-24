package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeCommit
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeSync
import com.riffle.core.domain.launcher.workspace.pool.PoolHostIds
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

internal const val POOL_HOME_WRITE_DEBOUNCE_MILLIS = 250L

/**
 * The standard home rendered and edited FROM the placed-items pool (S4 cutover, app half). The shell keeps talking to
 * a [HomeLayoutRepository]; this one answers with the pool's view of every layout ([PoolHomeSync]) and is the single
 * write path for placed items:
 *
 * - [commitHomeLayoutSet] (every layout write of the shell) ingests what the engines changed into the pool, publishes
 *   the edited pool to [pool] (in memory at once, written behind after [debounceMillis]) and returns the projected set
 *   the shell keeps. The [legacy] `HomeLayoutSet` store is told only what the pool does not hold (page slots, generated
 *   and All-apps pages, settings, selection, mode choices, dock settings and Library's edge): placed items and dock
 *   pins are no longer written there ([PoolHomeSync.stored]).
 * - Widget host ids the pool let go of are handed to [hostIdReleaser] once the pool write has landed, and only those
 *   nothing references any more ([isHostIdReferenced]: no arrangement, no dock, not the shell's set).
 * - [externalChanges] tells the shell when the pool changed elsewhere (the Workspaces preview's editor writes the same
 *   [CachedPoolRepository]), so it can [reproject]; the preview in turn observes [CachedPoolRepository.version].
 *
 * Before [pool] has loaded, every call falls through to [legacy] unchanged. Main thread, like the shell's reducers.
 * Nothing here logs or stores item content.
 */
@Suppress("TooManyFunctions") // The repository contract plus the cutover's restore, reprojection and host-id calls.
internal class PoolHomeLayoutRepository(
    private val legacy: HomeLayoutRepository,
    private val pool: CachedPoolRepository,
    private val scope: CoroutineScope,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    private val debounceMillis: Long = POOL_HOME_WRITE_DEBOUNCE_MILLIS,
    /** Keeps the recovery snapshot recent: the full projected set, written after the pool's own writes. */
    private val writeSnapshot: suspend (HomeLayoutSet) -> Unit = {},
) : HomeLayoutRepository {
    private val lock = Any()

    @Volatile
    private var shown: HomeLayoutSet? = null
    private var lastStored: HomeLayoutSet? = null
    private var releasedPending: Set<HostedWidgetId> = emptySet()

    /** The pool state this repository last published or projected from; anything else is someone else's change. */
    @Volatile
    private var lastPublished: PoolStoreState? = null
    private var lastSnapshot: HomeLayoutSet? = null
    private val flushRequests = AtomicLong()

    init {
        // Once the pool on disk is everything in memory, whoever wrote it (the home or the preview's editor): released
        // host ids go, and the recovery snapshot catches up with the pool.
        pool.onWritten {
            if (pool.isWritten) {
                releaseHostIds()
                scope.launch { refreshSnapshot() }
            }
        }
    }

    /** Deletes host ids nothing references any more; set by the platform wiring (it hops to the main thread). */
    @Volatile
    var hostIdReleaser: ((Set<HostedWidgetId>) -> Unit)? = null

    /**
     * Changes of the pool this repository did not make, for the shell to [reproject]. Compared by state, not by version
     * (the version flow is conflated): a change is skipped only when the pool is exactly what this repository last
     * published, which already includes anything it was built on.
     */
    val externalChanges: Flow<Unit>
        get() = pool.version.drop(1).filter { pool.current().let { it != null && it !== lastPublished } }.map { }

    /**
     * Projects [stored] (what the `HomeLayoutSet` store holds: page slots without placed items once cut over) from
     * the loaded pool, so the first frame already shows the pool's items. A null [stored] (nothing stored yet) leaves
     * the first [commitHomeLayoutSet] to adopt whatever the shell starts with.
     */
    fun open(stored: HomeLayoutSet?) {
        val state = pool.current() ?: return
        synchronized(lock) { lastStored = stored }
        // A set with placed items was written by a legacy fallback session: ingest its edits ([PoolHomeSync.open]).
        if (stored != null) publish(PoolHomeSync.open(state, stored, ids), state)
    }

    override fun loadHomeLayout(): HomeLayout? = (shown ?: legacy.loadHomeLayoutSet())?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        commitHomeLayoutSet(shown?.withActiveLayout(layout) ?: HomeLayoutSet.fromLayout(layout))
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? = shown ?: legacy.loadHomeLayoutSet()

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        commitHomeLayoutSet(layoutSet)
    }

    override fun commitHomeLayoutSet(layoutSet: HomeLayoutSet): HomeLayoutSet {
        val state = pool.current() ?: return layoutSet.also(legacy::saveHomeLayoutSet)
        // Nothing shown yet (a fresh install starts from a default set the pool never showed): the edit is measured
        // against the pool's view of that set, so it is ingested instead of being projected away.
        val previous = shown ?: PoolHomeSync.projection(state, layoutSet)
        return publish(PoolHomeSync.commit(state, previous, layoutSet, ids), state)
    }

    override fun withoutRemovedPackage(
        layoutSet: HomeLayoutSet,
        packageName: AppPackageName,
        profile: AppProfile,
    ): HomeLayoutSet {
        val state = pool.current() ?: return layoutSet
        return publish(PoolHomeSync.pruneUninstalled(state, layoutSet, packageName, profile, ids), state)
    }

    /** [layoutSet] (the shell's) projected again from the pool as it is now, after [externalChanges]. */
    fun reproject(layoutSet: HomeLayoutSet): HomeLayoutSet {
        val state = pool.current() ?: return layoutSet
        return publish(PoolHomeSync.reproject(state, layoutSet, ids), state)
    }

    /**
     * Restores a backup ([PoolHomeSync.restore]): a cut-over backup's pool section is authoritative, any other backup's
     * `homeLayouts` are imported into the pool. Returns the set the shell keeps. Before the pool loaded this is a plain
     * `HomeLayoutSet` restore.
     */
    fun restoreFromBackup(
        homeLayouts: HomeLayoutSet,
        backupPool: PoolStoreState?,
        homeFromPool: Boolean,
    ): HomeLayoutSet {
        val state = pool.current() ?: return homeLayouts.also(legacy::saveHomeLayoutSet)
        return publish(PoolHomeSync.restore(state, backupPool, homeLayouts, homeFromPool, ids, providerOf), state)
    }

    /** Whether anything still shows [hostedWidgetId]; true while the pool is not loaded (never delete on a guess). */
    fun isHostIdReferenced(hostedWidgetId: HostedWidgetId): Boolean {
        val state = pool.current() ?: return true
        return PoolHostIds.isReferenced(hostedWidgetId, state, shown)
    }

    /**
     * Writes the pool now, then releases host ids it let go of and refreshes the recovery snapshot (only after the
     * pool write landed, so the snapshot never runs ahead of the pool). The legacy store flushes on its own.
     */
    suspend fun flushNow() {
        if (pool.flush()) {
            releaseHostIds()
            refreshSnapshot()
        }
    }

    /** Writes the pool's full projection of the shell's set as the recovery snapshot, when it changed. */
    private suspend fun refreshSnapshot() {
        val state = pool.current()
        val base = shown
        val snapshot = if (state == null || base == null) null else PoolHomeSync.projection(state, base)
        val changed =
            snapshot != null && synchronized(lock) { (snapshot != lastSnapshot).also { lastSnapshot = snapshot } }
        if (snapshot != null && changed) {
            val written =
                runCatching {
                    writeSnapshot(
                        snapshot,
                    )
                }.onFailure { if (it is CancellationException) throw it }
            // A failed snapshot write is retried after the next pool write.
            if (written.isFailure) synchronized(lock) { if (lastSnapshot == snapshot) lastSnapshot = null }
        }
    }

    private fun publish(
        commit: PoolHomeCommit,
        before: PoolStoreState,
    ): HomeLayoutSet {
        val stored = PoolHomeSync.stored(commit.shown, commit.state.pools.keys)
        val storedChanged =
            synchronized(lock) {
                if (commit.state != before) pool.replace(commit.state)
                lastPublished = pool.current()
                shown = commit.shown
                releasedPending = releasedPending + commit.releasedHostIds
                (stored != lastStored).also { lastStored = stored }
            }
        if (storedChanged) legacy.saveHomeLayoutSet(stored)
        if (commit.state != before || commit.releasedHostIds.isNotEmpty()) scheduleFlush()
        return commit.shown
    }

    private fun scheduleFlush() {
        val request = flushRequests.incrementAndGet()
        scope.launch {
            delay(debounceMillis)
            if (flushRequests.get() == request) flushNow()
        }
    }

    private fun releaseHostIds() {
        val releaser = hostIdReleaser ?: return
        val pending =
            synchronized(lock) {
                releasedPending.also { releasedPending = emptySet() }
            }
        val deletable = pending.filterNot(::isHostIdReferenced).toSet()
        if (deletable.isNotEmpty()) releaser(deletable)
    }
}
