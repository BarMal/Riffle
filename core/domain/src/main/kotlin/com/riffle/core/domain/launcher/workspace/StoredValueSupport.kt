package com.riffle.core.domain.launcher.workspace

// Builders and safe readers shared by the workspace codecs. Readers return null or a default for
// anything missing or of the wrong type, so decoding never throws on hand-edited or future data.

internal fun StoredValue.Obj.string(key: String): String? =
    (fields[key] as? StoredValue.Str)?.value?.takeIf { it.isNotBlank() }

internal fun StoredValue.Obj.long(key: String): Long? = (fields[key] as? StoredValue.Num)?.value

internal fun StoredValue.Obj.bool(key: String): Boolean? = (fields[key] as? StoredValue.Bool)?.value

internal fun StoredValue.Obj.obj(key: String): StoredValue.Obj? = fields[key] as? StoredValue.Obj

internal fun StoredValue.Obj.array(key: String): List<StoredValue> = (fields[key] as? StoredValue.Arr)?.items.orEmpty()

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    enumValues<E>().firstOrNull { it.name == name }

/** Constructors validate with require(); a stored value violating them is dropped, never thrown. */
internal inline fun <T> guarded(block: () -> T?): T? =
    try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    }
