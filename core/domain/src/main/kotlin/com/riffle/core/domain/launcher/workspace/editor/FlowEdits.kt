package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.Workspace

/** Maps a flow mode to the edit it produces and to the binding it starts from. */
internal object FlowEdits {
    fun editFor(
        context: BindingFlowContext,
        binding: LensBinding,
    ): WorkspaceEdit = editFor(context.mode, binding)

    fun editFor(
        mode: FlowMode,
        binding: LensBinding,
    ): WorkspaceEdit =
        when (mode) {
            is FlowMode.EditPage -> WorkspaceEdit.SetPageBinding(mode.pageId, binding)
            is FlowMode.EditWidget -> WorkspaceEdit.SetWidgetBinding(mode.pageId, mode.widgetId, binding)
            FlowMode.EditDock -> WorkspaceEdit.SetDockSection(binding)
            FlowMode.Add -> error("Adding has no single edit; the container choice decides it.")
        }

    fun existingBinding(context: BindingFlowContext): LensBinding? = existingBinding(context.workspace, context.mode)

    fun existingBinding(
        workspace: Workspace,
        mode: FlowMode,
    ): LensBinding? =
        when (mode) {
            FlowMode.Add -> null
            FlowMode.EditDock -> workspace.dock.dynamicSection
            is FlowMode.EditPage ->
                when (val page = workspace.pages.firstOrNull { it.id == mode.pageId }) {
                    is PageSetContainer -> page.binding
                    is PageContainer -> (page.content as? PageContent.Bound)?.binding
                    null -> null
                }
            is FlowMode.EditWidget ->
                (
                    workspace.pages.firstOrNull { it.id == mode.pageId }
                        .let { it as? PageContainer }?.content as? PageContent.WidgetGrid
                )?.placements?.firstOrNull { it.widget.id == mode.widgetId }?.widget?.binding
        }
}
