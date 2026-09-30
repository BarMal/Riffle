package com.riffle.core.domain.launcher.workspace

/**
 * Framework-free JSON-like tree used as the serialization seam for lenses and workspaces. The app
 * layer bridges it to its JSON library; keeping it here keeps the domain free of Android types.
 */
sealed interface StoredValue {
    data class Obj(val fields: Map<String, StoredValue>) : StoredValue

    data class Arr(val items: List<StoredValue>) : StoredValue

    data class Str(val value: String) : StoredValue

    data class Num(val value: Long) : StoredValue

    data class Bool(val value: Boolean) : StoredValue
}
