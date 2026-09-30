package com.riffle.core.domain.launcher.workspace

/**
 * The workspaces one layout (device class) holds: its list in display order, which one is active and
 * which is the default the resolver falls back to.
 *
 * Invariants, enforced by construction: at least one workspace, unique ids, and [activeId] and
 * [defaultId] both name a stored workspace. Every operation returns a value satisfying them. An
 * operation that cannot apply (unknown id, removing the last workspace, a duplicate id, a blank name)
 * returns the receiver unchanged, so callers never have to handle a half-edited state.
 */
data class LayoutWorkspaces(
    val workspaces: List<Workspace>,
    val activeId: WorkspaceId,
    val defaultId: WorkspaceId,
) {
    init {
        require(workspaces.isNotEmpty()) { "A layout needs at least one workspace." }
        require(workspaces.map { it.id }.toSet().size == workspaces.size) { "Workspace ids must be unique." }
        require(workspaces.any { it.id == activeId }) { "The active workspace must exist." }
        require(workspaces.any { it.id == defaultId }) { "The default workspace must exist." }
    }

    val active: Workspace get() = checkNotNull(find(activeId))

    val default: Workspace get() = checkNotNull(find(defaultId))

    fun find(id: WorkspaceId): Workspace? = workspaces.firstOrNull { it.id == id }

    /** Appends [workspace]; it becomes active when [activate]. Ignored when its id is already in use. */
    fun add(
        workspace: Workspace,
        activate: Boolean = false,
    ): LayoutWorkspaces =
        if (find(workspace.id) != null) {
            this
        } else {
            copy(workspaces = workspaces + workspace, activeId = if (activate) workspace.id else activeId)
        }

    /** Removes [id]. The default moves to the first remaining workspace if it was removed; active to the default. */
    fun remove(id: WorkspaceId): LayoutWorkspaces {
        val remaining = workspaces.filterNot { it.id == id }
        if (remaining.size == workspaces.size || remaining.isEmpty()) return this
        val newDefault = if (defaultId == id) remaining.first().id else defaultId
        return copy(
            workspaces = remaining,
            activeId = if (activeId == id) newDefault else activeId,
            defaultId = newDefault,
        )
    }

    fun rename(
        id: WorkspaceId,
        name: String,
    ): LayoutWorkspaces = if (name.isBlank()) this else replace(id) { it.copy(name = name.trim()) }

    /** Moves [id] to [toIndex] (clamped to the list), shifting the others. */
    fun move(
        id: WorkspaceId,
        toIndex: Int,
    ): LayoutWorkspaces {
        val moving = find(id) ?: return this
        val without = workspaces.filterNot { it.id == id }
        return copy(workspaces = without.toMutableList().apply { add(toIndex.coerceIn(0, without.size), moving) })
    }

    /** Inserts a copy of [id] with fresh ids right after it, named "<name> copy", without activating it. */
    fun duplicate(
        id: WorkspaceId,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): LayoutWorkspaces {
        val source = find(id) ?: return this
        val duplicate = WorkspaceCopy.withFreshIds(source, ids).copy(name = "${source.name} copy".trim())
        val index = workspaces.indexOfFirst { it.id == id } + 1
        return if (find(duplicate.id) != null) {
            this
        } else {
            copy(workspaces = workspaces.toMutableList().apply { add(index, duplicate) })
        }
    }

    fun activate(id: WorkspaceId): LayoutWorkspaces = if (find(id) == null) this else copy(activeId = id)

    fun withDefault(id: WorkspaceId): LayoutWorkspaces = if (find(id) == null) this else copy(defaultId = id)

    /** Replaces the content of the workspace with [id] by [transform]'s result, keeping its id. */
    fun replace(
        id: WorkspaceId,
        transform: (Workspace) -> Workspace,
    ): LayoutWorkspaces =
        if (find(id) == null) {
            this
        } else {
            copy(workspaces = workspaces.map { if (it.id == id) transform(it).copy(id = id) else it })
        }

    companion object {
        fun single(workspace: Workspace): LayoutWorkspaces =
            LayoutWorkspaces(listOf(workspace), workspace.id, workspace.id)

        /**
         * Builds a valid value from possibly inconsistent parts (decoded data): later duplicates are
         * dropped and a missing active or default id falls back (active to the default, default to the
         * first workspace). Returns null when no workspace remains.
         */
        fun repaired(
            workspaces: List<Workspace>,
            activeId: WorkspaceId?,
            defaultId: WorkspaceId?,
        ): LayoutWorkspaces? {
            val unique = workspaces.distinctBy { it.id }
            val first = unique.firstOrNull() ?: return null
            val default = unique.firstOrNull { it.id == defaultId } ?: first
            val active = unique.firstOrNull { it.id == activeId } ?: default
            return LayoutWorkspaces(unique, active.id, default.id)
        }
    }
}
