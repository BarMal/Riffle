package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.PresetLensInstaller
import com.riffle.core.domain.launcher.workspace.preset.PresetPosture
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets

/** What happened, for the confirmation the page announces. Wording is the UI's. */
sealed interface WorkspacesSettingsMessage {
    data class Activated(val name: String) : WorkspacesSettingsMessage

    data class Renamed(val name: String) : WorkspacesSettingsMessage

    data class Duplicated(val name: String) : WorkspacesSettingsMessage

    data class Moved(val name: String) : WorkspacesSettingsMessage

    data class MadeDefault(val name: String) : WorkspacesSettingsMessage

    data class Deleted(val name: String) : WorkspacesSettingsMessage

    data class Reset(val name: String, val presetName: String) : WorkspacesSettingsMessage

    data class Installed(val name: String) : WorkspacesSettingsMessage

    data class Copied(val source: HomeLayoutDeviceClass, val replaced: Int, val copied: Int) :
        WorkspacesSettingsMessage

    /** The last workspace of a layout is never deleted. */
    data object CannotDeleteLast : WorkspacesSettingsMessage

    data object InvalidName : WorkspacesSettingsMessage

    /** Reset to preset is hidden, not guessed, when the preset a workspace came from is not recorded. */
    data object NoKnownPreset : WorkspacesSettingsMessage

    data object NothingToDo : WorkspacesSettingsMessage
}

/**
 * The outcome of one action: the new [set] (the receiver unchanged when [applied] is false) and the
 * [message] to announce. [undo] restores exactly what the action replaced, for destructive actions only;
 * it touches only the layout the action changed, so it never clobbers another layout's edits.
 */
data class WorkspacesSettingsChange(
    val set: WorkspaceSet,
    val applied: Boolean,
    val message: WorkspacesSettingsMessage,
    val before: LayoutWorkspaces? = null,
    val undoable: Boolean = false,
) {
    /** [current] with the changed layout put back, or [current] when this change is not undoable. */
    fun undo(
        current: WorkspaceSet,
        layout: HomeLayoutDeviceClass,
    ): WorkspaceSet = if (undoable && before != null) current.withLayout(layout, before) else current
}

/**
 * One thing the Workspaces page can do to the layout it shows. Pure over [WorkspaceSet]: nothing here
 * reads or writes storage, and placed home items are never involved (workspaces hold no items).
 */
sealed interface WorkspacesSettingsAction {
    fun applyTo(
        set: WorkspaceSet,
        layout: HomeLayoutDeviceClass,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): WorkspacesSettingsChange

