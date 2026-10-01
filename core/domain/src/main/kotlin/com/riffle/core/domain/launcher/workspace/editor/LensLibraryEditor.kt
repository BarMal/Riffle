package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.BindingSite
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryRejection
import com.riffle.core.domain.launcher.workspace.LensLibraryResult
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SiteKind
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

sealed interface LibraryEditRejection {
    data object UnknownWorkspace : LibraryEditRejection

    /** The target container (or the dock section) does not exist or has no binding to change. */
    data object UnknownTarget : LibraryEditRejection

    data class Library(val reason: LensLibraryRejection) : LibraryEditRejection

    /** The workspace editor refused the resulting binding (for example the pairing is invalid). */
    data class Editor(val reason: EditRejection) : LibraryEditRejection
}

sealed interface LibraryEditResult {
    /** [layout] holds the new library and the edited workspace. [lensId] is set by Save as lens. */
    data class Applied(
        val layout: LayoutWorkspaces,
        val lensId: LensId? = null,
    ) : LibraryEditResult

    data class Rejected(val reason: LibraryEditRejection) : LibraryEditResult
}

/**
 * "Use saved lens", "Save as lens", "Detach" and "Edit saved lens" as pure operations over a layout.
 *
 * Binding changes go through [WorkspaceEditor.apply], so the existing gate applies: a saved lens that
 * does not pair with the target's expression is rejected with the reasons, never stored. Editing the
 * saved lens itself is gated by [LensLibraryOps.previewEdit]: an edit that would invalidate dependents
 * is rejected with the impact unless the caller chose [BreakPolicy.DETACH_BROKEN]. [FlowMode.Add] has no
 * existing binding; build one with [bindingFor] and add the container through the usual flow.
 */
object LensLibraryEditor {
    /** The binding that uses [id] with [expression], or null when the layout has no such lens. */
    fun bindingFor(
        layout: LayoutWorkspaces,
        id: LensId,
        expression: ExpressionKind,
    ): LensBinding? = layout.library.find(id)?.let { LensBinding(it.lens, expression, it.id) }

    /** Points the binding at [target] to saved lens [id], keeping its expression unless [expression] is set. */
    fun useSavedLens(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
        target: FlowMode,
        id: LensId,
        expression: ExpressionKind? = null,
        context: EditContext = EditContext(),
    ): LibraryEditResult =
        withTarget(layout, workspaceId, target) { workspace, current ->
            val binding = bindingFor(layout, id, expression ?: current.expression)
            if (binding == null) {
                reject(LensLibraryRejection.Problem(LibraryProblem.UNKNOWN_LENS))
            } else {
                setBinding(layout, workspace, target, binding, context)
            }
        }

    /** Adds the binding's lens to the library as [name] and makes the binding reference the new entry. */
    fun saveAsLens(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
        target: FlowMode,
        name: String,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
        context: EditContext = EditContext(),
    ): LibraryEditResult =
        withTarget(layout, workspaceId, target) { workspace, current ->
            when (val added = layout.library.tryAdd(name, current.lens, ids)) {
                is LibraryAdd.Rejected -> reject(LensLibraryRejection.Problem(added.problem))
                is LibraryAdd.Added -> {
                    val withLens = layout.copy(library = added.library)
                    val result = setBinding(withLens, workspace, target, current.copy(ref = added.id), context)
                    if (result is LibraryEditResult.Applied) result.copy(lensId = added.id) else result
                }
            }
        }

    /** Makes the binding inline again: it keeps its lens and draws exactly as before. Always valid. */
    fun detach(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
        target: FlowMode,
        context: EditContext = EditContext(),
    ): LibraryEditResult =
        withTarget(layout, workspaceId, target) { workspace, current ->
            setBinding(layout, workspace, target, current.copy(ref = null), context)
        }

    /** Edits a saved lens and refreshes every dependent, gated on validity (see the class comment). */
    fun editSavedLens(
        layout: LayoutWorkspaces,
        id: LensId,
        newLens: Lens,
        policy: BreakPolicy = BreakPolicy.REJECT,
        context: EditContext = EditContext(),
    ): LibraryEditResult =
        when (
            val result =
                LensLibraryOps.applyEdit(
                    layout,
                    id,
                    newLens,
                    policy,
                    context.sources,
                    context.capabilities,
                )
        ) {
            is LensLibraryResult.Applied -> LibraryEditResult.Applied(result.layout)
            is LensLibraryResult.Rejected -> reject(result.reason)
        }

    private fun reject(reason: LensLibraryRejection): LibraryEditResult =
        LibraryEditResult.Rejected(LibraryEditRejection.Library(reason))

    private fun withTarget(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
        target: FlowMode,
        block: (Workspace, LensBinding) -> LibraryEditResult,
    ): LibraryEditResult {
        val workspace = layout.find(workspaceId)
        val current = workspace?.let { existing(it, target) }
        return when {
            workspace == null -> LibraryEditResult.Rejected(LibraryEditRejection.UnknownWorkspace)
            current == null -> LibraryEditResult.Rejected(LibraryEditRejection.UnknownTarget)
            else -> block(workspace, current)
        }
    }

    private fun existing(
        workspace: Workspace,
        target: FlowMode,
    ): LensBinding? = WorkspaceBindings.sites(workspace).firstOrNull { matches(it, target) }?.binding

    private fun matches(
        site: BindingSite,
        target: FlowMode,
    ): Boolean =
        when (target) {
            FlowMode.Add -> false
            FlowMode.EditDock -> site.kind == SiteKind.DOCK
            is FlowMode.EditPage -> site.kind in PAGE_KINDS && site.containerId == target.pageId
            is FlowMode.EditWidget -> site.kind == SiteKind.WIDGET && site.containerId == target.widgetId
        }

    private fun setBinding(
        layout: LayoutWorkspaces,
        workspace: Workspace,
        target: FlowMode,
        binding: LensBinding,
        context: EditContext,
    ): LibraryEditResult {
        val edit =
            when (target) {
                is FlowMode.EditPage -> WorkspaceEdit.SetPageBinding(target.pageId, binding)
                is FlowMode.EditWidget -> WorkspaceEdit.SetWidgetBinding(target.pageId, target.widgetId, binding)
                else -> WorkspaceEdit.SetDockSection(binding)
            }
        return when (val result = WorkspaceEditor.apply(workspace, edit, context)) {
            is EditResult.Applied -> LibraryEditResult.Applied(layout.replace(workspace.id) { result.workspace })
            is EditResult.Rejected -> LibraryEditResult.Rejected(LibraryEditRejection.Editor(result.reason))
        }
    }

    private val PAGE_KINDS = setOf(SiteKind.PAGE, SiteKind.PAGE_SET)
}
