package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.WorkspacesDialogText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.settings.SourceUsage
import com.riffle.core.domain.launcher.workspace.settings.SourceUsagePlanner
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsChange
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-time announcement the page shows after an action: [message] is read out by the snackbar and
 * [canUndo] adds its Undo button. [id] is new for every announcement so an identical message shows again.
 */
internal data class WorkspacesFeedback(
    val id: Long,
    val message: String,
    val canUndo: Boolean,
)

/**
 * Runs the Workspaces page's actions against the workspace [repository] and keeps the one Undo the
 * announcement offers. No Android types. All persistence goes through the repository; nothing here reads or
 * writes home items, and the actions never delete the last workspace of a layout (the domain refuses).
 *
 * Only the latest destructive change can be undone, and any later change drops the offer, so Undo can never
 * overwrite something newer. [onChanged] runs after every applied change or undo (the dock menu re-plans).
 */
internal class WorkspacesSettingsController(
    private val repository: WorkspaceRepository,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val onChanged: () -> Unit = {},
) {
    private class UndoOffer(val layout: HomeLayoutDeviceClass, val change: WorkspacesSettingsChange)

    private var offer: UndoOffer? = null
    private var announcements = 0L
    private val mutableFeedback = MutableStateFlow<WorkspacesFeedback?>(null)

    val feedback: StateFlow<WorkspacesFeedback?> = mutableFeedback.asStateFlow()

    /** The page's model, or null until the repository has loaded. */
    fun model(
        viewed: HomeLayoutDeviceClass,
        current: HomeLayoutDeviceClass,
        available: Collection<HomeLayoutDeviceClass>,
    ): WorkspacesSettingsModel? =
        repository.load()?.let { set -> WorkspacesSettingsPlanner.plan(set, viewed, current, available) }

    /**
     * Which places read each source across [layouts] (a layout with nothing stored reads as its default workspace,
     * as the rest of Settings does), or null until the repository has loaded. Reads only; nothing is changed.
     */
    fun sourceUsage(layouts: Collection<HomeLayoutDeviceClass>): SourceUsage? =
        repository.load()?.let { set ->
            SourceUsagePlanner.plan(layouts.distinct().associateWith { set.workspacesFor(it) })
        }

    /** Applies [action] to [layout] and persists it. False when it changed nothing (the reason is announced). */
    fun dispatch(
        layout: HomeLayoutDeviceClass,
        action: WorkspacesSettingsAction,
    ): Boolean {
        val set = repository.load() ?: return false
        val change = action.applyTo(set, layout, ids)
        offer = if (change.applied && change.undoable) UndoOffer(layout, change) else null
        if (change.applied) {
            repository.save(change.set)
            onChanged()
        }
        announce(WorkspacesDialogText.message(change.message), canUndo = offer != null)
        return change.applied
    }

    /** Puts back what the last destructive change replaced, if its Undo is still on offer. */
    fun undo() {
        val pending = offer ?: return
        val set = repository.load() ?: return
        offer = null
        repository.save(pending.change.undo(set, pending.layout))
        onChanged()
        announce(UNDONE, canUndo = false)
    }

    /** The announcement was shown (or dismissed): without Undo being pressed the offer ends with it. */
    fun feedbackShown(id: Long) {
        if (mutableFeedback.value?.id == id) {
            mutableFeedback.value = null
            offer = null
        }
    }

    /** The page closed: nothing is left to announce and no Undo is on offer. */
    fun leave() {
        mutableFeedback.value = null
        offer = null
    }

    private fun announce(
        message: String,
        canUndo: Boolean,
    ) {
        mutableFeedback.value = WorkspacesFeedback(++announcements, message, canUndo)
    }

    companion object {
        const val UNDONE = "Undone"
    }
}
