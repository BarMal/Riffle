package com.riffle.core.domain.launcher.rss

/** Why a refresh of one feed failed; deliberately coarse so it can be shown without echoing URLs or content. */
enum class FeedRefreshFailure {
    NETWORK,
    TIMEOUT,
    BAD_STATUS,
    OVERSIZE,
    MALFORMED,
    UNSAFE_URL,
}

/** The result of refreshing one feed. Never carries article content or URLs. */
sealed interface FeedRefreshOutcome {
    /** The feed was fetched, parsed and stored; [newArticles] counts articles not previously cached. */
    data class Updated(val newArticles: Int, val totalArticles: Int) : FeedRefreshOutcome

    data object NotModified : FeedRefreshOutcome

    data class Failed(val reason: FeedRefreshFailure) : FeedRefreshOutcome
}

/** Why a feed was left out of a refresh plan without any network request. */
enum class FeedRefreshSkip {
    DISABLED,
    PROFILE_LOCKED,
    PROFILE_REMOVED,
    UNSAFE_URL,
    TOO_SOON,
    BACKING_OFF,
}

/** What the user asked to refresh. */
sealed interface FeedRefreshScope {
    data object All : FeedRefreshScope

    data class One(val feedId: FeedId) : FeedRefreshScope
}

/** Per-feed refresh bookkeeping kept outside the article cache; never persisted with article content. */
data class FeedRefreshState(
    val lastAttemptAtEpochMillis: Long? = null,
    val lastSuccessAtEpochMillis: Long? = null,
    val consecutiveFailures: Int = 0,
    val lastFailure: FeedRefreshFailure? = null,
    val validators: FeedValidators? = null,
)

fun FeedSourceError.toRefreshFailure(): FeedRefreshFailure =
    when (this) {
        FeedSourceError.INVALID_REDIRECT, FeedSourceError.REDIRECT_LIMIT -> FeedRefreshFailure.UNSAFE_URL
        FeedSourceError.TIMEOUT -> FeedRefreshFailure.TIMEOUT
        FeedSourceError.RESPONSE_TOO_LARGE -> FeedRefreshFailure.OVERSIZE
        FeedSourceError.INVALID_ENCODING -> FeedRefreshFailure.MALFORMED
        FeedSourceError.NETWORK -> FeedRefreshFailure.NETWORK
        FeedSourceError.HTTP -> FeedRefreshFailure.BAD_STATUS
    }

/** Folds a finished attempt into the feed's [FeedRefreshState]; validators are kept on success and not-modified. */
fun FeedRefreshState.after(
    outcome: FeedRefreshOutcome,
    nowEpochMillis: Long,
    newValidators: FeedValidators?,
): FeedRefreshState =
    when (outcome) {
        is FeedRefreshOutcome.Failed ->
            copy(
                lastAttemptAtEpochMillis = nowEpochMillis,
                consecutiveFailures = consecutiveFailures + 1,
                lastFailure = outcome.reason,
            )
        else ->
            copy(
                lastAttemptAtEpochMillis = nowEpochMillis,
                lastSuccessAtEpochMillis = nowEpochMillis,
                consecutiveFailures = 0,
                lastFailure = null,
                validators = newValidators?.takeIf { it != FeedValidators() } ?: validators,
            )
    }
