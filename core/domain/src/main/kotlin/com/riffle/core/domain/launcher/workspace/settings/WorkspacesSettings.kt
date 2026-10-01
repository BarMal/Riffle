package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceResolution
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets

/** One workspace of the layout being shown, with what its row may offer. */
data class WorkspaceRow(
    val id: WorkspaceId,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val pageCount: Int,
    /** The preset it was installed from, when known; Reset to preset is offered only then. */
    val presetName: String?,
    val canDelete: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
) {
    val canReset: Boolean get() = presetName != null
}

/**
 * The stored active workspace cannot be drawn on this layout, so [shownName] is drawn instead. [unsupported]
 * are the expressions this layout cannot draw; [otherIssues] counts any other reasons.
 */
data class LayoutFallbackNotice(
    val requestedName: String,
    val shownName: String,
    val unsupported: List<ExpressionKind>,
    val otherIssues: Int,
)

/** A layout a copy can come from, with how many workspaces it would bring. */
data class CopySource(
    val layout: HomeLayoutDeviceClass,
    val workspaceCount: Int,
)

/** The Workspaces page as data for the layout being viewed. */
data class WorkspacesSettingsModel(
    val layout: HomeLayoutDeviceClass,
    val rows: List<WorkspaceRow>,
    val fallback: LayoutFallbackNotice?,
    /** The other layouts a copy can come from, in device-class order. */
    val copySources: List<CopySource>,
    /** Edit opens the editor for the layout the device is showing; another layout is read and managed only. */
    val canEdit: Boolean,
) {
    val onlyOne: Boolean get() = rows.size == 1
}

object WorkspacesSettingsPlanner {
    /**
     * Plans the page for [viewed], among the layouts [available]. [current] is the layout this device is
     * showing now (the only one the editor can open).
     */
    fun plan(
        set: WorkspaceSet,
        viewed: HomeLayoutDeviceClass,
        current: HomeLayoutDeviceClass,
        available: Collection<HomeLayoutDeviceClass> = HomeLayoutDeviceClass.entries,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): WorkspacesSettingsModel {
        val layout = set.workspacesFor(viewed)
        return WorkspacesSettingsModel(
            layout = viewed,
            rows = layout.workspaces.mapIndexed { index, workspace -> row(layout, workspace, index) },
            fallback = fallback(set.resolveActive(viewed, capabilities), layout),
            copySources =
                HomeLayoutDeviceClass.entries
                    .filter { it != viewed && it in available }
                    .map { CopySource(it, set.workspacesFor(it).workspaces.size) },
            canEdit = viewed == current,
        )
    }

    private fun row(
        layout: LayoutWorkspaces,
        workspace: Workspace,
        index: Int,
    ) = WorkspaceRow(
        id = workspace.id,
        name = workspace.name,
        isActive = workspace.id == layout.activeId,
        isDefault = workspace.id == layout.defaultId,
        pageCount = workspace.pages.size,
        presetName = presetOf(workspace)?.name,
        canDelete = layout.workspaces.size > 1,
        canMoveUp = index > 0,
        canMoveDown = index < layout.workspaces.size - 1,
    )

    /** The preset [workspace] records as its origin; never guessed from its name or content. */
    fun presetOf(workspace: Workspace) = workspace.presetId?.let(WorkspacePresets::byId)

    private fun fallback(
        resolution: WorkspaceResolution,
        layout: LayoutWorkspaces,
    ): LayoutFallbackNotice? =
        (resolution as? WorkspaceResolution.FellBack)?.let { fellBack ->
            LayoutFallbackNotice(
                requestedName = layout.find(fellBack.requested)?.name.orEmpty(),
                shownName = fellBack.workspace.name,
                unsupported = fellBack.issues.filterIsInstance<WorkspaceIssue.UnsupportedExpression>().map { it.kind },
                otherIssues = fellBack.issues.count { it !is WorkspaceIssue.UnsupportedExpression },
            )
        }
}
