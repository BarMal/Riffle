package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val SHA_256_HEX_LENGTH = 64
const val DEFAULT_FEED_ITEM_LIMIT = 50
const val MAX_FEED_INPUT_ITEMS = 500
const val MAX_FEED_ITEM_LIMIT = 100
const val MAX_FEED_TITLE_LENGTH = 256
const val MAX_FEED_AUTHOR_LENGTH = 256
const val MAX_FEED_SUMMARY_LENGTH = 4096
const val MAX_FEED_SOURCE_ID_LENGTH = 512
const val MAX_FEED_URL_LENGTH = 2048

@JvmInline
value class FeedId(val value: String) {
    init {
        require(value.isNotBlank()) { "Feed ids must not be blank." }
    }
}

/** A normalized, explicitly configured HTTPS feed URL. */
class FeedUrl private constructor(val value: String) {
    companion object {
        fun parse(raw: String): Result<FeedUrl> =
            runCatching {
                val input = raw.trim()
                require(input.isNotEmpty()) { "Feed URLs must not be blank." }
                require(input.length <= MAX_FEED_URL_LENGTH) { "Feed URLs are too long." }
                val uri = URI(input)
                require(uri.scheme.equals("https", ignoreCase = true)) {
                    "Feed URLs must use HTTPS."
                }
                require(uri.userInfo == null) { "Feed URLs must not contain userinfo." }
                val host = uri.host?.lowercase() ?: error("Feed URL host is invalid.")
                require(uri.port == -1 || uri.port == 443) { "Feed URLs must use the default HTTPS port." }
                require(uri.fragment == null) { "Feed URLs must not contain fragments." }
                require(uri.rawAuthority != null) { "Feed URL authority is invalid." }

                val path = uri.rawPath.takeUnless(String::isEmpty) ?: "/"
                val query =
                    uri.rawQuery
                        ?.split('&')
                        ?.filter { parameter -> parameter.substringBefore('=').lowercase() !in TRACKING_QUERY_KEYS }
                        ?.joinToString("&")
                        ?.takeIf(String::isNotEmpty)
                val normalized =
                    buildString {
                        append("https://")
                        append(host)
                        append(path)
                        if (query != null) append('?').append(query)
                    }
                require(normalized.length <= MAX_FEED_URL_LENGTH) { "Feed URLs are too long." }
                FeedUrl(normalized)
            }

        private val TRACKING_QUERY_KEYS =
            setOf(
                "fbclid",
                "gclid",
                "dclid",
                "msclkid",
                "ref",
                "ref_src",
                "utm_campaign",
                "utm_content",
                "utm_medium",
                "utm_source",
                "utm_term",
            )
    }
}

enum class FeedRefreshIntent {
    MANUAL,
    ALLOW_SCHEDULED,
}

enum class FeedProfileStatus {
    AVAILABLE,
    LOCKED,
    REMOVED,
}

enum class FeedAvailability {
    ENABLED,
    DISABLED,
    PROFILE_LOCKED,
    PROFILE_REMOVED,
}

data class FeedConfiguration(
    val id: FeedId,
    val url: FeedUrl,
    val profile: AppProfile = AppProfile.personal(),
    val enabled: Boolean = true,
    val refreshIntent: FeedRefreshIntent = FeedRefreshIntent.MANUAL,
) {
    fun availability(profileStatuses: Map<AppProfileId, FeedProfileStatus>): FeedAvailability =
        when {
            !enabled -> FeedAvailability.DISABLED
            profileStatuses[profile.id] == FeedProfileStatus.LOCKED -> FeedAvailability.PROFILE_LOCKED
            profileStatuses[profile.id] == FeedProfileStatus.REMOVED -> FeedAvailability.PROFILE_REMOVED
            else -> FeedAvailability.ENABLED
        }

    /** The URL is deliberately used only as input to an opaque persisted identity. */
    fun identity(): String = sha256Hex("feed|${profile.id.value}|${url.value}")
}

enum class FeedFormat {
    RSS_2,
    ATOM,
}

/** Untrusted fields supplied by an RSS/Atom adapter before domain normalization. */
data class FeedItemInput(
    val sourceId: String? = null,
    val canonicalUrl: String? = null,
    val title: String? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val summary: String? = null,
    val imageUrl: String? = null,
)

