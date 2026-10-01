package com.riffle.app.launcher.pool

import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreCodec
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import org.json.JSONObject
import org.json.JSONTokener

/*
 * org.json bridge for the pool store. The domain codec (PoolStoreCodec) owns the schema, the version and the
 * safe-decode rules over the framework-free StoredValue tree; this file only moves that tree to and from JSON
 * text. The pool holds apps, folders and widget providers, never item content.
 */

internal fun encodePoolStore(state: PoolStoreState): String = PoolStoreCodec.encode(state).toJson().toString()

/** Returns null when [value] is not a JSON object or has an unknown version; never throws on malformed input. */
internal fun decodePoolStore(value: String): PoolStoreState? =
    runCatching { JSONTokener(value).nextValue() }
        .getOrNull()
        .let { parsed -> (parsed as? JSONObject)?.toStoredValue() }
        ?.let { PoolStoreCodec.decode(it).state }
