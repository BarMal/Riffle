package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowReducer
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTargetTest {
    private val context =
        BindingFlowContext(
            sources =
                SourceChoices.build(
                    listOf(
                        SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE)),
                        SourceDescriptor(SourceIds.NOTIFICATIONS, setOf(SourceCapability.GROUPABLE)),
                    ),
                ),
            workspace = Workspace(WorkspaceId("w"), "W"),
        )

    private fun run(vararg actions: BindingFlowAction) =
        actions.fold(BindingFlowState()) { state, action -> BindingFlowReducer.reduce(state, action, context) }

    @Test
    fun nothingChosenPreviewsAHint() {
        assertEquals(PreviewTarget.Empty(EditorText.PREVIEW_EMPTY), previewTargetFor(BindingFlowState(), context))
    }

    @Test
    fun theFirstAllowedExpressionIsPreviewedBeforeOneIsPicked() {
        val state = run(BindingFlowAction.ToggleSource(SourceIds.ALL_APPS))
        val target = previewTargetFor(state, context) as PreviewTarget.Single
        assertEquals(ExpressionKind.ICON_ROW, target.binding.expression)
    }

    @Test
    fun aPerGroupExpressionPreviewsAsAPageSet() {
        val state =
            run(
                BindingFlowAction.ToggleSource(SourceIds.NOTIFICATIONS),
                BindingFlowAction.ApplyPreset(LensPreset.GROUPED),
                BindingFlowAction.Next,
                BindingFlowAction.PickExpression(ExpressionKind.CARD_STACK),
            )
        assertTrue(previewTargetFor(state, context) is PreviewTarget.PerGroup)
    }

    @Test
    fun aLensBuiltOnItsOwnPreviewsWithTheFirstExpressionThisLayoutCanDraw() {
        val edit = context.edit
        val flat = Lens(listOf(SourceIds.ALL_APPS))
        val grouped = Lens(listOf(SourceIds.NOTIFICATIONS), group = LensGroup.ByGroupKey)

        assertEquals(PreviewTarget.Empty(EditorText.PREVIEW_EMPTY), previewTargetFor(null, edit))
        val single = previewTargetFor(flat, edit) as PreviewTarget.Single
        assertEquals(ExpressionKind.ICON_ROW, single.binding.expression)
        assertEquals(flat, single.binding.lens)
        val shown = previewTargetFor(grouped, edit)
        assertTrue(shown is PreviewTarget.Single || shown is PreviewTarget.PerGroup)
        val nothing = previewTargetFor(flat, EditContext(LayoutCapabilities(emptySet()), edit.sources))
        assertEquals(PreviewTarget.Empty(EditorText.PREVIEW_NO_EXPRESSION), nothing)
    }
}
