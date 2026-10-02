package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedUrls
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsInstanceOverride
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsLimits
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Versioned JSON for the two device-local ICS stores. Neither document is ever part of the launcher backup
 * or Auto Backup (feed URLs can carry secret tokens): the settings document holds the URLs, the cache document
 * holds parsed events and no URL. Decoding is defensive: an unknown version or unreadable document reads as
 * nothing stored, an unreadable entry is dropped, and every URL is validated again (https, public host).
 */
internal object IcsJsonCodecs {
    const val CURRENT_SETTINGS_VERSION = 1
    const val CURRENT_CACHE_VERSION = 1

    /** Bounds the decoder applies to a stored cache, whatever the document claims. */
    const val MAX_CACHED_FEEDS = IcsFeedSettings.MAX_ICS_FEEDS
    const val MAX_CACHED_EVENTS_PER_FEED = 1_000

    fun encodeSettings(settings: IcsFeedSettings): String =
        JSONObject()
            .put("version", CURRENT_SETTINGS_VERSION)
            .put(
                "feeds",
                JSONArray(
                    settings.feeds.map { feed ->
                        JSONObject()
                            .put("id", feed.id.value)
                            .put("name", feed.name)
                            .put("url", feed.url.value)
                            .put("enabled", feed.enabled)
                    },
                ),
            ).toString()

    fun decodeSettings(text: String): IcsFeedSettings? =
        runCatching {
            val json = JSONObject(JSONTokener(text))
            require(json.optInt("version", -1) == CURRENT_SETTINGS_VERSION)
            val array = json.optJSONArray("feeds") ?: JSONArray()
            val feeds =
                (0 until array.length())
                    .mapNotNull { index -> runCatching { decodeFeed(array.getJSONObject(index)) }.getOrNull() }
                    .distinctBy { it.id }
                    .distinctBy { it.url }
                    .take(IcsFeedSettings.MAX_ICS_FEEDS)
            IcsFeedSettings(feeds)
        }.getOrNull()

    private fun decodeFeed(json: JSONObject): IcsFeed? {
        val raw = json.getString("url")
        val url: FeedUrl? = IcsFeedUrls.parse(raw).getOrNull()?.takeIf { IcsFeedUrls.problemWith(raw) == null }
        return url?.let {
            IcsFeed(
                id = IcsFeedId(json.getString("id")),
                name = IcsFeedSettings.displayName(json.optString("name", ""), it),
                url = it,
                enabled = json.optBoolean("enabled", true),
            )
        }
    }

    fun encodeCache(cache: Map<IcsFeedId, CachedIcsFeed>): String =
        JSONObject()
            .put("version", CURRENT_CACHE_VERSION)
            .put(
                "feeds",
                JSONArray(
                    cache.map { (id, feed) ->
                        JSONObject()
                            .put("id", id.value)
                            .put("at", feed.fetchedAtEpochMillis)
                            .put("events", JSONArray(feed.events.map(IcsEventJson::encode)))
                    },
                ),
            ).toString()

    fun decodeCache(text: String): Map<IcsFeedId, CachedIcsFeed>? =
        runCatching {
            val json = JSONObject(JSONTokener(text))
            require(json.optInt("version", -1) == CURRENT_CACHE_VERSION)
            val array = json.optJSONArray("feeds") ?: JSONArray()
            (0 until array.length())
                .take(MAX_CACHED_FEEDS)
                .mapNotNull { index -> runCatching { decodeCachedFeed(array.getJSONObject(index)) }.getOrNull() }
                .toMap()
        }.getOrNull()

    private fun decodeCachedFeed(json: JSONObject): Pair<IcsFeedId, CachedIcsFeed> {
        val events = json.optJSONArray("events") ?: JSONArray()
        val decoded =
            (0 until events.length())
                .take(MAX_CACHED_EVENTS_PER_FEED)
                .mapNotNull { index -> runCatching { IcsEventJson.decode(events.getJSONObject(index)) }.getOrNull() }
        return IcsFeedId(json.getString("id")) to CachedIcsFeed(decoded, json.getLong("at"))
    }
}

/** The JSON of one cached event, with the bounds [IcsLimits] puts on feed text applied again on decode. */
internal object IcsEventJson {
    fun encode(event: IcsEvent): JSONObject =
        JSONObject()
            .put("u", event.uid)
            .put("t", event.title)
            .put("l", event.location ?: JSONObject.NULL)
            .put("s", event.start.toString())
            .put("z", event.zone?.id ?: JSONObject.NULL)
            .put("a", event.allDay)
            .put("d", event.duration.seconds)
            .put("r", event.rrule ?: JSONObject.NULL)
            .put("x", JSONArray(event.exDates.map(LocalDateTime::toString)))
            .put("y", JSONArray(event.rDates.map(LocalDateTime::toString)))
            .put("o", JSONArray(event.overrides.map(::encodeOverride)))
            .put("p", event.isPrivate)

    fun decode(json: JSONObject): IcsEvent =
        IcsEvent(
            uid = json.getString("u"),
            title = json.getString("t").take(IcsLimits.MAX_TEXT_CHARS),
            location = json.optStringOrNull("l")?.take(IcsLimits.MAX_TEXT_CHARS),
            start = LocalDateTime.parse(json.getString("s")),
            zone = json.optStringOrNull("z")?.let { ZoneId.of(it) },
            allDay = json.getBoolean("a"),
            duration = durationOf(json.getLong("d")),
            rrule = json.optStringOrNull("r")?.takeIf { it.length <= IcsLimits.MAX_RRULE_CHARS },
            exDates = json.dates("x", IcsLimits.MAX_EX_DATES),
            rDates = json.dates("y", IcsLimits.MAX_R_DATES),
            overrides = json.overrides(),
            isPrivate = json.optBoolean("p", false),
        )

    private fun encodeOverride(override: IcsInstanceOverride): JSONObject =
        JSONObject()
            .put("i", override.recurrenceId.toString())
            .put("c", override.cancelled)
            .put("s", override.start?.toString() ?: JSONObject.NULL)
            .put("d", override.duration?.seconds ?: JSONObject.NULL)
            .put("t", override.title ?: JSONObject.NULL)
            .put("l", override.location ?: JSONObject.NULL)

    private fun JSONObject.overrides(): List<IcsInstanceOverride> {
        val array = optJSONArray("o") ?: return emptyList()
        return (0 until array.length()).take(IcsLimits.MAX_OVERRIDES).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                IcsInstanceOverride(
                    recurrenceId = LocalDateTime.parse(item.getString("i")),
                    cancelled = item.optBoolean("c", false),
                    start = item.optStringOrNull("s")?.let(LocalDateTime::parse),
                    duration = if (item.has("d") && !item.isNull("d")) durationOf(item.getLong("d")) else null,
                    title = item.optStringOrNull("t")?.take(IcsLimits.MAX_TEXT_CHARS),
                    location = item.optStringOrNull("l")?.take(IcsLimits.MAX_TEXT_CHARS),
                )
            }.getOrNull()
        }
    }

    private fun JSONObject.dates(
        key: String,
        max: Int,
    ): Set<LocalDateTime> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .take(max)
            .mapNotNull { index -> runCatching { LocalDateTime.parse(array.getString(index)) }.getOrNull() }
            .toSet()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun durationOf(seconds: Long): Duration =
        Duration.ofSeconds(seconds.coerceIn(0L, Duration.ofDays(IcsLimits.MAX_DURATION_DAYS).seconds))
}
