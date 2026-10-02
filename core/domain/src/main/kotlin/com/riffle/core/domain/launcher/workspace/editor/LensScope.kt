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

    companion object {
        /** The scope of [layout] around workspace [id]: its library and every other workspace. */
        fun of(
            layout: LayoutWorkspaces,
            id: WorkspaceId,
        ): LensScope = LensScope(layout.library, layout.workspaces.filter { it.id != id })
    }
}
