package com.riffle.core.domain.launcher.workspace

/** Builds an object, skipping entries whose value is null. */
internal fun obj(vararg entries: Pair<String, StoredValue?>): StoredValue.Obj =
    StoredValue.Obj(entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

internal fun str(value: String) = StoredValue.Str(value)

internal fun num(value: Number) = StoredValue.Num(value.toLong())

internal fun arr(items: List<StoredValue>) = StoredValue.Arr(items)
