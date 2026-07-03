package com.riffle.app.launcher

import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal fun List<InstalledWidgetProvider>.filteredWidgetProviders(query: String): List<InstalledWidgetProvider> =
    query.normalizedWidgetSearchTokens()
        .takeIf { tokens -> tokens.isNotEmpty() }
        ?.let { queryTokens ->
            filter { provider -> provider.matchesWidgetQuery(queryTokens) }
        }
        ?: this

private fun InstalledWidgetProvider.matchesWidgetQuery(queryTokens: List<String>): Boolean =
    widgetSearchableValues().let { values ->
        values.matchesAll(queryTokens) || values.map(String::widgetAcronym).matchesAll(queryTokens)
    }

private fun InstalledWidgetProvider.widgetSearchableValues(): List<String> =
    listOfNotNull(
        label,
        identity.packageName.value,
        identity.className.value,
        identity.profile.drawerProfilePrefix(),
        "${dimensions.minWidthDp}x${dimensions.minHeightDp}",
    ).map { value -> value.lowercase() }

private fun String.normalizedWidgetSearchTokens(): List<String> =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

private fun List<String>.matchesAll(queryTokens: List<String>): Boolean =
    queryTokens.all { queryToken -> any { value -> value.contains(queryToken) } }

private fun String.widgetAcronym(): String =
    split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString(separator = "") { token -> token.first().toString() }
