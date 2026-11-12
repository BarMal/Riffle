package com.riffle.app.launcher

import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.ics.CachedIcsFeedRepository
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Backs the backup flow with the workspace, exclusion and pool repositories, never blocking a thread.
 *
 * Export reads the in-memory copy when the workspace system has initialised this process; otherwise it uses
 * what [prepareExport] loaded from the stores on [scope] while the file picker was open (null when nothing is
 * stored, so with the workspace preview never used the backup is unchanged). Restores write to the stores on
 * [scope] and update the in-memory copies when they exist.
 */
@Suppress("TooManyFunctions")
internal class AndroidWorkspaceBackupPort(
    private val workspaces: CachedWorkspaceRepository,
    private val exclusions: CachedExclusionRepository,
    private val pool: CachedPoolRepository,
    private val scope: CoroutineScope,
    private val stream: CachedStreamLayoutRepository? = null,
    private val calendarFeeds: CachedIcsFeedRepository? = null,
    /**
     * True once the standard home renders from the pool (S4): the pool section is then restored together with the
     * backup's home layouts by the home's own restore (`withImportedBackup`), never on its own here.
     */
    private val poolOwnsHome: () -> Boolean = { false },
) : WorkspaceBackupPort {
    @Volatile
    private var storedSet: WorkspaceSet? = null

    @Volatile
    private var storedRules: LayoutExclusionRules? = null

    @Volatile
    private var storedPool: PoolStoreState? = null

    @Volatile
    private var storedStream: StreamLayouts? = null

    override fun prepareExport() {
        storedSet = null
        storedRules = null
        storedPool = null
        storedStream = null
        scope.launch {
            runCatching { workspaces.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedSet = it }
            runCatching { exclusions.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedRules = it }
            runCatching { pool.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedPool = it }
            runCatching { stream?.currentOrStored() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { storedStream = it }
        }
    }

    override fun currentWorkspaceSet(): WorkspaceSet? = workspaces.load() ?: storedSet

    override fun currentExclusions(): LayoutExclusionRules? = storedRules

    override fun currentPool(): PoolStoreState? = pool.current() ?: storedPool

    override fun currentStreamLayouts(): StreamLayouts? =
        stream?.layouts?.value?.takeIf {
            it.byPosture.isNotEmpty()
        } ?: storedStream

    override fun currentCalendarFeeds(): IcsFeedSettings? =
        calendarFeeds?.takeIf { it.isLoaded }?.settings()?.takeIf { it.feeds.isNotEmpty() }

    override fun restoreCalendarFeeds(feeds: IcsFeedSettings) {
        val repository = calendarFeeds ?: return
        scope.launch {
            runCatching { repository.replaceFromBackup(feeds) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    override fun restoreStreamLayouts(layouts: StreamLayouts) {
        val repository = stream ?: return
        scope.launch {
            runCatching { repository.replaceFromBackup(layouts) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    override fun restoreWorkspaceSet(set: WorkspaceSet) = workspaces.replaceFromBackup(set)

    override fun restoreExclusions(rules: LayoutExclusionRules) {
        scope.launch {
            runCatching { exclusions.replaceFromBackup(rules) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    override fun restorePool(state: PoolStoreState) {
        if (poolOwnsHome()) return
        scope.launch {
            runCatching { pool.replaceFromBackup(state) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }
}
