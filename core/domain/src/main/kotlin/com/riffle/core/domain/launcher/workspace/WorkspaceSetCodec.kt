package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

const val CURRENT_WORKSPACE_SET_SCHEMA_VERSION = 2

/**
 * Encodes and decodes a [WorkspaceSet] to [StoredValue], on top of [WorkspaceCodec]. Only lenses and
 * workspaces are written; there is no item content to write.
 *
 * Decoding never throws. A layout entry with an unknown device class is dropped; workspaces that fail
 * to decode or have no drawable page are dropped; a missing or stale active or default id falls back
 * (see [LayoutWorkspaces.repaired]); a layout left with no workspace is dropped, so it reads as the
 * built-in default and migration can rebuild it. A newer schema version is read on a best-effort basis.
 *
 * Schema 2 adds, per layout, an optional `library` (saved lenses) and, per binding, an optional `ref`; a
 * schema 1 blob has neither and decodes unchanged. Every ref-carrying binding also stores its lens inline,
 * so a reader that ignores both keys still draws every container.
 */
object WorkspaceSetCodec {
    fun encode(set: WorkspaceSet): StoredValue.Obj =
        obj(
            "version" to num(CURRENT_WORKSPACE_SET_SCHEMA_VERSION),
            "layouts" to arr(set.layouts.map { (deviceClass, layout) -> encodeLayout(deviceClass, layout) }),
        )

    /** Returns null only when [value] is not an object; anything else decodes to a (possibly empty) set. */
    fun decode(value: StoredValue?): WorkspaceSet? {
        val root = value as? StoredValue.Obj ?: return null
        val layouts =
            root.array("layouts")
                .mapNotNull { (it as? StoredValue.Obj)?.let(::decodeLayout) }
                .toMap()
        return WorkspaceSet(layouts)
    }

    private fun encodeLayout(
        deviceClass: HomeLayoutDeviceClass,
        layout: LayoutWorkspaces,
    ): StoredValue.Obj =
        obj(
            "deviceClass" to str(deviceClass.name),
            "active" to str(layout.activeId.value),
            "default" to str(layout.defaultId.value),
            "workspaces" to arr(layout.workspaces.map(WorkspaceCodec::encode)),
            "library" to layout.library.takeIf { it.lenses.isNotEmpty() }?.let(LensLibraryCodec::encode),
        )

    private fun decodeLayout(root: StoredValue.Obj): Pair<HomeLayoutDeviceClass, LayoutWorkspaces>? {
        val deviceClass = enumOrNull<HomeLayoutDeviceClass>(root.string("deviceClass")) ?: return null
        val workspaces =
            root.array("workspaces")
                .mapNotNull(WorkspaceCodec::decodeWorkspace)
                .filter { it.pages.isNotEmpty() }
        val layout =
            LayoutWorkspaces.repaired(
                workspaces = workspaces,
                activeId = root.string("active")?.let(::WorkspaceId),
                defaultId = root.string("default")?.let(::WorkspaceId),
                library = LensLibraryCodec.decode(root.obj("library")),
            )
        // A ref that resolves in this layout's library takes the library's lens; others keep their snapshot.
        return layout?.let { deviceClass to LensLibraryOps.rehydrate(it).layout }
    }
}
