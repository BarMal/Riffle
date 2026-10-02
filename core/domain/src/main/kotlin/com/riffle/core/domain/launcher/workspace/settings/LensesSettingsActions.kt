package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryRejection
import com.riffle.core.domain.launcher.workspace.LensLibraryResult
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/** What happened, for the announcement the page makes. Wording is the UI's. */
sealed interface LensesSettingsMessage {
    data class Created(val name: String) : LensesSettingsMessage

    /** [detached] containers kept the old lens because the edit would have broken them. */
    data class Saved(val name: String, val detached: Int) : LensesSettingsMessage

    data class Renamed(val name: String) : LensesSettingsMessage

    data class Duplicated(val name: String) : LensesSettingsMessage

    /**
     * [usedBy] containers used it; [detached] of them kept their lens inline and the rest follow [replacedBy]
     * (null when the choice was Detach, in which case every one is detached).
     */
    data class Deleted(
        val name: String,
        val usedBy: Int,
        val detached: Int,
        val replacedBy: String?,
    ) : LensesSettingsMessage

    data class CopiedToLayout(
        val name: String,
        val target: HomeLayoutDeviceClass,
        val newName: String,
    ) : LensesSettingsMessage

    /** The library or the name refuses it. */
    data class Refused(val problem: LibraryProblem) : LensesSettingsMessage

    /** The edit would break [count] containers and the choice was to refuse it. */
    data class BreaksContainers(val count: Int) : LensesSettingsMessage

    data object NothingCanDraw : LensesSettingsMessage

    data object NothingToDo : LensesSettingsMessage
}

/**
 * The outcome of one action: the new [set] (the receiver unchanged when [applied] is false) and the [message].
 * [undo] puts back exactly the layout the action replaced ([changedLayout]; for a copy that is the target, never
 * the viewed layout), for destructive actions only, so it cannot clobber another layout's edits.
 */
data class LensesSettingsChange(
    val set: WorkspaceSet,
    val applied: Boolean,
    val message: LensesSettingsMessage,
    val changedLayout: HomeLayoutDeviceClass? = null,
    val before: LayoutWorkspaces? = null,
    val undoable: Boolean = false,
    /** The lens that was created or duplicated, so the page can open or select it. */
    val lensId: LensId? = null,
) {
    /** [current] with the changed layout put back, or [current] when this change is not undoable. */
    fun undo(current: WorkspaceSet): WorkspaceSet =
        if (undoable && changedLayout != null && before != null) current.withLayout(changedLayout, before) else current
}

/** What the actions need besides the stored set: the registry's descriptors, what the layout draws and ids. */
data class LensesEnvironment(
    val descriptors: List<SourceDescriptor>? = null,
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
)

/**
 * One thing the Saved lenses page can do to the layout it shows. Pure over [WorkspaceSet]: nothing here reads or
 * writes storage. Every edit goes through [LensLibraryOps] and [com.riffle.core.domain.launcher.workspace.LensLibrary],
 * so the library invariants (cap, names, no dangling refs, snapshots equal the library lens) hold after each one.
 */
sealed interface LensesSettingsAction {
    fun applyTo(
        set: WorkspaceSet,
        layout: HomeLayoutDeviceClass,
        env: LensesEnvironment = LensesEnvironment(),
    ): LensesSettingsChange

