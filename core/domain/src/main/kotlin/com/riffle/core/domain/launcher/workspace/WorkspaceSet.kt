package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * Every workspace Riffle stores, per layout (device class). Layouts are independent: there is no live
 * link between them, and [copyFromOtherLayout] is a one-time deep copy. Holds no item content.
 *
 * A device class with no stored entry reads as the built-in default workspace
 * ([WorkspaceMigration.defaultFor]), so every layout always has a drawable workspace.
 */
data class WorkspaceSet(
    val layouts: Map<HomeLayoutDeviceClass, LayoutWorkspaces> = emptyMap(),
) {
    fun workspacesFor(deviceClass: HomeLayoutDeviceClass): LayoutWorkspaces =
        layouts[deviceClass] ?: WorkspaceMigration.defaultFor(deviceClass)

    fun withLayout(
        deviceClass: HomeLayoutDeviceClass,
        workspaces: LayoutWorkspaces,
    ): WorkspaceSet = copy(layouts = layouts + (deviceClass to workspaces))

    /** Applies [edit] to [deviceClass]'s workspaces (materializing the default if none are stored). */
    fun update(
        deviceClass: HomeLayoutDeviceClass,
        edit: (LayoutWorkspaces) -> LayoutWorkspaces,
    ): WorkspaceSet = withLayout(deviceClass, edit(workspacesFor(deviceClass)))

    /**
     * One-time "Copy from other layout": [target]'s workspaces are replaced by deep copies of
     * [source]'s, with fresh ids throughout; active and default map onto their copies. Nothing stays
     * linked afterwards. A no-op when [source] and [target] are the same layout.
     */
    fun copyFromOtherLayout(
        source: HomeLayoutDeviceClass,
        target: HomeLayoutDeviceClass,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): WorkspaceSet {
        if (source == target) return this
        val from = workspacesFor(source)
        // Refs cannot cross layouts: the library is copied with fresh ids and every ref rewritten.
        val library = LensLibraryCopy.copy(from.library, ids)
        val copies =
            from.workspaces.map { it.id to LensLibraryCopy.remap(WorkspaceCopy.withFreshIds(it, ids), library.idMap) }
        val byOldId = copies.toMap()
        val copied =
            LayoutWorkspaces.repaired(
                workspaces = copies.map { it.second },
                activeId = byOldId[from.activeId]?.id,
                defaultId = byOldId[from.defaultId]?.id,
                library = library.library,
            )
        return copied?.let { withLayout(target, it) } ?: this
    }

    /**
     * The workspace to draw on [deviceClass]: the active one, or the layout's default with the reasons
     * when this layout's [capabilities] (or the known [sources]) cannot draw the active one.
     */
    fun resolveActive(
        deviceClass: HomeLayoutDeviceClass,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        sources: List<SourceDescriptor>? = null,
    ): WorkspaceResolution {
        val stored = workspacesFor(deviceClass)
        return WorkspaceResolver.resolve(stored.active, stored.default, capabilities, sources)
    }
}
