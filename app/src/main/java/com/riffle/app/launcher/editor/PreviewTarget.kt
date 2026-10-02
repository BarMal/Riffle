package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.ExpressionChoice
import com.riffle.core.domain.launcher.workspace.editor.ExpressionOptions

/** What the live preview draws. The preview is transient: nothing it shows is stored. */
internal sealed interface PreviewTarget {
    /** No source yet, or nothing can draw the lens. */
    data class Empty(val reason: String) : PreviewTarget

    data class Single(val binding: LensBinding) : PreviewTarget

    /** One page per group, as a page-set would draw it. */
    data class PerGroup(val binding: LensBinding) : PreviewTarget
}

/**
 * The preview for the flow's current choices: the picked expression, or until one is picked the first one the
 * lens allows. An expression that draws each group on its own, or a page-set container, previews as a page-set.
 */
internal fun previewTargetFor(
    state: BindingFlowState,
    context: BindingFlowContext,
): PreviewTarget =
    previewTarget(
        lens = BindingFlow.lens(state),
        choices = BindingFlow.expressionChoices(state, context),
        picked = state.expression,
        pageSet = state.container == ContainerKind.PAGE_SET,
    )

/**
 * The preview of a lens built on its own (Settings > Saved lenses), where no container or expression was picked:
 * the first expression this layout can draw it with, the same rule the flow uses until an expression is picked.
 */
internal fun previewTargetFor(
    lens: Lens?,
    context: EditContext,
): PreviewTarget =
    previewTarget(
        lens = lens,
        choices = lens?.let { ExpressionOptions.forLens(it, context) }.orEmpty(),
        picked = null,
        pageSet = false,
    )

private fun previewTarget(
    lens: Lens?,
    choices: List<ExpressionChoice>,
    picked: ExpressionKind?,
    pageSet: Boolean,
): PreviewTarget {
    if (lens == null) return PreviewTarget.Empty(EditorText.PREVIEW_EMPTY)
    val kind = picked ?: choices.firstOrNull { it.enabled }?.kind
    val perGroup = choices.firstOrNull { it.kind == kind }?.perGroupOnly == true
    return when {
        kind == null -> PreviewTarget.Empty(EditorText.PREVIEW_NO_EXPRESSION)
        perGroup || pageSet -> PreviewTarget.PerGroup(LensBinding(lens, kind))
        else -> PreviewTarget.Single(LensBinding(lens, kind))
    }
}
