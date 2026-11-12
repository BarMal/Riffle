package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * Every workspace Riffle stores, per layout (device class). Layouts are independent: there is no live
 * link between them. Holds no item content. Since ADR 0003 nothing in the app edits it (the stored workspaces stay
 * decodable and untouched, only read to find the active arrangement), so it round-trips whatever was stored.
 *
 * A device class with no stored entry reads as the built-in default workspace
 * ([WorkspaceMigration.defaultFor]), so every layout always has a drawable workspace.
 *
 * Every accessor keys by [workspaceLayoutClass] (N10): `PHONE_LANDSCAPE` reads and writes `PHONE`, so rotation
 * never changes which workspaces are shown. Stored data is folded once on load ([WorkspaceLayoutFold]).
 */
data class WorkspaceSet(
    val layouts: Map<HomeLayoutDeviceClass, LayoutWorkspaces> = emptyMap(),
) {
    fun workspacesFor(deviceClass: HomeLayoutDeviceClass): LayoutWorkspaces =
        deviceClass.workspaceLayoutClass().let { layout -> layouts[layout] ?: WorkspaceMigration.defaultFor(layout) }

    /** Whether [deviceClass]'s layout has stored workspaces (rather than reading as the built-in default). */
    fun hasLayout(deviceClass: HomeLayoutDeviceClass): Boolean = deviceClass.workspaceLayoutClass() in layouts

    fun withLayout(
        deviceClass: HomeLayoutDeviceClass,
        workspaces: LayoutWorkspaces,
    ): WorkspaceSet = copy(layouts = layouts + (deviceClass.workspaceLayoutClass() to workspaces))

    /** Applies [edit] to [deviceClass]'s workspaces (materializing the default if none are stored). */
    fun update(
        deviceClass: HomeLayoutDeviceClass,
        edit: (LayoutWorkspaces) -> LayoutWorkspaces,
    ): WorkspaceSet = withLayout(deviceClass, edit(workspacesFor(deviceClass)))
}