data class FeedItem(
    val identity: String,
    val canonicalUrl: String?,
    val title: String,
    val author: String?,
    val publishedAt: Instant?,
    val summary: String?,
    val imageUrl: String?,
    val sourceOrder: Int,
)

data class NormalizedFeed(
    val format: FeedFormat,
    val title: String,
    val items: List<FeedItem>,
)

object FeedItemNormalizer {
    fun normalize(
        format: FeedFormat,
        feedTitle: String?,
        items: List<FeedItemInput>,
        maxItems: Int = DEFAULT_FEED_ITEM_LIMIT,
    ): NormalizedFeed {
        require(maxItems > 0) { "Feed item limit must be positive." }
        val boundedMaxItems = maxItems.coerceAtMost(MAX_FEED_ITEM_LIMIT)
        val normalized =
            items
                .take(MAX_FEED_INPUT_ITEMS)
                .mapIndexedNotNull { index, input -> normalizeItem(input, index) }
        val deduplicated =
            normalized
                .groupBy(FeedItem::identity)
                .values
                .map { duplicates -> duplicates.minWith(itemDuplicateOrder) }
        return NormalizedFeed(
            format = format,
            title = normalizeText(feedTitle, MAX_FEED_TITLE_LENGTH).orEmpty(),
            items = deduplicated.sortedWith(itemDisplayOrder).take(boundedMaxItems),
        )
    }

    private fun normalizeItem(
        input: FeedItemInput,
        sourceOrder: Int,
    ): FeedItem? {
        val title = normalizeText(input.title, MAX_FEED_TITLE_LENGTH) ?: return null
        val canonicalUrl = normalizeOptionalUrl(input.canonicalUrl)
        val sourceId = normalizeText(input.sourceId, MAX_FEED_SOURCE_ID_LENGTH)
        val author = normalizeText(input.author, MAX_FEED_AUTHOR_LENGTH)
        val summary = normalizeText(input.summary, MAX_FEED_SUMMARY_LENGTH)
        val imageUrl = normalizeOptionalUrl(input.imageUrl)
        val publishedAt = parseDate(input.publishedAt)
        val identity =
            sourceId ?: canonicalUrl ?: sha256Hex(
                listOf(title, author.orEmpty(), publishedAt?.toString().orEmpty()).joinToString("|"),
            )
        return FeedItem(identity, canonicalUrl, title, author, publishedAt, summary, imageUrl, sourceOrder)
    }

    private fun normalizeOptionalUrl(raw: String?): String? =
        raw?.trim()?.let { value ->
            FeedUrl.parse(value).getOrNull()?.value
        }

    private fun parseDate(raw: String?): Instant? =
        raw?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
                ?: runCatching { DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from) }.getOrNull()
        }

    private fun normalizeText(
        raw: String?,
        maxLength: Int,
    ): String? =
        raw
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() && value.length <= maxLength }

    private val itemDuplicateOrder =
        compareBy<FeedItem> { it.sourceOrder }
            .thenByDescending { it.publishedAt ?: Instant.MIN }
            .thenBy { it.title }

    private val itemDisplayOrder =
        Comparator<FeedItem> { left, right ->
            when {
                left.publishedAt != null && right.publishedAt != null ->
                    compareValuesBy(right, left, FeedItem::publishedAt, FeedItem::identity)
                left.publishedAt != null -> -1
                right.publishedAt != null -> 1
                else -> left.sourceOrder.compareTo(right.sourceOrder)
            }
        }

    private val WHITESPACE = Regex("\\s+")
}

/** Fixed-length opaque key for persisted read/dismiss intent. */
@JvmInline
value class FeedItemIntentDigest private constructor(val value: String) {
    init {
        require(
            value.length == SHA_256_HEX_LENGTH &&
                value.all { character ->
                    character in '0'..'9' || character in 'a'..'f'
                },
        ) {
            "Feed item intent digests must be lowercase SHA-256 hex."
        }
    }

    companion object {
        fun forItem(
            feed: FeedConfiguration,
            item: FeedItem,
        ): FeedItemIntentDigest = FeedItemIntentDigest(sha256Hex("item|${feed.identity()}|${item.identity}"))
    }
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
