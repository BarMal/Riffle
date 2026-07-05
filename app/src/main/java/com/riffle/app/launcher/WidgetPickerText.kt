package com.riffle.app.launcher

internal fun widgetPickerResultSummaryText(
    totalProviderCount: Int,
    resultCount: Int,
    query: String,
): String =
    when {
        query.isNotBlank() ->
            "${resultCount.widgetCountLabel()} matching, ${totalProviderCount.widgetCountLabel()} total"

        else -> "${totalProviderCount.widgetCountLabel()} available"
    }

internal fun widgetPickerEmptyMessageText(
    totalProviderCount: Int,
    query: String,
): String =
    when {
        totalProviderCount == 0 -> "No widgets available"
        query.isNotBlank() -> "No widgets found for \"${query.trim()}\""
        else -> "No matching widgets"
    }

private fun Int.widgetCountLabel(): String = "$this ${if (this == 1) "widget" else "widgets"}"
