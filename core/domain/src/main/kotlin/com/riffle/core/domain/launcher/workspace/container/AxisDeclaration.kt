package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Container
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionCatalog
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetContainer

/** An axis a container claims that its own pager already owns. Mirrors `ContainerIssue.AxisConflict`. */
data class AxisClaimConflict(
    val containerId: ContainerId,
    val axis: GestureAxis,
)

/**
 * What the gesture arbitration layer needs to know about a container subtree:
 *
 * - [owned]: axes the container itself owns (a page-set's pager, a bound page's scroll axis).
 * - [childAxes]: axes each child widget owns, keyed by widget id. Widgets are siblings in separate
 *   regions, so they never conflict with each other.
 * - [effective]: every axis something in the subtree consumes; what a parent arbiter must leave alone.
 * - [conflicts]: declarations that cannot both hold. Identical to what `ContainerValidation` reports
 *   for the same container, so the editor and the runtime agree.
 */
data class AxisDeclaration(
    val owned: Set<GestureAxis>,
    val childAxes: Map<ContainerId, Set<GestureAxis>> = emptyMap(),
    val conflicts: List<AxisClaimConflict> = emptyList(),
) {
    val effective: Set<GestureAxis> get() = owned + childAxes.values.flatten()

    val hasConflict: Boolean get() = conflicts.isNotEmpty()

    operator fun contains(axis: GestureAxis): Boolean = axis in effective

    /**
     * A page-set with fewer than two pages has nowhere to page to, so it must not claim the pager axis
     * (gestures.md rule 4: a container with no room scrolls nowhere and hands the drag on).
     */
    fun forPageCount(pageCount: Int): AxisDeclaration =
        if (pageCount >= 2) this else copy(owned = owned - GestureAxis.HORIZONTAL_PAGER)
}

object AxisDeclarations {
    /** Axes a page-set's own pager already claims; an expression may not also scroll along them. */
    private val PAGER_EXCLUDED_AXES = setOf(GestureAxis.HORIZONTAL_SCROLL, GestureAxis.HORIZONTAL_PAGER)

    fun resolve(container: Container): AxisDeclaration =
        when (container) {
            is WidgetContainer -> AxisDeclaration(container.ownedAxes)
            is PageContainer -> resolvePage(container)
            is PageSetContainer -> resolvePageSet(container)
        }

    private fun resolvePage(page: PageContainer): AxisDeclaration =
        when (val content = page.content) {
            is PageContent.Bound -> AxisDeclaration(page.ownedAxes)
            is PageContent.WidgetGrid ->
                AxisDeclaration(
                    owned = page.ownedAxes,
                    childAxes =
                        content.placements
                            .filter { it.widget.ownedAxes.isNotEmpty() }
                            .associate { it.widget.id to it.widget.ownedAxes },
                )
        }

    private fun resolvePageSet(pageSet: PageSetContainer): AxisDeclaration =
        AxisDeclaration(
            owned = pageSet.ownedAxes,
            conflicts =
                ExpressionCatalog.descriptorFor(pageSet.binding.expression).axes
                    .filter { it in PAGER_EXCLUDED_AXES }
                    .map { AxisClaimConflict(pageSet.id, it) },
        )
}
