package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainerValidationTest {
    private val flat = Lens(sources = listOf(SourceId("apps")))
    private val grouped = flat.copy(group = LensGroup.ByGroupKey)

    private fun widget(
        id: String,
        cols: Int = 1,
        rows: Int = 1,
        expression: ExpressionKind = ExpressionKind.LIST,
    ) = WidgetContainer(ContainerId(id), WidgetSpan(cols, rows), LensBinding(flat, expression))

    private fun grid(vararg placements: WidgetPlacement) =
        PageContainer(ContainerId("p"), PageContent.WidgetGrid(4, 4, placements.toList()))

    @Test
    fun validBoundPageHasNoIssues() {
        val page = PageContainer(ContainerId("p"), PageContent.Bound(LensBinding(flat, ExpressionKind.ICON_GRID)))
        assertTrue(ContainerValidation.validate(page).isEmpty())
    }

    @Test
    fun invalidPairingIsReported() {
        val page = PageContainer(ContainerId("p"), PageContent.Bound(LensBinding(flat, ExpressionKind.CATEGORIES)))
        assertTrue(ContainerValidation.validate(page).single() is ContainerIssue.InvalidPairing)
    }

    @Test
    fun pageSetNeedsGroupedLens() {
        val issues =
            ContainerValidation.validate(
                PageSetContainer(ContainerId("s"), LensBinding(flat, ExpressionKind.LIST)),
            )
        assertTrue(issues.any { it is ContainerIssue.PageSetNeedsGroupedLens })
    }

    @Test
    fun groupedPageSetWithVerticalExpressionIsValidAndOwnsPager() {
        val set = PageSetContainer(ContainerId("s"), LensBinding(grouped, ExpressionKind.INDEX))
        assertTrue(ContainerValidation.validate(set).isEmpty())
        assertTrue(GestureAxis.HORIZONTAL_PAGER in set.ownedAxes)
        assertTrue(GestureAxis.VERTICAL_SCROLL in set.ownedAxes)
    }

    @Test
    fun widgetsInAGridDetectBoundsAndOverlap() {
        val outOfBounds = grid(WidgetPlacement(widget("a", cols = 2), column = 3, row = 0))
        assertTrue(ContainerValidation.validate(outOfBounds).any { it is ContainerIssue.WidgetOutOfBounds })

        val overlapping =
            grid(
                WidgetPlacement(widget("a", 2, 2), 0, 0),
                WidgetPlacement(widget("b", 2, 2), 1, 1),
            )
        assertEquals(
            listOf(ContainerIssue.WidgetOverlap(ContainerId("b"), ContainerId("a"))),
            ContainerValidation.validate(overlapping),
        )

        val touching =
            grid(
                WidgetPlacement(widget("a", 2, 2), 0, 0),
                WidgetPlacement(widget("b", 2, 2), 2, 0),
            )
        assertTrue(ContainerValidation.validate(touching).isEmpty())
    }

    @Test
    fun duplicateWidgetIdsAreReported() {
        val page = grid(WidgetPlacement(widget("a"), 0, 0), WidgetPlacement(widget("a"), 1, 0))
        assertTrue(ContainerValidation.validate(page).any { it is ContainerIssue.DuplicateContainerId })
    }

    @Test
    fun finderMustBeBoundCategoriesOrAlphaList() {
        fun finder(
            expression: ExpressionKind,
            lens: Lens,
        ) = PageContainer(ContainerId("f"), PageContent.Bound(LensBinding(lens, expression)), PageRole.FINDER)
        assertTrue(ContainerValidation.validate(finder(ExpressionKind.CATEGORIES, grouped)).isEmpty())
        assertTrue(ContainerValidation.validate(finder(ExpressionKind.ALPHA_LIST, flat)).isEmpty())
        assertTrue(
            ContainerValidation.validate(finder(ExpressionKind.LIST, flat)).any { it is ContainerIssue.InvalidFinder },
        )
    }
}
