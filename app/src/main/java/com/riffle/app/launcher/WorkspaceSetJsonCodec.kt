package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/*
 * org.json bridge for the workspace set. The domain codec (WorkspaceSetCodec) owns the schema, the
 * version and the safe-decode rules over the framework-free StoredValue tree; this file only moves
 * that tree to and from JSON text. Only lenses and workspaces are written, never item content.
 */

fun encodeWorkspaceSet(set: WorkspaceSet): String = WorkspaceSetCodec.encode(set).toJson().toString()

/** Returns null when [value] is not a JSON object; never throws on malformed input. */
fun decodeWorkspaceSet(value: String): WorkspaceSet? =
    runCatching { JSONTokener(value).nextValue() }
        .getOrNull()
        .let { parsed -> (parsed as? JSONObject)?.toStoredValue() }
        ?.let(WorkspaceSetCodec::decode)

internal fun StoredValue.toJson(): Any =
    when (this) {
        is StoredValue.Obj ->
            JSONObject().also { json -> fields.forEach { (key, child) -> json.put(key, child.toJson()) } }
        is StoredValue.Arr -> JSONArray().also { json -> items.forEach { child -> json.put(child.toJson()) } }
        is StoredValue.Str -> value
        is StoredValue.Num -> value
        is StoredValue.Bool -> value
    }

/** Null, non-finite numbers and anything nested deeper than [MAX_STORED_VALUE_DEPTH] are skipped. */
internal fun JSONObject.toStoredValue(depth: Int = 0): StoredValue.Obj =
    StoredValue.Obj(
        keys().asSequence()
            .mapNotNull { key -> opt(key).toStoredValueOrNull(depth + 1)?.let { child -> key to child } }
            .toMap(),
    )

private fun JSONArray.toStoredValues(depth: Int): List<StoredValue> =
    (0 until length()).mapNotNull { index -> opt(index).toStoredValueOrNull(depth + 1) }

private fun Any?.toStoredValueOrNull(depth: Int): StoredValue? =
    when {
        depth > MAX_STORED_VALUE_DEPTH -> null
        this is JSONObject -> toStoredValue(depth)
        this is JSONArray -> StoredValue.Arr(toStoredValues(depth))
        this is String -> StoredValue.Str(this)
        this is Boolean -> StoredValue.Bool(this)
        this is Number -> StoredValue.Num(toLong())
        else -> null
    }

private const val MAX_STORED_VALUE_DEPTH = 64
