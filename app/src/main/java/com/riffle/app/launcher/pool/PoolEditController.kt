package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolEditSession
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeEditing
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeTarget
import com.riffle.core.domain.launcher.workspace.pool.PoolHostIds
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolRemoval
import com.riffle.core.domain.launcher.workspace.pool.PoolResult
import com.riffle.core.domain.launcher.workspace.pool.PoolWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A one-shot message for the snackbar. [seq] makes two identical messages distinct. */
internal data class PoolEditNotice(
    val seq: Long,
    val text: String,
    /** True when it describes an applied edit, so the snackbar offers Undo. */
    val undoable: Boolean,
)

internal data class PoolEditState(
    val editing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selected: PoolItemId? = null,
    val notice: PoolEditNotice? = null,
    /** An item whose "Delete everywhere" awaits the user's confirmation. */
    val deleteRequest: PoolItemId? = null,
    /** "Refresh home items" awaits confirmation: it replaces the preview's edits. */
    val reimportRequested: Boolean = false,
)

/**
 * Edit mode of the preview's home pages, over the [PoolEditSession] pattern: one session per edit mode, every edit
 * one atomic immutable `PoolEdit`, Undo and Redo restore pool values. Compose-free, so it is JVM-tested.
 *
 * Each applied edit is published to the [repository] at once (in memory) and written after a quiet period
 * ([debounceMillis]) by one atomic store write; [exit] and [flushNow] write immediately. Widget host ids that left
 * the pool wait in the session's queue and are handed to [onHostIdsReleased] only after the final write succeeded
 * and only if the standard home does not hold them (it shares the migrated widgets' ids and is never changed here).
 * A host id queued when the process dies is leaked, not deleted (startup reconciliation is not built).
 *
 * Nothing runs unless [enter] is called, which only the preview's Edit action does.
 */