    /** Adds a lens ("+ New" and "Save as a new lens"). */
    data class Create(val name: String, val lens: Lens) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val stored = set.workspacesFor(layout)
            if (LensDetailPlanner.drawableAs(lens, env.descriptors, env.capabilities).isEmpty()) {
                return set.refused(LensesSettingsMessage.NothingCanDraw)
            }
            return when (val added = stored.library.tryAdd(name, lens, env.ids)) {
                is LibraryAdd.Rejected -> set.refused(LensesSettingsMessage.Refused(added.problem))
                is LibraryAdd.Added ->
                    set.changed(
                        layout,
                        stored.copy(library = added.library),
                        LensesSettingsMessage.Created(name.trim()),
                    )
                        .copy(lensId = added.id)
            }
        }
    }

    /**
     * Saves the draft of [id]: renames it and/or changes its definition. A change that would break containers is
     * refused under [BreakPolicy.REJECT] and detaches just those under [BreakPolicy.DETACH_BROKEN]. Undoable.
     */
    data class Save(
        val id: LensId,
        val name: String,
        val lens: Lens,
        val policy: BreakPolicy = BreakPolicy.REJECT,
    ) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val stored = set.workspacesFor(layout)
            val saved = stored.library.find(id) ?: return set.refused(LensesSettingsMessage.Refused(UNKNOWN))
            val trimmed = name.trim()
            val renamed = trimmed != saved.name
            val nameProblem = if (renamed) stored.library.nameProblem(trimmed, id) else null
            return when {
                nameProblem != null -> set.refused(LensesSettingsMessage.Refused(nameProblem))
                lens == saved.lens && !renamed -> set.refused(LensesSettingsMessage.NothingToDo)
                lens == saved.lens ->
                    set.changed(
                        layout,
                        stored.copy(library = stored.library.rename(id, trimmed)),
                        LensesSettingsMessage.Renamed(trimmed),
                        undoable = true,
                    )
                else -> edit(set, layout, stored.copy(library = stored.library.rename(id, trimmed)), env)
            }
        }

        private fun edit(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            renamed: LayoutWorkspaces,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            if (LensDetailPlanner.drawableAs(lens, env.descriptors, env.capabilities).isEmpty()) {
                return set.refused(LensesSettingsMessage.NothingCanDraw)
            }
            return when (
                val result =
                    LensLibraryOps.applyEdit(
                        renamed,
                        id,
                        lens,
                        policy,
                        env.descriptors,
                        env.capabilities,
                    )
            ) {
                is LensLibraryResult.Applied ->
                    set.changed(
                        layout,
                        result.layout,
                        LensesSettingsMessage.Saved(name.trim(), result.detached.size),
                        undoable = true,
                    )
                is LensLibraryResult.Rejected ->
                    when (val reason = result.reason) {
                        is LensLibraryRejection.BreaksDependents ->
                            set.refused(LensesSettingsMessage.BreaksContainers(reason.impact.wouldBreak.size))
                        is LensLibraryRejection.Problem -> set.refused(LensesSettingsMessage.Refused(reason.problem))
                    }
            }
        }
    }

    data class Rename(val id: LensId, val name: String) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val stored = set.workspacesFor(layout)
            val trimmed = name.trim()
            val problem = if (stored.library.find(id) == null) UNKNOWN else stored.library.nameProblem(trimmed, id)
            return if (problem != null) {
                set.refused(LensesSettingsMessage.Refused(problem))
            } else {
                set.changed(
                    layout,
                    stored.copy(library = stored.library.rename(id, trimmed)),
                    LensesSettingsMessage.Renamed(trimmed),
                )
            }
        }
    }

    /** A copy with a fresh id and a unique name ("<name> copy"), right after the original. Nothing is rebound. */
    data class Duplicate(val id: LensId) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val stored = set.workspacesFor(layout)
            val index = stored.library.lenses.indexOfFirst { it.id == id }
            val duplicated = stored.library.duplicate(id, env.ids)
            val added = duplicated.lenses.getOrNull(index + 1)
            return when {
                index < 0 -> set.refused(LensesSettingsMessage.Refused(UNKNOWN))
                stored.library.lenses.size >= MAX_SAVED_LENSES ->
                    set.refused(LensesSettingsMessage.Refused(LibraryProblem.LIBRARY_FULL))
                added == null -> set.refused(LensesSettingsMessage.NothingToDo)
                else ->
                    set.changed(layout, stored.copy(library = duplicated), LensesSettingsMessage.Duplicated(added.name))
                        .copy(lensId = added.id)
            }
        }
    }

    /**
     * Deletes a lens; never blocked. [RemovePolicy.Detach] makes every user keep its lens inline;
     * [RemovePolicy.ReplaceWith] points them at another saved lens (the ones it would break are detached). Undoable.
     */
    data class Delete(val id: LensId, val policy: RemovePolicy = RemovePolicy.Detach) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val stored = set.workspacesFor(layout)
            val saved = stored.library.find(id)
            val usedBy = LensLibraryOps.dependents(stored, id).size
            val replacement = (policy as? RemovePolicy.ReplaceWith)?.id?.let(stored.library::find)
            return when (val result = LensLibraryOps.remove(stored, id, policy, env.descriptors, env.capabilities)) {
                is LensLibraryResult.Rejected -> set.refused(LensesSettingsMessage.Refused(UNKNOWN))
                is LensLibraryResult.Applied ->
                    set.changed(
                        layout,
                        result.layout,
                        LensesSettingsMessage.Deleted(
                            name = saved?.name.orEmpty(),
                            usedBy = usedBy,
                            detached = if (replacement == null) usedBy else result.detached.size,
                            replacedBy = replacement?.name,
                        ),
                        undoable = true,
                    )
            }
        }
    }

    /** An explicit one-time copy into [target]'s library: fresh id, collision-free name, nothing linked. Undoable. */
    data class CopyToLayout(val id: LensId, val target: HomeLayoutDeviceClass) : LensesSettingsAction {
        override fun applyTo(
            set: WorkspaceSet,
            layout: HomeLayoutDeviceClass,
            env: LensesEnvironment,
        ): LensesSettingsChange {
            val saved = set.workspacesFor(layout).library.find(id)
            val destination = set.workspacesFor(target)
            val copied =
                saved?.takeIf { target != layout }?.let {
                    destination.library.addCopy(
                        it.copy(origin = null),
                        env.ids,
                    )
                }
            return when {
                saved == null -> set.refused(LensesSettingsMessage.Refused(UNKNOWN))
                target == layout -> set.refused(LensesSettingsMessage.NothingToDo)
                copied == null -> set.refused(LensesSettingsMessage.Refused(LibraryProblem.LIBRARY_FULL))
                else ->
                    set.changed(
                        target,
                        destination.copy(library = copied.first),
                        LensesSettingsMessage.CopiedToLayout(
                            saved.name,
                            target,
                            copied.first.find(copied.second)?.name.orEmpty(),
                        ),
                        undoable = true,
                    ).copy(lensId = copied.second)
            }
        }
    }
}

private val UNKNOWN = LibraryProblem.UNKNOWN_LENS

private fun WorkspaceSet.refused(message: LensesSettingsMessage) = LensesSettingsChange(this, false, message)

/** Stores [layout] as [device]'s workspaces. [before] defaults to what was stored (the undo target). */
private fun WorkspaceSet.changed(
    device: HomeLayoutDeviceClass,
    layout: LayoutWorkspaces,
    message: LensesSettingsMessage,
    undoable: Boolean = false,
    before: LayoutWorkspaces = workspacesFor(device),
) = LensesSettingsChange(withLayout(device, layout), true, message, device, before, undoable)
