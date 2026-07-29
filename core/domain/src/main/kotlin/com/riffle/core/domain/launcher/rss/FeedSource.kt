package com.riffle.core.domain.launcher.rss

/** Conditional request metadata retained by the cache boundary, never exposed to the renderer. */
data class FeedValidators(
    val etag: String? = null,
    val lastModified: String? = null,
)

enum class FeedRefreshTrigger {
    USER,
    SCHEDULED,
}

data class FeedFetchRequest(
    val configuration: FeedConfiguration,
    val validators: FeedValidators? = null,
    val trigger: FeedRefreshTrigger = FeedRefreshTrigger.USER,
)

enum class FeedSourceError {
    INVALID_REDIRECT,
    REDIRECT_LIMIT,
    TIMEOUT,
    RESPONSE_TOO_LARGE,
    INVALID_ENCODING,
    NETWORK,
    HTTP,
}

sealed interface FeedTransportResult {
    data class Content(
        val body: String,
        val validators: FeedValidators,
    ) : FeedTransportResult

    data class NotModified(
        val validators: FeedValidators,
    ) : FeedTransportResult

    data class Failure(
        val error: FeedSourceError,
    ) : FeedTransportResult
}

/** Platform transport contract; implementations must not expose response bodies on failures. */
fun interface FeedTransport {
    fun fetch(request: FeedFetchRequest): FeedTransportResult
}

fun interface FeedParser {
    fun parse(body: String): Result<NormalizedFeed>
}

data class FeedRefreshConstraints(
    val meteredNetwork: Boolean,
    val batterySaver: Boolean,
)

enum class FeedRefreshDecision {
    ALLOWED,
    SUPPRESSED_METERED_NETWORK,
    SUPPRESSED_BATTERY_SAVER,
}

interface FeedRefreshPolicy {
    fun decide(
        trigger: FeedRefreshTrigger,
        constraints: FeedRefreshConstraints,
    ): FeedRefreshDecision
}

object DefaultFeedRefreshPolicy : FeedRefreshPolicy {
    override fun decide(
        trigger: FeedRefreshTrigger,
        constraints: FeedRefreshConstraints,
    ): FeedRefreshDecision =
        when {
            trigger == FeedRefreshTrigger.USER -> FeedRefreshDecision.ALLOWED
            constraints.meteredNetwork -> FeedRefreshDecision.SUPPRESSED_METERED_NETWORK
            constraints.batterySaver -> FeedRefreshDecision.SUPPRESSED_BATTERY_SAVER
            else -> FeedRefreshDecision.ALLOWED
        }
}
