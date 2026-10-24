package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The notice a blocked home edit shows (read-only home). Names the two ways out; no item content. */
internal const val READ_ONLY_HOME_NOTICE =
    "Your home screen can't be changed right now. Restart Riffle, or restore a backup in Settings > Backup."

/** Restores a backup into the home's stores; implemented by the pool-backed and the read-only home. */
internal interface BackupRestoringHomeLayouts {
    fun restoreFromBackup(
        homeLayouts: HomeLayoutSet,
        backupPool: PoolStoreState?,
        homeFromPool: Boolean,
    ): HomeLayoutSet
}

/**
 * The home of a process that cannot use the pool although the store was cut over (the pool or the `HomeLayoutSet`
 * cannot be read or written, or part of the home is damaged): shown read-only from the best full set there is (the
 * recovery snapshot, else what is stored, else the shell's defaults), so nothing can be lost by editing a copy.
 *
 * - A commit that changes placed items or page structure of a layout already shown (`Home` pages, docks) is refused:
 *   the shell keeps [shown], and [blockedEdits] fires (at most once per [noticeIntervalMillis]) for the notice.
 *   Selection, edit mode, a newly seen layout key (rotation, a new device class) and other non-item changes are kept in
 *   memory only.
 * - Nothing is ever written: not `HomeLayoutSet`, not the pool, not the snapshot (and the platform wiring deletes no
 *   widget host id in such a process).
 * - The one exception is [restoreFromBackup], the recovery the notice names: the backup's layouts are written as the
 *   full `HomeLayoutSet` and the cutover marker removed (via [restore]), so the next start cuts over from them.
 */
internal class ReadOnlyHomeLayoutRepository(
    initial: HomeLayoutSet?,
    private val restore: (HomeLayoutSet) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val noticeIntervalMillis: Long = NOTICE_INTERVAL_MILLIS,
    /** Called for every refused edit (the notice itself is throttled). */
    private val onBlocked: () -> Unit = {},
) : HomeLayoutRepository,
    BackupRestoringHomeLayouts {
    @Volatile
    private var shown: HomeLayoutSet? = initial
    private var lastNoticeAt = Long.MIN_VALUE
    private val mutableBlockedEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when an edit was refused, for the short notice. */
    val blockedEdits: SharedFlow<Unit> = mutableBlockedEdits.asSharedFlow()

    override fun loadHomeLayout(): HomeLayout? = shown?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        commitHomeLayoutSet(shown?.withActiveLayout(layout) ?: HomeLayoutSet.fromLayout(layout))
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? = shown

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        commitHomeLayoutSet(layoutSet)
    }

    override fun commitHomeLayoutSet(layoutSet: HomeLayoutSet): HomeLayoutSet {
        val current = shown
        if (current != null && changesPlacedItems(current, layoutSet)) {
            notifyBlocked()
            return current
        }
        shown = layoutSet
        return layoutSet
    }

    override fun restoreFromBackup(
        homeLayouts: HomeLayoutSet,
        backupPool: PoolStoreState?,
        homeFromPool: Boolean,
    ): HomeLayoutSet {
        restore(homeLayouts)
        shown = homeLayouts
        return homeLayouts
    }

    private fun notifyBlocked() {
        onBlocked()
        val now = clock()
        if (lastNoticeAt == Long.MIN_VALUE || now - lastNoticeAt >= noticeIntervalMillis) {
            lastNoticeAt = now
            mutableBlockedEdits.tryEmit(Unit)
        }
    }

    companion object {
        const val NOTICE_INTERVAL_MILLIS = 3_000L

        /**
         * True when [next] changes what [current] shows for a layout key or a dock it already has: `Home` pages (their
         * items, order, grids) or a dock's pins and panel. Keys and device classes [current] does not have yet are new
         * defaults, not edits.
         */
        fun changesPlacedItems(
            current: HomeLayoutSet,
            next: HomeLayoutSet,
        ): Boolean {
            val layouts =
                current.layouts.keys.intersect(next.layouts.keys).any { key ->
                    homeOf(current.layoutFor(key)) != homeOf(next.layoutFor(key))
                }
            val docks =
                current.docks.keys.intersect(next.docks.keys).any { deviceClass ->
                    val before = current.docks.getValue(deviceClass)
                    val after = next.docks.getValue(deviceClass)
                    before.items != after.items || before.panel != after.panel
                }
            return layouts || docks
        }

        private fun homeOf(layout: HomeLayout) =
            layout.pages.filter { it.type == LauncherPageType.Home } to (layout.dock.items to layout.dock.panel)
    }
}
