package com.riffle.core.domain.launcher.workspace

/**
 * Encodes a [LensLibrary] as `{"lenses": [{"id", "name", "lens", "origin"?}]}`. The library stores lens
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
                obj("type" to str("preset"), "preset" to str(origin.presetId), "key" to str(origin.key))
        }

    /** An unknown origin type is ignored (the lens is then simply user-owned), never an error. */
    private fun decodeOrigin(root: StoredValue.Obj?): LensOrigin? =
        when (root?.string("type")) {
            "preset" ->
                root.string("preset")?.let { preset -> root.string("key")?.let { LensOrigin.Preset(preset, it) } }
            else -> null
        }
}
