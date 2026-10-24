package com.riffle.core.domain.launcher.workspace

/**
 * Encodes a [LensLibrary] as `{"lenses": [{"id", "name", "lens", "origin"?}]}`, a preset origin as
 * `{"type": "preset", "preset", "key", "installed"?}` ("installed" additive). The library stores lens
 * definitions only: no item content, no query text. Decoding never throws: an entry that fails to decode
 * is dropped (its bindings become dangling-with-snapshot rather than lost), and the survivors are repaired
 * to unique ids and names within the cap.
 */
internal object LensLibraryCodec {
    fun encode(library: LensLibrary): StoredValue.Obj = obj("lenses" to arr(library.lenses.map(::encodeEntry)))

    fun decode(value: StoredValue.Obj?): LensLibrary =
        LensNames.repaired(value.arrayOrEmpty().mapNotNull { (it as? StoredValue.Obj)?.let(::decodeEntry) })

    private fun StoredValue.Obj?.arrayOrEmpty(): List<StoredValue> = this?.array("lenses").orEmpty()

    private fun encodeEntry(saved: SavedLens): StoredValue.Obj =
        obj(
            "id" to str(saved.id.value),
            "name" to str(saved.name),
            "lens" to LensCodec.encode(saved.lens),
            "origin" to saved.origin?.let(::encodeOrigin),
        )

    private fun decodeEntry(root: StoredValue.Obj): SavedLens? =
        guarded {
            val id = root.string("id") ?: return@guarded null
            val lens = LensCodec.decode(root.fields["lens"]) ?: return@guarded null
            SavedLens(LensId(id), root.string("name").orEmpty(), lens, decodeOrigin(root.obj("origin")))
        }

    private fun encodeOrigin(origin: LensOrigin): StoredValue.Obj =
        when (origin) {
            is LensOrigin.Preset ->
                obj(
                    "type" to str("preset"),
                    "preset" to str(origin.presetId),
                    "key" to str(origin.key),
                    "installed" to origin.installed?.let(LensCodec::encode),
                )
        }

    /**
     * An unknown origin type is ignored (the lens is then simply user-owned), never an error. A missing or
     * undecodable "installed" definition (older blobs) reads as null, so the entry counts as edited.
     */
    private fun decodeOrigin(root: StoredValue.Obj?): LensOrigin? =
        when (root?.string("type")) {
            "preset" ->
                root.string("preset")?.let { preset ->
                    root.string("key")?.let { key ->
                        LensOrigin.Preset(preset, key, guarded { LensCodec.decode(root.fields["installed"]) })
                    }
                }
            else -> null
        }
}
