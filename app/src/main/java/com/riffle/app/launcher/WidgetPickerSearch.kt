package com.riffle.app.launcher

import com.riffle.core.domain.launcher.search.containsAllSearchTokens
import com.riffle.core.domain.launcher.search.normalizedSearchTokens
import com.riffle.core.domain.launcher.search.searchAcronym
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions

internal fun List<InstalledWidgetProvider>.filteredWidgetProviders(query: String): List<InstalledWidgetProvider> =
    normalizedSearchTokens(query)
        .takeIf { tokens -> tokens.isNotEmpty() }
        ?.let { queryTokens ->
            filter { provider -> provider.matchesWidgetQuery(queryTokens) }
        }
        ?: this

private fun InstalledWidgetProvider.matchesWidgetQuery(queryTokens: List<String>): Boolean =
    widgetSearchableValues().let { values ->
        values.containsAllSearchTokens(queryTokens) ||
            values.map(String::searchAcronym).containsAllSearchTokens(queryTokens)
    }

private fun InstalledWidgetProvider.widgetSearchableValues(): List<String> =
    listOfNotNull(
        label,
        identity.packageName.value,
        identity.className.value,
        identity.profile.drawerProfilePrefix(),
        "${dimensions.minWidthDp}x${dimensions.minHeightDp}",
        "${dimensions.minWidthDp}x${dimensions.minHeightDp}dp",
        dimensions.targetCellSizeSearchToken(),
        widgetPickerResizeLabel(),
    ).map { value -> value.lowercase() }

private fun WidgetProviderDimensions.targetCellSizeSearchToken(): String? =
    listOfNotNull(targetCellWidth, targetCellHeight)
        .takeIf { cells -> cells.size == 2 }
        ?.joinToString(separator = "x")
