package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/**
 * What a saved-lens operation in the editor can touch besides the workspace being edited: the layout's library and
 * the layout's other workspaces (an "identical lens" can be adopted by a container of another workspace).
 *
 * The editor session carries it next to the draft, so Save as lens, adopting identical containers and Undo change the
 * workspace, the library and the other workspaces as one step. [layoutWith] builds the [LayoutWorkspaces] the pure
 * library operations (`LensLibraryEditor`, `LensLibraryOps`) work on; [of] splits an operation's result back.
 */
data class LensScope(
    val library: LensLibrary = LensLibrary(),
    /** Every other workspace of the layout, in layout order. Never contains the edited workspace. */
    val others: List<Workspace> = emptyList(),
) {
    /** The layout as the editor sees it: [workspace] (the draft) first, then [others]. */
    fun layoutWith(workspace: Workspace): LayoutWorkspaces =
        LayoutWorkspaces(
            workspaces = listOf(workspace) + others.filter { it.id != workspace.id }.distinctBy { it.id },
            activeId = workspace.id,
            defaultId = workspace.id,
            library = library,
        )

    /** The other workspaces that differ from [base] (the ones a save has to write back). */
    fun changedOthers(base: LensScope): List<Workspace> {
        val before = base.others.associateBy { it.id }
        return others.filter { before[it.id] != it }
    }

    /** What a save writes besides the edited workspace: the library if it changed, and the workspaces that did. */
    fun changeFrom(base: LensScope): LensScopeChange =
        LensScopeChange(library.takeIf { it != base.library }, changedOthers(base))

    companion object {
        /** The scope of [layout] around workspace [id]: its library and every other workspace. */
        fun of(
            layout: LayoutWorkspaces,
            id: WorkspaceId,
        ): LensScope = LensScope(layout.library, layout.workspaces.filter { it.id != id })
    }
}

/**
 * The part of a saved editor session that lies outside the edited workspace, so the host writes only what changed
 * and leaves the rest of the layout as it is. Empty for a session that never touched a saved lens.
 */
data class LensScopeChange(
    val library: LensLibrary? = null,
    val others: List<Workspace> = emptyList(),
) {
    val isEmpty: Boolean get() = library == null && others.isEmpty()

    /** [layout] with the new library and the changed workspaces written; workspaces that vanished are skipped. */
    fun applyTo(layout: LayoutWorkspaces): LayoutWorkspaces {
        val withOthers = others.fold(layout) { acc, other -> acc.replace(other.id) { other } }
        return if (library == null) withOthers else withOthers.copy(library = library)
    }
}
