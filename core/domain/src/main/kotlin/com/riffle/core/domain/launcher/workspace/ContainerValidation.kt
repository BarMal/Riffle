package com.riffle.core.domain.launcher.workspace

sealed interface ContainerIssue {
    val containerId: ContainerId

    /** The hosted lens cannot be drawn by the chosen expression. */
    data class InvalidPairing(
        override val containerId: ContainerId,
        val issues: List<LensIssue>,
    ) : ContainerIssue

    /** A page-set expands groups into pages, so its lens must be grouped. */
    data class PageSetNeedsGroupedLens(override val containerId: ContainerId) : ContainerIssue

    /** The expression scrolls on an axis the page-set's pager already owns. */
    data class AxisConflict(
        override val containerId: ContainerId,
        val axis: GestureAxis,
    ) : ContainerIssue

    data class WidgetOutOfBounds(override val containerId: ContainerId) : ContainerIssue

    data class WidgetOverlap(
        override val containerId: ContainerId,
        val otherId: ContainerId,
    ) : ContainerIssue

    data class DuplicateContainerId(override val containerId: ContainerId) : ContainerIssue

    /** A Finder page must be a bound Categories or AlphaList page. */
    data class InvalidFinder(override val containerId: ContainerId) : ContainerIssue
}

object ContainerValidation {
    val FINDER_EXPRESSIONS: Set<ExpressionKind> = setOf(ExpressionKind.CATEGORIES, ExpressionKind.ALPHA_LIST)

    fun validate(
        container: Container,
        sources: List<SourceDescriptor>? = null,
    ): List<ContainerIssue> =
        when (container) {
            is WidgetContainer -> validateBinding(container.id, container.binding, sources)
            is PageContainer -> validatePage(container, sources)
            is PageSetContainer -> validatePageSet(container, sources)
        }

    private fun validateBinding(
        id: ContainerId,
        binding: LensBinding,
        sources: List<SourceDescriptor>?,
    ): List<ContainerIssue> =
        when (val validity = LensExpressionValidity.check(binding.lens, binding.expression, sources)) {
            LensValidity.Valid -> emptyList()
            is LensValidity.Invalid -> listOf(ContainerIssue.InvalidPairing(id, validity.issues))
        }

    private fun validatePage(
        page: PageContainer,
        sources: List<SourceDescriptor>?,
    ): List<ContainerIssue> =
        buildList {
            when (val content = page.content) {
                is PageContent.Bound -> {
                    addAll(validateBinding(page.id, content.binding, sources))
                    if (page.role == PageRole.FINDER && content.binding.expression !in FINDER_EXPRESSIONS) {
                        add(ContainerIssue.InvalidFinder(page.id))
                    }
                }
                is PageContent.WidgetGrid -> {
                    if (page.role == PageRole.FINDER) add(ContainerIssue.InvalidFinder(page.id))
                    addAll(validateGrid(content, sources))
                }
            }
        }

    private fun validatePageSet(
        pageSet: PageSetContainer,
        sources: List<SourceDescriptor>?,
    ): List<ContainerIssue> =
        buildList {
            addAll(validateBinding(pageSet.id, pageSet.binding, sources))
            if (LensGroup.None == pageSet.binding.lens.group) add(ContainerIssue.PageSetNeedsGroupedLens(pageSet.id))
            ExpressionCatalog.descriptorFor(pageSet.binding.expression).axes
                .filter { it == GestureAxis.HORIZONTAL_SCROLL || it == GestureAxis.HORIZONTAL_PAGER }
                .forEach { add(ContainerIssue.AxisConflict(pageSet.id, it)) }
        }

    private fun validateGrid(
        grid: PageContent.WidgetGrid,
        sources: List<SourceDescriptor>?,
    ): List<ContainerIssue> =
        buildList {
            val seen = HashSet<ContainerId>()
            grid.placements.forEach { placement ->
                val widget = placement.widget
                if (!seen.add(widget.id)) add(ContainerIssue.DuplicateContainerId(widget.id))
                val inBounds =
                    placement.column >= 0 && placement.row >= 0 &&
                        placement.column + widget.span.columns <= grid.columns &&
                        placement.row + widget.span.rows <= grid.rows
                if (!inBounds) add(ContainerIssue.WidgetOutOfBounds(widget.id))
                addAll(validateBinding(widget.id, widget.binding, sources))
            }
            val placements = grid.placements
            for (i in placements.indices) {
                for (j in i + 1 until placements.size) {
                    if (overlaps(placements[i], placements[j])) {
                        add(ContainerIssue.WidgetOverlap(placements[j].widget.id, placements[i].widget.id))
                    }
                }
            }
        }

    private fun overlaps(
        a: WidgetPlacement,
        b: WidgetPlacement,
    ): Boolean =
        a.column < b.column + b.widget.span.columns && b.column < a.column + a.widget.span.columns &&
            a.row < b.row + b.widget.span.rows && b.row < a.row + a.widget.span.rows
}
