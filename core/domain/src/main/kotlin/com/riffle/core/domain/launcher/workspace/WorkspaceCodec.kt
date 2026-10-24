package com.riffle.core.domain.launcher.workspace

const val CURRENT_WORKSPACE_SCHEMA_VERSION = 1

/**
 * Encodes and decodes [Lens] and [Workspace] to [StoredValue].
 *
 * Decoding is safe: unknown enum values and unknown filter or group types fall back to a default,
 * malformed containers and unrecoverable entries are dropped, and it never throws. There is
 * deliberately no codec for [Item]: item content is transient and must never be persisted.
 */
object WorkspaceCodec {
    fun encode(workspace: Workspace): StoredValue.Obj =
        obj(
            "version" to num(CURRENT_WORKSPACE_SCHEMA_VERSION),
            "id" to str(workspace.id.value),
            "name" to str(workspace.name),
            "pages" to arr(workspace.pages.map(PageCodec::encode)),
            "dock" to encodeDock(workspace.dock),
            "gestures" to StoredValue.Obj(workspace.gestureBindings.mapValues { str(it.value) }),
            "skin" to workspace.skinOverrideId?.let(::str),
            "preset" to workspace.presetId?.let(::str),
            "start" to workspace.startPageId?.let { str(it.value) },
            "newApps" to workspace.newApps?.let(WorkspaceExtrasCodec::encodeNewApps),
        )

    fun encode(lens: Lens): StoredValue.Obj = LensCodec.encode(lens)

    /** Returns null only when the value has no usable identity (missing or blank id). */
    fun decodeWorkspace(value: StoredValue?): Workspace? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            Workspace(
                id = WorkspaceId(root.string("id") ?: return@guarded null),
                name = root.string("name").orEmpty(),
                pages = root.array("pages").mapNotNull(PageCodec::decode),
                dock = decodeDock(root.obj("dock")),
                gestureBindings =
                    root.obj("gestures")?.fields.orEmpty()
                        .mapNotNull { (k, v) -> (v as? StoredValue.Str)?.let { k to it.value } }
                        .toMap(),
                skinOverrideId = root.string("skin"),
                presetId = root.string("preset")?.takeIf { it.isNotBlank() },
                startPageId = root.string("start")?.takeIf { it.isNotBlank() }?.let(::ContainerId),
                newApps = WorkspaceExtrasCodec.decodeNewApps(root.obj("newApps")),
            )
        }
    }

    private fun encodeDock(dock: WorkspaceDock): StoredValue.Obj =
        obj(
            "dynamic" to dock.dynamicSection?.let(LensCodec::encodeBinding),
            "presentation" to dock.presentation?.let(WorkspaceExtrasCodec::encodePresentation),
        )

    private fun decodeDock(dock: StoredValue.Obj?): WorkspaceDock =
        WorkspaceDock(
            dynamicSection = LensCodec.decodeBinding(dock?.obj("dynamic")),
            presentation = WorkspaceExtrasCodec.decodePresentation(dock?.obj("presentation")),
        )

    /** Returns null when no usable source remains. */
    fun decodeLens(value: StoredValue?): Lens? = LensCodec.decode(value)
}
