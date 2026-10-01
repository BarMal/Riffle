package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionCatalog
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensIssue
import com.riffle.core.domain.launcher.workspace.LensValidity
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue

/**
 * One entry of the Expression step. Only [enabled] entries can be picked; the others carry why not, so the
 * UI can say so instead of hiding them.
 *
 * [perGroupOnly] marks an expression that cannot draw the grouped lens as a whole but can draw each group
 * on its own: it is offered for a grouped lens and leads only to a page-set (see docs/product/workspaces-editor.md).
 */
data class ExpressionChoice(
    val kind: ExpressionKind,
    val enabled: Boolean,
    val perGroupOnly: Boolean = false,
    /** Why the lens cannot be drawn this way. */
    val lensIssues: List<LensIssue> = emptyList(),
    val unsupportedByLayout: Boolean = false,
    /** Why the container being edited cannot host it (edit modes only). */
    val containerIssues: List<WorkspaceIssue> = emptyList(),
)

object ExpressionOptions {
    /**
     * Every expression, in catalogue order, enabled only when `LensExpressionValidity.compatibleExpressions`
     * accepts it for [lens] (or, for a grouped lens, when it passes the per-group check a page-set uses),
     * and the layout can draw it. [acceptedBy] further narrows the choice to what the container being edited
     * accepts; adding a new container leaves it null and lets the Container step filter instead.
     */
    fun forLens(
        lens: Lens,
        context: EditContext = EditContext(),
        acceptedBy: ((ExpressionKind) -> EditResult)? = null,
    ): List<ExpressionChoice> {
        val compatible = LensExpressionValidity.compatibleExpressions(lens, context.sources).toSet()
        return ExpressionKind.entries.map { kind ->
            val whole = kind in compatible
            val perGroup = !whole && perGroupValid(lens, kind, context)
            val layoutOk = kind in context.capabilities.expressions
            val container = acceptedBy?.invoke(kind)
            val rejection = (container as? EditResult.Rejected)?.reason
            val containerIssues = (rejection as? EditRejection.Invalid)?.issues.orEmpty()
            ExpressionChoice(
                kind = kind,
                enabled = (whole || perGroup) && layoutOk && container !is EditResult.Rejected,
                perGroupOnly = perGroup,
                lensIssues =
                    (
                        LensExpressionValidity.check(
                            lens,
                            kind,
                            context.sources,
                        ) as? LensValidity.Invalid
                    )?.issues.orEmpty(),
                unsupportedByLayout = !layoutOk,
                containerIssues = containerIssues,
            )
        }
    }

    /** A page-set draws one group per page, so the expression must accept a flat result and not scroll sideways. */
    fun perGroupValid(
        lens: Lens,
        kind: ExpressionKind,
        context: EditContext = EditContext(),
    ): Boolean =
        lens.group != LensGroup.None &&
            LensExpressionValidity.checkPerGroup(lens, kind, context.sources).isValid &&
            ExpressionCatalog.descriptorFor(kind).axes.none {
                it == GestureAxis.HORIZONTAL_SCROLL || it == GestureAxis.HORIZONTAL_PAGER
            }
}
