package com.riffle.core.domain.launcher.home

class WidgetEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun addWidgetToSelectedPage(
        layout: HomeLayout,
        hostedWidgetId: HostedWidgetId,
        label: String,
        preferredSpan: GridSpan = GridSpan(),
        resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
        targetCell: GridCell? = null,
    ): WidgetEditResult =
        when {
            layout.selectedPage.type is LauncherPageType.Generated ->
                WidgetEditResult.Rejected(PlacementRejectionReason.GENERATED_PAGE)

            layout.hasHostedWidget(hostedWidgetId) ->
                WidgetEditResult.Rejected(PlacementRejectionReason.DUPLICATE_ITEM_ID)

            else ->
                WidgetItem(
                    id = LauncherItemId("widget:${hostedWidgetId.value}"),
                    appWidgetId = hostedWidgetId,
                    label = label.ifBlank { DEFAULT_WIDGET_LABEL },
                    resizeConstraints = resizeConstraints,
                ).let { widget ->
                    preferredSpan
                        .placementCandidates(resizeConstraints)
                        .map { span ->
                            span to
                                if (targetCell == null) {
                                    gridPlacementEngine.placeItemInFirstAvailableCell(
                                        page = layout.selectedPage,
                                        item = widget,
                                        span = span,
                                    )
                                } else {
                                    gridPlacementEngine.placeItem(
                                        page = layout.selectedPage,
                                        item =
                                            widget.withPlacement(
                                                GridPlacement(cell = targetCell, span = span),
                                            ),
                                    )
                                }
                        }
                        .firstOrNull { (_, result) -> result is PlaceLauncherItemResult.Placed }
                        ?.let { (span, result) ->
                            WidgetEditResult.Updated(
                                layout =
                                    layout.withUpdatedSelectedPage(
                                        (result as PlaceLauncherItemResult.Placed).page,
                                    ),
                                placedSpan = span,
                            )
                        }
                        ?: WidgetEditResult.Rejected(PlacementRejectionReason.NO_AVAILABLE_CELL)
                }
        }

    fun resizeWidgetOnSelectedPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
        span: GridSpan,
    ): WidgetEditResult =
        when (val widget = layout.selectedPage.items.firstOrNull { item -> item.id == itemId }) {
            null, !is WidgetItem -> WidgetEditResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)

            else ->
                if (!widget.resizeConstraints.permits(span.coerceAtLeastOneCell())) {
                    WidgetEditResult.Rejected(PlacementRejectionReason.OUT_OF_BOUNDS)
                } else {
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
                }
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

private fun HomeLayout.hasHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
    (
        pages
            .flatMap { page -> page.items } + dock.items
    )
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }

private fun GridSpan.placementCandidates(resizeConstraints: WidgetResizeConstraints): List<GridSpan> =
    coerceAtLeastOneCell().coerceAtLeast(resizeConstraints.minSpan).let { preferredSpan ->
        (preferredSpan.columns downTo 1).flatMap { columns ->
            (preferredSpan.rows downTo 1).map { rows ->
                GridSpan(columns = columns, rows = rows)
            }
        }.distinct()
            .filter(resizeConstraints::permits)
            .sortedWith(
                compareBy<GridSpan> { span -> preferredSpan.area - span.area }
                    .thenBy { span -> preferredSpan.columns - span.columns }
                    .thenBy { span -> preferredSpan.rows - span.rows },
            )
    }

private fun GridSpan.coerceAtLeast(minimum: GridSpan): GridSpan =
    GridSpan(
        columns = maxOf(columns, minimum.columns),
        rows = maxOf(rows, minimum.rows),
    )

private val GridSpan.area: Int
    get() = columns * rows

sealed interface WidgetEditResult {
    data class Updated(
        val layout: HomeLayout,
        val placedSpan: GridSpan? = null,
    ) : WidgetEditResult

    data class Rejected(val reason: PlacementRejectionReason) : WidgetEditResult
}

private const val DEFAULT_WIDGET_LABEL = "Widget"
