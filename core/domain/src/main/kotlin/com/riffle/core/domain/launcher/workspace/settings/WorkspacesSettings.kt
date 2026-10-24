package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.workspace.DockMenuReachability
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.NewAppDefaults
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutClasses
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import com.riffle.core.domain.launcher.workspace.WorkspaceResolution
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSkin
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

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
    /** The workspace's dock override as stored (null follows the layout's dock, 11.2). */
    val dock: DockPresentation? = null,
    /** Whether Hidden may be chosen: a gesture reaches the workspace menu without the dock (11.4). */
    val canHideDock: Boolean = false,
    /** The new-app setting in effect (9.8): the workspace's own, else its preset's default. */
    val newApps: WorkspaceNewApps = NewAppDefaults.MIGRATED,
    /** True while [newApps] is the preset's default rather than the workspace's own choice. */
    val newAppsFollowPreset: Boolean = true,
    /** The theme preset this workspace overrides the global one with; null follows Settings > Appearance. */
    val skin: LauncherThemePreset? = null,
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
     * showing now (the only one the editor can open). All three are read as Workspaces layout classes
     * ([workspaceLayoutClass], N10), so a landscape phone is the phone layout and is never offered as a source.
     */
    fun plan(
        set: WorkspaceSet,
        viewed: HomeLayoutDeviceClass,
        current: HomeLayoutDeviceClass,
        available: Collection<HomeLayoutDeviceClass> = HomeLayoutDeviceClass.entries,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        globalGestureActions: Collection<LauncherGestureAction> = emptyList(),
    ): WorkspacesSettingsModel {
        val viewedLayout = viewed.workspaceLayoutClass()
        val layout = set.workspacesFor(viewedLayout)
        val offered = WorkspaceLayoutClasses.of(available)
        return WorkspacesSettingsModel(
            layout = viewedLayout,
            rows =
                layout.workspaces.mapIndexed { index, workspace ->
                    row(layout, workspace, index, globalGestureActions)
                },
            fallback = fallback(set.resolveActive(viewedLayout, capabilities), layout),
            copySources =
                WorkspaceLayoutClasses.entries
                    .filter { it != viewedLayout && it in offered }
                    .map { CopySource(it, set.workspacesFor(it).workspaces.size) },
            canEdit = viewedLayout == current.workspaceLayoutClass(),
        )
    }

    private fun row(
        layout: LayoutWorkspaces,
        workspace: Workspace,
        index: Int,
        globalGestureActions: Collection<LauncherGestureAction>,
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
        dock = workspace.dock.presentation,
        canHideDock = DockMenuReachability.canHideDock(workspace, globalGestureActions),
        newApps = NewAppDefaults.resolve(workspace),
        newAppsFollowPreset = workspace.newApps == null,
        skin = WorkspaceSkin.overrideOf(workspace),
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
