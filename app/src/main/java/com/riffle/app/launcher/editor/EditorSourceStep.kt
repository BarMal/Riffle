package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.toFlowAction

/**
 * Step 1: pick sources and a lens. The body is the shared [LensBuilder] (also used by Settings > Saved lenses); the
 * flow only adapts its lens actions into [BindingFlowAction]s, whose reducer applies them to the same draft rules.
 */
@Composable
internal fun EditorSourceStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
    queryFlush: PendingQueryFlush? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
        if (showsSavedLensStep(state)) {
            EditorSavedLensStep(state, context, onFlowAction)
        } else {
            SavedLensEntry(context, onFlowAction)
            LensBuilder(
                draft = state.draft,
                sources = context.sources,
                onAction = { onFlowAction(it.toFlowAction()) },
                onRequestSourceAccess = onRequestSourceAccess,
                queryFlush = queryFlush,
            )
        }
    }
}
