package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshState
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.MAX_CONFIGURED_FEEDS
import org.json.JSONArray
import org.json.JSONObject

/** Persisted validators beyond this length are dropped (not truncated: a cut validator would be wrong). */
internal const val MAX_PERSISTED_VALIDATOR_LENGTH = 256

/** Persisted failure counts are clamped so a corrupt value cannot hold a feed in backoff forever. */
internal const val MAX_PERSISTED_CONSECUTIVE_FAILURES = 32

/** At most one persisted state per configurable feed. */
internal const val MAX_PERSISTED_REFRESH_STATES = MAX_CONFIGURED_FEEDS

/**
 * Encodes per-feed refresh bookkeeping (timestamps, failure counters, a coarse failure reason and the
 * ETag/Last-Modified validators). Holds no URLs, no response bodies and no article content. Bounded to
 * [MAX_PERSISTED_REFRESH_STATES] entries.
 */
internal fun encodeRefreshStates(states: Map<FeedId, FeedRefreshState>): JSONArray =
    JSONArray(
        states.entries.take(MAX_PERSISTED_REFRESH_STATES).map { (id, state) ->
            JSONObject()
                .put("feedId", id.value)
                .put("failures", state.consecutiveFailures)
                .putOpt("lastAttemptAt", state.lastAttemptAtEpochMillis)
                .putOpt("lastSuccessAt", state.lastSuccessAtEpochMillis)
                .putOpt("lastFailure", state.lastFailure?.name)
                .putOpt("etag", state.validators?.etag?.takeIf(::isPersistableValidator))
                .putOpt("lastModified", state.validators?.lastModified?.takeIf(::isPersistableValidator))
        },
    )

/** [incoming] wins over [existing]; the result keeps at most [MAX_PERSISTED_REFRESH_STATES] newest entries. */
internal fun mergeRefreshStates(
    existing: Map<FeedId, FeedRefreshState>,
    incoming: Map<FeedId, FeedRefreshState>,
): Map<FeedId, FeedRefreshState> {
    val merged = LinkedHashMap(existing)
    incoming.forEach { (id, state) ->
        merged.remove(id)
        merged[id] = state
    }
    return merged.entries.toList().takeLast(MAX_PERSISTED_REFRESH_STATES).associate { it.key to it.value }
}

/** Safe decode: malformed entries are dropped, numbers are clamped, unknown enum names become null. */
internal fun decodeRefreshStates(array: JSONArray?): Map<FeedId, FeedRefreshState> {
    val length = minOf(array?.length() ?: 0, MAX_PERSISTED_REFRESH_STATES)
    return (0 until length)
        .mapNotNull { index -> array?.optJSONObject(index)?.toIdAndState() }
        .toMap()
}

private fun JSONObject.toIdAndState(): Pair<FeedId, FeedRefreshState>? =
    optString("feedId").takeIf(String::isNotBlank)?.let { id -> FeedId(id) to toRefreshState() }

private fun JSONObject.toRefreshState(): FeedRefreshState {
    val validators =
        FeedValidators(
            etag = optString("etag").takeIf(::isPersistableValidator),
            lastModified = optString("lastModified").takeIf(::isPersistableValidator),
        ).takeIf { it != FeedValidators() }
    return FeedRefreshState(
        lastAttemptAtEpochMillis = optPositiveLong("lastAttemptAt"),
        lastSuccessAtEpochMillis = optPositiveLong("lastSuccessAt"),
        consecutiveFailures = optInt("failures", 0).coerceIn(0, MAX_PERSISTED_CONSECUTIVE_FAILURES),
        lastFailure = FeedRefreshFailure.entries.firstOrNull { it.name == optString("lastFailure") },
        validators = validators,
    )
}

private fun isPersistableValidator(value: String): Boolean =
    value.isNotBlank() && value.length <= MAX_PERSISTED_VALIDATOR_LENGTH

private fun JSONObject.optPositiveLong(key: String): Long? = if (has(key)) optLong(key, -1).takeIf { it >= 0 } else null

internal fun encodeBackgroundRun(record: FeedBackgroundRunRecord): JSONObject =
    JSONObject().put("at", record.atEpochMillis).put("kind", record.kind.name)

internal fun decodeBackgroundRun(json: JSONObject?): FeedBackgroundRunRecord? {
    val at = json?.optLong("at", -1)?.takeIf { it >= 0 }
    val kind = FeedBackgroundRunRecord.Kind.entries.firstOrNull { it.name == json?.optString("kind") }
    return if (at != null && kind != null) FeedBackgroundRunRecord(at, kind) else null
}

/**
 * Adopts [persisted] entries that are newer than what [known] holds, e.g. written by the background worker's
 * coordinator or by a previous process. Mutates and returns [known].
 */
internal fun adoptNewerStates(
    known: MutableMap<FeedId, FeedRefreshState>,
    persisted: Map<FeedId, FeedRefreshState>,
): MutableMap<FeedId, FeedRefreshState> {
    persisted.forEach { (id, state) ->
        val current = known[id]
        if (current == null || (state.lastAttemptAtEpochMillis ?: 0) > (current.lastAttemptAtEpochMillis ?: 0)) {
            known[id] = state
        }
    }
    return known
}
