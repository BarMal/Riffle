package com.riffle.app.launcher.rss

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Explicit "Open in browser" hand-off for a feed article's validated HTTPS [canonicalUrl] from
 * [CachedFeedArticle]. Per ADR 0001, opening an article detail never embeds a third-party page or
 * silently launches an app -- it is always this one explicit, user-initiated action.
 */
interface FeedArticleBrowserLauncher {
    /** Returns true when an activity was successfully started for [url]. */
    fun launch(url: String): Boolean
}

/** Mirrors [com.riffle.app.launcher.AndroidWebSearchLauncher]'s validate-then-launch shape. */
class AndroidFeedArticleBrowserLauncher(
    private val context: Context,
) : FeedArticleBrowserLauncher {
    override fun launch(url: String): Boolean =
        launchFeedArticleBrowser(
            url = url,
            isAvailable = { intent -> intent.resolveActivity(context.packageManager) != null },
            launch = context::startActivity,
        )
}

@Suppress("ReturnCount")
internal fun launchFeedArticleBrowser(
    url: String,
    isAvailable: (Intent) -> Boolean,
    launch: (Intent) -> Unit,
): Boolean {
    if (!isHttpsFeedArticleUrl(url)) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!isAvailable(intent)) return false
    return runCatching { launch(intent) }.isSuccess
}

/**
 * Pure, Android-free scheme validation kept separate from [launchFeedArticleBrowser] so it can run
 * under a plain JVM unit test rather than only an instrumented one. Only ever offers to open an
 * `https` URL, per ADR 0001.
 */
internal fun isHttpsFeedArticleUrl(url: String): Boolean =
    runCatching { java.net.URI(url) }
        .getOrNull()
        ?.scheme
        ?.equals("https", ignoreCase = true) == true

/** Safe default for previews and callers that have not wired a platform launcher. */
object NoOpFeedArticleBrowserLauncher : FeedArticleBrowserLauncher {
    override fun launch(url: String): Boolean = false
}
