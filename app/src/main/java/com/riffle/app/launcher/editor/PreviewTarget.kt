package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind

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
): PreviewTarget {
    val lens = BindingFlow.lens(state) ?: return PreviewTarget.Empty(EditorText.PREVIEW_EMPTY)
    val choices = BindingFlow.expressionChoices(state, context)
    val kind = state.expression ?: choices.firstOrNull { it.enabled }?.kind
    val perGroup = choices.firstOrNull { it.kind == kind }?.perGroupOnly == true
    return when {
        kind == null -> PreviewTarget.Empty(EditorText.PREVIEW_NO_EXPRESSION)
        perGroup || state.container == ContainerKind.PAGE_SET -> PreviewTarget.PerGroup(LensBinding(lens, kind))
        else -> PreviewTarget.Single(LensBinding(lens, kind))
    }
}
