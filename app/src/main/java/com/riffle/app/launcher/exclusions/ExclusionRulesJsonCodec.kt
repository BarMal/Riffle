package com.riffle.app.launcher.exclusions

import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRulesCodec
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import org.json.JSONObject
import org.json.JSONTokener

/*
 * org.json bridge for the per-layout exclusion rules. The domain codec (ExclusionRulesCodec) owns the schema
 * and the safe-decode rules over the framework-free StoredValue tree; this file only moves that tree to and
 * from JSON text. Rules hold identifiers and user-chosen match text, never item content.
 */

internal fun encodeExclusionRules(rules: LayoutExclusionRules): String =
    ExclusionRulesCodec.encode(rules).toJson().toString()

/** Returns null when [value] is not a JSON object; never throws on malformed input. */
internal fun decodeExclusionRules(value: String): LayoutExclusionRules? =
    runCatching { JSONTokener(value).nextValue() }
        .getOrNull()
        .let { parsed -> (parsed as? JSONObject)?.toStoredValue() }
        ?.let(ExclusionRulesCodec::decode)