    data class Activate(val id: WorkspaceId) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ) = set.edit(layout, { WorkspacesSettingsMessage.Activated(it.nameOf(id)) }) { it.activate(id) }
    }

    data class Rename(val id: WorkspaceId, val name: String) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ) = if (name.isBlank()) {
            set.unchanged(WorkspacesSettingsMessage.InvalidName)
        } else {
            set.edit(layout, { WorkspacesSettingsMessage.Renamed(it.nameOf(id)) }) { it.rename(id, name) }
        }
    }

    data class Duplicate(val id: WorkspaceId) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ) = set.edit(layout, { WorkspacesSettingsMessage.Duplicated(it.nameOf(id)) }) { it.duplicate(id, ids) }
    }

    data class Move(val id: WorkspaceId, val delta: Int) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ) = set.edit(layout, { WorkspacesSettingsMessage.Moved(it.nameOf(id)) }) { stored ->
            val index = stored.workspaces.indexOfFirst { it.id == id }
            if (index < 0) stored else stored.move(id, index + delta)
        }
    }

    data class MakeDefault(val id: WorkspaceId) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ) = set.edit(layout, { WorkspacesSettingsMessage.MadeDefault(it.nameOf(id)) }) { it.withDefault(id) }
    }

    /** Removes a workspace; never the last one. Undoable. */
    data class Delete(val id: WorkspaceId) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ): WorkspacesSettingsChange {
            val stored = set.workspacesFor(layout)
            return if (stored.workspaces.size <= 1) {
                set.unchanged(WorkspacesSettingsMessage.CannotDeleteLast)
            } else {
                set.edit(layout, { WorkspacesSettingsMessage.Deleted(it.nameOf(id)) }, undoable = true) {
                    it.remove(id)
                }
            }
        }
    }

    /** Replaces a workspace's pages, dock and bindings by its recorded preset's, keeping its id and name. Undoable. */
    data class ResetToPreset(val id: WorkspaceId) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ): WorkspacesSettingsChange {
            val workspace = set.workspacesFor(layout).find(id)
            val preset = workspace?.let(WorkspacesSettingsPlanner::presetOf)
            return if (workspace == null || preset == null) {
                set.unchanged(WorkspacesSettingsMessage.NoKnownPreset)
            } else {
                val message = WorkspacesSettingsMessage.Reset(workspace.name, preset.name)
                set.edit(layout, { message }, undoable = true) { stored ->
                    PresetLensInstaller.reset(stored, id, preset, PresetPosture.of(layout), ids = ids)
                }
            }
        }
    }

    /** Adds a fresh copy of a preset to the layout, optionally making it the active workspace. */
    data class InstallPreset(val presetId: String, val activate: Boolean) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ): WorkspacesSettingsChange {
            val preset = WorkspacePresets.byId(presetId) ?: return set.unchanged(WorkspacesSettingsMessage.NothingToDo)
            val stored = set.workspacesFor(layout)
            val name = uniqueName(preset.name, stored)
            return set.edit(layout, { WorkspacesSettingsMessage.Installed(name) }) { current ->
                val installed = PresetLensInstaller.install(current, preset, PresetPosture.of(layout), ids, activate)
                installed.layout.replace(installed.workspaceId) { it.copy(name = name, presetId = preset.id) }
            }
        }
    }

    /**
     * The one-time copy: this layout's workspaces are replaced by fresh copies of [source]'s. Nothing stays
     * linked afterwards. Undoable.
     */
    data class CopyFromLayout(val source: HomeLayoutDeviceClass) : WorkspacesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            ids: WorkspaceIdFactory,
        ): WorkspacesSettingsChange {
            val replaced = set.workspacesFor(layout).workspaces.size
            val copied = set.workspacesFor(source).workspaces.size
            val next = set.copyFromOtherLayout(source, layout, ids)
            return if (next == set) {
                set.unchanged(WorkspacesSettingsMessage.NothingToDo)
            } else {
                WorkspacesSettingsChange(
                    set = next,
                    applied = true,
                    message = WorkspacesSettingsMessage.Copied(source, replaced, copied),
                    before = set.workspacesFor(layout),
                    undoable = true,
                )
            }
        }
    }
}

private fun LayoutWorkspaces.nameOf(id: WorkspaceId): String = find(id)?.name.orEmpty()

private fun uniqueName(
    base: String,
    layout: LayoutWorkspaces,
): String {
    val taken = layout.workspaces.map { it.name }.toSet()
    return generateSequence(1) { it + 1 }
        .map { n -> if (n == 1) base else "$base $n" }
        .first { it !in taken }
}

private fun WorkspaceSet.unchanged(message: WorkspacesSettingsMessage) =
    WorkspacesSettingsChange(set = this, applied = false, message = message)

/**
 * Applies [transform] to [layout]'s workspaces. The message is built from the layout as it was before
 * (so a deleted workspace is still named). A transform that changes nothing is not an applied change.
 */
private fun WorkspaceSet.edit(
    layout: HomeLayoutDeviceClass,
    message: (LayoutWorkspaces) -> WorkspacesSettingsMessage,
    undoable: Boolean = false,
    transform: (LayoutWorkspaces) -> LayoutWorkspaces,
): WorkspacesSettingsChange {
    val before = workspacesFor(layout)
    val after = transform(before)
    return if (after == before) {
        unchanged(WorkspacesSettingsMessage.NothingToDo)
    } else {
        WorkspacesSettingsChange(
            set = withLayout(layout, after),
            applied = true,
            message = message(before),
            before = before,
            undoable = undoable,
        )
    }
}
