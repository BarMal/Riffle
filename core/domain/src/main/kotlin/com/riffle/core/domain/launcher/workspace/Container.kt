package com.riffle.core.domain.launcher.workspace

@JvmInline
value class ContainerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Container ids must not be blank." }
    }
}

/** A lens and the expression that draws it: the unit a container hosts. */
data class LensBinding(
    val lens: Lens,
    val expression: ExpressionKind,
)

/**
 * Where an expression lives. Nesting is fixed by the types below (page-set/page -> widget), which
 * bounds composition depth by construction.
 */
sealed interface Container {
    val id: ContainerId

    /** Gesture axes this container owns; consumed by the gesture arbitration layer. */
    val ownedAxes: Set<GestureAxis>
}

/** Containers that occupy a full workspace page. */
sealed interface PageHost : Container

/** A sized area on a page, hosting one lens + expression. */
data class WidgetContainer(
    override val id: ContainerId,
    val span: WidgetSpan,
    val binding: LensBinding,
) : Container {
    override val ownedAxes: Set<GestureAxis> get() = ExpressionCatalog.descriptorFor(binding.expression).axes
}

data class WidgetSpan(val columns: Int = 1, val rows: Int = 1) {
    init {
        require(columns > 0 && rows > 0) { "Widget spans must be positive." }
    }
}

enum class PageRole {
    STANDARD,

    /** The page the dock menu's Finder opens: an All-apps lens drawn as Categories or AlphaList. */
    FINDER,
}

/** One full page hosting one lens + expression, or a free grid of widgets. */
data class PageContainer(
    override val id: ContainerId,
    val content: PageContent,
    val role: PageRole = PageRole.STANDARD,
) : PageHost {
    override val ownedAxes: Set<GestureAxis>
        get() =
            when (content) {
                is PageContent.Bound -> ExpressionCatalog.descriptorFor(content.binding.expression).axes
                // Each widget declares its own axes; the grid itself claims none.
                is PageContent.WidgetGrid -> emptySet()
            }
}

sealed interface PageContent {
    data class Bound(val binding: LensBinding) : PageContent

    data class WidgetGrid(
        val columns: Int,
        val rows: Int,
        val placements: List<WidgetPlacement>,
    ) : PageContent {
        init {
            require(columns > 0 && rows > 0) { "Widget grids must have positive dimensions." }
        }
    }
}

data class WidgetPlacement(
    val widget: WidgetContainer,
    val column: Int,
    val row: Int,
)

/** A grouped lens expanded to one page per group (notifications per app, apps per category). */
data class PageSetContainer(
    override val id: ContainerId,
    val binding: LensBinding,
) : PageHost {
    override val ownedAxes: Set<GestureAxis>
        get() = ExpressionCatalog.descriptorFor(binding.expression).axes + GestureAxis.HORIZONTAL_PAGER
}
