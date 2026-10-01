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
            "dock" to obj("dynamic" to workspace.dock.dynamicSection?.let(LensCodec::encodeBinding)),
            "gestures" to StoredValue.Obj(workspace.gestureBindings.mapValues { str(it.value) }),
            "skin" to workspace.skinOverrideId?.let(::str),
            "preset" to workspace.presetId?.let(::str),
            "start" to workspace.startPageId?.let { str(it.value) },
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
                dock = WorkspaceDock(dynamicSection = LensCodec.decodeBinding(root.obj("dock")?.obj("dynamic"))),
                gestureBindings =
                    root.obj("gestures")?.fields.orEmpty()
                        .mapNotNull { (k, v) -> (v as? StoredValue.Str)?.let { k to it.value } }
                        .toMap(),
                skinOverrideId = root.string("skin"),
                presetId = root.string("preset")?.takeIf { it.isNotBlank() },
                startPageId = root.string("start")?.takeIf { it.isNotBlank() }?.let(::ContainerId),
            )
        }
    }

    /** Returns null when no usable source remains. */
    fun decodeLens(value: StoredValue?): Lens? = LensCodec.decode(value)
}
