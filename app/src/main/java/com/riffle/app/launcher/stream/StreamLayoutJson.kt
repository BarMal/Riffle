package com.riffle.app.launcher.stream

import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.stream.StreamLayoutCodec
import com.riffle.core.domain.launcher.stream.StreamLayouts
import org.json.JSONObject
import org.json.JSONTokener

/*
 * org.json bridge for the Stream layouts. The domain codec (StreamLayoutCodec) owns the schema and the safe-decode
 * rules over the framework-free StoredValue tree; this file only moves that tree to and from JSON text. A layout
 * holds only the user's choices (sections, order, views, options), never any content.
 */

internal fun encodeStreamLayouts(layouts: StreamLayouts): String = StreamLayoutCodec.encode(layouts).toJson().toString()

/** Returns null when [value] is not a JSON object; never throws on malformed input. */
internal fun decodeStreamLayouts(value: String): StreamLayouts? =
    runCatching { JSONTokener(value).nextValue() }
        .getOrNull()
        .let { parsed -> (parsed as? JSONObject)?.toStoredValue() }
        ?.let(StreamLayoutCodec::decode)
