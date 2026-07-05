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

private fun Int.widgetCountLabel(): String = "$this ${if (this == 1) "widget" else "widgets"}"
