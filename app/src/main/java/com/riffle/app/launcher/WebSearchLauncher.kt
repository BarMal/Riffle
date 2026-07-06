package com.riffle.app.launcher

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

class AndroidWebSearchLauncher(
    private val context: Context,
) {
    fun launch(query: String): Boolean =
        launchWebSearch(
            query = query,
            isAvailable = { intent -> intent.resolveActivity(context.packageManager) != null },
            launch = context::startActivity,
        )
}

internal fun launchWebSearch(
    query: String,
    isAvailable: (Intent) -> Boolean,
    launch: (Intent) -> Unit,
): Boolean {
    val trimmedQuery = query.trim()
    val intentSpec =
        if (trimmedQuery.isEmpty()) {
            null
        } else {
            val searchIntent = webSearchIntent(trimmedQuery)
            webSearchIntentSpec(
                query = trimmedQuery,
                hasWebSearchProvider = isAvailable(searchIntent),
            )
        }

    return intentSpec
        ?.let { spec ->
            runCatching {
                launch(spec.toIntent())
            }.isSuccess
        }
        ?: false
}

internal data class WebSearchIntentSpec(
    val action: String,
    val query: String? = null,
    val uriString: String? = null,
    val flags: Int = WEB_SEARCH_FLAGS,
)

internal fun webSearchIntentSpec(
    query: String,
    hasWebSearchProvider: Boolean,
): WebSearchIntentSpec? {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return null

    return if (hasWebSearchProvider) {
        WebSearchIntentSpec(
            action = WEB_SEARCH_ACTION,
            query = trimmedQuery,
        )
    } else {
        WebSearchIntentSpec(
            action = WEB_VIEW_ACTION,
            uriString = googleSearchUriString(trimmedQuery),
        )
    }
}

internal fun webSearchIntent(query: String): Intent =
    Intent(WEB_SEARCH_ACTION)
        .putExtra(SearchManager.QUERY, query)
        .addFlags(WEB_SEARCH_FLAGS)

internal fun googleWebSearchIntent(query: String): Intent =
    Intent(WEB_VIEW_ACTION)
        .setData(Uri.parse(googleSearchUriString(query)))
        .addFlags(WEB_SEARCH_FLAGS)

internal fun googleSearchUriString(query: String): String =
    "https://www.google.com/search?q=${URLEncoder.encode(query, Charsets.UTF_8.name())}"

private fun WebSearchIntentSpec.toIntent(): Intent =
    Intent(action)
        .apply {
            query?.let { value -> putExtra(SearchManager.QUERY, value) }
            uriString?.let { value -> setData(Uri.parse(value)) }
        }
        .addFlags(flags)

internal const val WEB_SEARCH_ACTION: String = Intent.ACTION_WEB_SEARCH
internal const val WEB_VIEW_ACTION: String = Intent.ACTION_VIEW
internal const val WEB_SEARCH_FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK
