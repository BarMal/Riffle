package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.pool.PoolCodec
import com.riffle.core.domain.launcher.workspace.pool.PoolValidation

const val CURRENT_WORKSPACE_SET_SCHEMA_VERSION = 2

/**
 * Encodes and decodes a [WorkspaceSet] to [StoredValue], on top of [WorkspaceCodec]. Only lenses and
 * workspaces are written; there is no item content to write.
 *
 * Decoding never throws. A layout entry with an unknown device class is dropped; workspaces that fail
 * to decode or have no drawable page are dropped; a missing or stale active or default id falls back
 * (see [LayoutWorkspaces.repaired]); a layout left with no workspace is dropped, so it reads as the
 * built-in default and migration can rebuild it. The optional pool (schema 2) decodes safely, see
 * [PoolCodec]. A newer schema version is read on a best-effort basis.
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
            "pool" to layout.pool.takeUnless { it.isEmpty }?.let(PoolCodec::encode),
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
            )
        return layout?.let { deviceClass to it.withDecodedPool(root.obj("pool")) }
    }

    /** Missing or unreadable pool data is an empty pool; arrangements of unknown workspaces are dropped. */
    private fun LayoutWorkspaces.withDecodedPool(value: StoredValue.Obj?): LayoutWorkspaces =
        copy(pool = PoolValidation.retainWorkspaces(PoolCodec.decode(value).pool, workspaces.map { it.id }.toSet()))
}
