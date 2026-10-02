package com.riffle.core.domain.launcher.workspace.sources.ics

import com.riffle.core.domain.launcher.rss.FeedHostSafety
import com.riffle.core.domain.launcher.rss.FeedUrl
import java.net.URI
import java.util.UUID

@JvmInline
value class IcsFeedId(val value: String) {
    init {
        require(value.isNotBlank()) { "ICS feed ids must not be blank." }
    }
}

/**
 * A subscribed calendar feed. The URL may carry a secret token, so it is never shown (only [host]), never
 * logged ([toString] redacts it) and never backed up: these settings live in their own device-local store.
 */
data class IcsFeed(
    val id: IcsFeedId,
    val name: String,
    val url: FeedUrl,
    val enabled: Boolean = true,
) {
    /** The only part of the URL that may be displayed. */
    val host: String
        get() = runCatching { URI(url.value).host }.getOrNull()?.removePrefix("www.").orEmpty()

    override fun toString(): String = "IcsFeed(id=${id.value}, enabled=$enabled)"
}

/** Why a URL cannot be subscribed to; never echoes the input. */
enum class IcsFeedUrlProblem {
    NOT_A_VALID_HTTPS_URL,
    NOT_A_PUBLIC_HOST,
}

/** Validation for a typed feed URL: https only, no credentials, public host (literal check only). */
object IcsFeedUrls {
    /** `webcal://` is the common way calendar apps share a feed; it means https here. */
    fun parse(raw: String): Result<FeedUrl> {
        val trimmed = raw.trim()
        val input =
            if (trimmed.startsWith(WEBCAL, ignoreCase = true)) "https://" + trimmed.drop(WEBCAL.length) else trimmed
        return FeedUrl.parse(input)
    }

    fun problemWith(raw: String): IcsFeedUrlProblem? {
        val url = parse(raw).getOrNull() ?: return IcsFeedUrlProblem.NOT_A_VALID_HTTPS_URL
        val host = runCatching { URI(url.value).host }.getOrNull().orEmpty()
        return if (FeedHostSafety.isPublicHost(host)) null else IcsFeedUrlProblem.NOT_A_PUBLIC_HOST
    }

    private const val WEBCAL = "webcal://"
}

/** The subscribed feeds. Default: none, so the source shows nothing and fetches nothing. */
data class IcsFeedSettings(
    val feeds: List<IcsFeed> = emptyList(),
) {
    override fun toString(): String = "IcsFeedSettings(feeds=${feeds.size})"

    /** Adds an enabled feed; null when [rawUrl] is invalid, a duplicate, or the list is full. */
    fun withAdded(
        rawName: String,
        rawUrl: String,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): IcsFeedSettings? {
        val url = IcsFeedUrls.parse(rawUrl).getOrNull()
        val unusable =
            url == null || IcsFeedUrls.problemWith(rawUrl) != null ||
                feeds.size >= MAX_ICS_FEEDS || feeds.any { it.url == url }
        if (unusable || url == null) return null
        val feed = IcsFeed(IcsFeedId(newId()), displayName(rawName, url), url)
        return copy(feeds = feeds + feed)
    }

    fun withoutFeed(id: IcsFeedId): IcsFeedSettings = copy(feeds = feeds.filterNot { it.id == id })

    fun withEnabled(
        id: IcsFeedId,
        enabled: Boolean,
    ): IcsFeedSettings = copy(feeds = feeds.map { if (it.id == id) it.copy(enabled = enabled) else it })

    val enabledFeeds: List<IcsFeed> get() = feeds.filter(IcsFeed::enabled)

    companion object {
        const val MAX_ICS_FEEDS = 10
        const val MAX_NAME_LENGTH = 40
        private val WHITESPACE = Regex("\\s+")

        /** The typed name (control characters and extra spaces removed, cut), or the host when it is blank. */
        fun displayName(
            rawName: String,
            url: FeedUrl,
        ): String {
            val cleaned = rawName.filterNot(Char::isISOControl).replace(WHITESPACE, " ").trim().take(MAX_NAME_LENGTH)
            val host = runCatching { URI(url.value).host }.getOrNull()?.removePrefix("www.")
            return cleaned.ifEmpty { host ?: "Calendar" }
        }
    }
}