@Suppress("TooManyFunctions")
internal class PoolEditController(
    private val repository: CachedPoolRepository,
    private val scope: CoroutineScope,
    private val onHostIdsReleased: (Set<HostedWidgetId>) -> Unit,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val mutableState = MutableStateFlow(PoolEditState())
    val state: StateFlow<PoolEditState> = mutableState.asStateFlow()

    private var session: PoolEditSession? = null
    private var deviceClass: HomeLayoutDeviceClass? = null
    private var standardHostIds: () -> Set<HostedWidgetId> = { emptySet() }
    private var flushJob: Job? = null
    private var noticeSeq = 0L

    /** The device class being edited, or null outside edit mode. */
    val editingDeviceClass: HomeLayoutDeviceClass? get() = if (session == null) null else deviceClass

    /** Starts edit mode for [deviceClass]'s pool. False when that class has no pool (nothing to edit). */
    fun enter(
        deviceClass: HomeLayoutDeviceClass,
        standardHostIds: () -> Set<HostedWidgetId>,
    ): Boolean {
        val pool = repository.pool(deviceClass)
        return if (pool == null || session != null) {
            session != null
        } else {
            session = PoolEditSession(pool)
            this.deviceClass = deviceClass
            this.standardHostIds = standardHostIds
            mutableState.value = PoolEditState(editing = true)
            true
        }
    }

    /** Leaves edit mode: closes the Undo window, writes now, then releases the dropped widget host ids. */
    fun exit() {
        val current = session ?: return
        val (released, _) = current.finish()
        val deletable = PoolHostIds.deletable(released, standardHostIds())
        session = null
        deviceClass = null
        flushJob?.cancel()
        mutableState.update { PoolEditState(reimportRequested = it.reimportRequested) }
        scope.launch {
            if (repository.flush() && deletable.isNotEmpty()) onHostIdsReleased(deletable)
        }
    }

    /** Writes pending edits now (the activity is stopping). Keeps edit mode and the Undo history. */
    fun flushNow() {
        flushJob?.cancel()
        scope.launch { repository.flush() }
    }

    fun select(id: PoolItemId?) {
        if (session != null) mutableState.update { it.copy(selected = id) }
    }

    fun requestDelete(id: PoolItemId) {
        if (session != null) mutableState.update { it.copy(deleteRequest = id) }
    }

    fun dismissDelete() = mutableState.update { it.copy(deleteRequest = null) }

    fun requestReimport() = mutableState.update { it.copy(reimportRequested = true) }

    fun dismissReimport() = mutableState.update { it.copy(reimportRequested = false) }

    fun noticeShown(notice: PoolEditNotice) {
        mutableState.update { if (it.notice == notice) it.copy(notice = null) else it }
    }

    fun undo() {
        session?.takeIf { it.canUndo }?.let { replace(it.undo(), notice = null) }
    }

    fun redo() {
        session?.takeIf { it.canRedo }?.let { replace(it.redo(), notice = null) }
    }

    /** Applies [op] to the session's pool as one edit; [summary] is the snackbar text when it changes something. */
    fun perform(
        summary: String,
        op: (PlacedItemPool) -> PoolResult,
    ) {
        val current = session ?: return
        val step = current.apply(op(current.pool))
        val rejection = (step.result as? PoolResult.Rejected)?.reason
        when {
            rejection != null -> notify(PoolEditText.rejected(rejection), undoable = false)
            step.changed -> replace(step.session, notice = summary)
        }
    }

    fun move(
        target: PoolHomeTarget,
        item: PoolItemId,
        label: String,
        columns: Int,
        rows: Int,
    ) = perform(PoolEditText.moved(label)) { PoolHomeEditing.nudge(it, target.workspaceId, item, columns, rows) }

    fun moveToCell(
        target: PoolHomeTarget,
        item: PoolItemId,
        label: String,
        toPage: LauncherPageId,
        cell: GridCell,
    ) = perform(PoolEditText.moved(label)) {
        PoolHomeEditing.moveToCell(it, target.workspaceId, item, toPage, cell)
    }

    fun moveToPage(
        target: PoolHomeTarget,
        item: PoolItemId,
        label: String,
        delta: Int,
    ) = perform(PoolEditText.moved(label)) {
        PoolHomeEditing.moveToAdjacentPage(it, target.workspaceId, item, delta)
    }

    fun remove(
        target: PoolHomeTarget,
        item: PoolItemId,
        label: String,
    ) {
        perform(PoolEditText.removed(label)) { PoolRemoval.remove(it, target.workspaceId, item) }
        select(null)
    }

    fun deleteEverywhere(
        item: PoolItemId,
        label: String,
    ) {
        perform(PoolEditText.deleted(label)) { PoolRemoval.removeEverywhere(it, item) }
        select(null)
    }

    fun addApp(
        target: PoolHomeTarget,
        identity: AppIdentity,
        label: String,
    ) = perform(PoolEditText.added(label)) {
        PoolHomeEditing.addApp(it, target.workspaceId, target.pageId, identity, label, ids)
    }

    fun addPage(workspaceId: WorkspaceId) =
        perform(PoolEditText.PAGE_ADDED) {
            PoolHomeEditing.addPage(it, workspaceId, ids)
        }

    fun removePage(target: PoolHomeTarget) =
        perform(PoolEditText.PAGE_REMOVED) { PoolHomeEditing.removeEmptyPage(it, target.workspaceId, target.pageId) }

    /** "Add a separate copy": a new placeholder widget for the same provider (never a second placement). */
    fun separateCopy(
        target: PoolHomeTarget,
        item: PoolItemId,
        label: String,
    ) = perform(PoolEditText.copied(label)) {
        PoolWidgets.separateCopy(it, item, target.workspaceId, target.pageId, ids, newHostedId = null)
    }

    /** The pool being edited right now (edits included), for the confirm text and the page lookup. */
    fun currentPool(): PlacedItemPool? = session?.pool

    private fun replace(
        next: PoolEditSession,
        notice: String?,
    ) {
        session = next
        val dc = deviceClass
        if (dc != null) repository.update(dc, next.pool)
        scheduleFlush()
        val selected = mutableState.value.selected?.takeIf { it in next.pool.items }
        mutableState.update {
            it.copy(
                canUndo = next.canUndo,
                canRedo = next.canRedo,
                selected = selected,
                notice = notice?.let { text -> PoolEditNotice(++noticeSeq, text, undoable = true) },
            )
        }
    }

    private fun notify(
        text: String,
        undoable: Boolean,
    ) {
        mutableState.update { it.copy(notice = PoolEditNotice(++noticeSeq, text, undoable)) }
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob =
            scope.launch {
                delay(debounceMillis)
                repository.flush()
            }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 400L
    }
}
