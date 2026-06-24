package com.riffle.core.domain.launcher.home

class WidgetEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun addWidgetToSelectedPage(
        layout: HomeLayout,
        hostedWidgetId: HostedWidgetId,
        label: String,
    ): WidgetEditResult =
        when (
            val result =
                gridPlacementEngine.placeItemInFirstAvailableCell(
                    page = layout.selectedPage,
                    item =
                        WidgetItem(
                            id = LauncherItemId("widget:${hostedWidgetId.value}"),
                            appWidgetId = hostedWidgetId,
                            label = label.ifBlank { DEFAULT_WIDGET_LABEL },
                        ),
                )
        ) {
            is PlaceLauncherItemResult.Placed ->
                WidgetEditResult.Updated(layout.withUpdatedSelectedPage(result.page))

            is PlaceLauncherItemResult.Rejected ->
                WidgetEditResult.Rejected(result.reason)
        }

    fun resizeWidgetOnSelectedPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
        span: GridSpan,
    ): WidgetEditResult =
        when (
            val result =
                gridPlacementEngine.resizeItem(
                    page = layout.selectedPage,
                    itemId = itemId,
                    span = span.coerceAtLeastOneCell(),
                )
        ) {
            is PlaceLauncherItemResult.Placed ->
                WidgetEditResult.Updated(layout.withUpdatedSelectedPage(result.page))

            is PlaceLauncherItemResult.Rejected ->
                WidgetEditResult.Rejected(result.reason)
        }

    private fun HomeLayout.withUpdatedSelectedPage(page: LauncherPage): HomeLayout =
        copy(
            pages =
                pages.map { existingPage ->
                    when (existingPage.id) {
                        page.id -> page
                        else -> existingPage
                    }
                },
        )
}

private fun GridSpan.coerceAtLeastOneCell(): GridSpan =
    GridSpan(
        columns = columns.coerceAtLeast(1),
        rows = rows.coerceAtLeast(1),
    )

sealed interface WidgetEditResult {
    data class Updated(val layout: HomeLayout) : WidgetEditResult

    data class Rejected(val reason: PlacementRejectionReason) : WidgetEditResult
}

private const val DEFAULT_WIDGET_LABEL = "Widget"
