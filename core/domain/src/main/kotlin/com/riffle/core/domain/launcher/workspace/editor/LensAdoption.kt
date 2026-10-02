package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensDependent
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.SiteKind
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.mapBindings

/**
 * "Use it in the N other containers with an identical lens", offered after Save as lens. A container is identical
 * when its binding is inline (it uses no saved lens yet) and its lens equals the saved lens exactly, parameters
 * included. Adopting only sets the reference: the lens and the expression stay as they are, so nothing a container
 * draws changes and no container can become invalid. Everything is scoped to one layout, like the library.
 */
object LensAdoption {
    /** The containers of [layout] that would adopt saved lens [id], across all of its workspaces. Empty if unknown. */
    fun identical(
        layout: LayoutWorkspaces,
        id: LensId,
    ): List<LensDependent> {
        val saved = layout.library.find(id) ?: return emptyList()
        return layout.workspaces.flatMap { workspace ->
            WorkspaceBindings.sites(workspace).filter { it.binding.ref == null && it.binding.lens == saved.lens }
                .map {
                    LensDependent(
                        it.workspaceId,
                        it.containerId,
                        it.binding.expression,
                        it.kind == SiteKind.PAGE_SET,
                    )
                }
        }
    }

    /** [layout] with every [identical] container pointing at [id]: one value, so one undo step and one write. */
    fun adopt(
        layout: LayoutWorkspaces,
        id: LensId,
    ): LayoutWorkspaces {
        val saved = layout.library.find(id) ?: return layout
        return layout.mapBindings { site ->
            if (site.binding.ref == null && site.binding.lens == saved.lens) {
                site.binding.copy(
                    ref = id,
                )
            } else {
                site.binding
            }
        }
    }
}
