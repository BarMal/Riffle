package com.riffle.app.launcher.savedhome

import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutsCodec
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutsDecodeResult
import org.json.JSONObject
import org.json.JSONTokener

/*
 * org.json bridge for the saved Home layouts. The domain codec (SavedHomeLayoutsCodec) owns the schema, the version and
 * the safe-decode rules over the framework-free StoredValue tree; this file only moves that tree to and from JSON
 * text. A saved layout holds apps, folders and widget descriptions (provider, span), never a host id or item content.
 */

internal fun encodeSavedHomeLayouts(layouts: SavedHomeLayouts): String =
    SavedHomeLayoutsCodec.encode(layouts).toJson().toString()

/** Never throws: text that is not a JSON object reads as nothing stored. */
internal fun decodeSavedHomeLayouts(value: String): SavedHomeLayoutsDecodeResult =
    runCatching { JSONTokener(value).nextValue() }
        .getOrNull()
        .let { parsed -> (parsed as? JSONObject)?.let { runCatching { it.toStoredValue() }.getOrNull() } }
        .let { SavedHomeLayoutsCodec.decode(it) }

/** The backup document's `savedHomeLayouts` section, or null when it is absent, empty or unreadable. */
internal fun JSONObject.optSavedHomeLayouts(): SavedHomeLayouts? =
    runCatching {
        optJSONObject("savedHomeLayouts")?.let { decodeSavedHomeLayouts(it.toString()).layouts }?.takeIf { !it.isEmpty }
    }.getOrNull()
