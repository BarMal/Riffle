package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.editor.ContainerEdits
import com.riffle.core.domain.launcher.workspace.editor.EditResult
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEditor
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool

/**
 * What the wizard's result is applied to: the layout as stored now (normally the context's), its pool, and the
 * arrangements a borrowing workspace shows (`PoolHomeView.candidates(...)`, used only to fork when apps are placed).
 */
data class WizardTarget(
    val layout: LayoutWorkspaces,
    val pool: PlacedItemPool = PlacedItemPool(),
    val candidates: List<WorkspaceId> = emptyList(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    /** The grid of a new arrangement page (the layout's home grid). */
    val grid: GridDimensions = DEFAULT_HOME_GRID,
)

sealed interface WizardResult {
    /**
     * Persist [layout] (workspace store) and, when [poolChanged], [pool] (pool store), together. [containerId] is the
     * new page or widget (null for a dock section, a lens only, or apps added to an existing placed page); [lensId] the
     * saved lens, when one was saved.
     */
    data class Applied(
        val layout: LayoutWorkspaces,
        val pool: PlacedItemPool,
        val workspaceId: WorkspaceId?,
        val containerId: ContainerId? = null,
        val lensId: LensId? = null,
        val apps: PlacedAppsResult? = null,
        val poolChanged: Boolean = false,
        /**
         * The one editor edit this result made to the workspace (null when it changed none): the workspace editor
         * applies it to its own session instead of [layout], so Undo, Redo and Revert cover it (see the doc).
         */
        val edit: WorkspaceEdit? = null,
    ) : WizardResult

    data class Rejected(val problems: List<WizardProblem>) : WizardResult
}

/**
 * Applies a finished wizard ([WizardExit.Finished]) with the existing operations, as one value: the saved lens through
 * `LensLibrary.tryAdd` (the binding references it), the container through `ContainerEdits` and `WorkspaceEditor.apply`
 * (re-validated, so stale state cannot slip through), picked apps through [PlacedApps] (the pool operations). Pure;
 * nothing is applied unless everything is.
 */
object WizardApply {
    fun apply(
        state: WizardState,
        context: WizardContext,
        target: WizardTarget,
    ): WizardResult {
        val problems = WizardFlow.allProblems(state, context)
        val setup = state.setup
        return when {
            problems.isNotEmpty() || setup == null || state.destination == null ->
                WizardResult.Rejected(problems.ifEmpty { listOf(WizardProblem.NoDestination) })
            else ->
                when (val saved = save(state, context, setup, target)) {
                    is LibraryAdd.Rejected -> WizardResult.Rejected(listOf(WizardProblem.Name(saved.problem)))
                    is LibraryAdd.Added, null -> place(state, context, setup, target, saved as LibraryAdd.Added?)
                }
        }
    }

    /** The saved lens, when the wizard saves one (null when it does not). */
    private fun save(
        state: WizardState,
        context: WizardContext,
        setup: GoalSetup,
        target: WizardTarget,
    ): LibraryAdd? =
        if (WizardFlow.saves(state)) {
            val lens = ContentRecipes.lens(setup, ContentRecipes.look(setup))
            target.layout.library.tryAdd(WizardFlow.name(state, context), lens, target.ids)
        } else {
            null
        }

    private fun place(
        state: WizardState,
        context: WizardContext,
        setup: GoalSetup,
        target: WizardTarget,
        saved: LibraryAdd.Added?,
    ): WizardResult {
        val layout = saved?.let { target.layout.copy(library = it.library) } ?: target.layout
        val workspace = context.workspace?.id?.let(layout::find)
        return when {
            state.destination == Destination.LENS_ONLY ->
                WizardResult.Applied(layout, target.pool, null, lensId = saved?.id)
            workspace == null -> WizardResult.Rejected(listOf(WizardProblem.NoWorkspace))
            ContentRecipes.goal(setup).placedItems -> placed(setup, context, workspace, layout, target)
            else -> {
                val binding = ContentRecipes.binding(setup).copy(ref = saved?.id)
                container(state, context, workspace, layout, target, binding, saved?.id)
            }
        }
    }

    private fun container(
        state: WizardState,
        context: WizardContext,
        workspace: Workspace,
        layout: LayoutWorkspaces,
        target: WizardTarget,
        binding: LensBinding,
        lensId: LensId?,
    ): WizardResult {
        val kind = checkNotNull(state.destination?.kind())
        val pageId = ContainerId(target.ids.next())
        val widgetId = ContainerId(target.ids.next())
        val edit = ContainerEdits.build(kind, binding, state.widgetTarget, pageId, widgetId)
        return when (val result = WorkspaceEditor.apply(workspace, edit, context.edit)) {
            is EditResult.Rejected -> WizardResult.Rejected(listOf(WizardProblem.DestinationRejected(result.reason)))
            is EditResult.Applied ->
                WizardResult.Applied(
                    layout = layout.replace(workspace.id) { result.workspace },
                    pool = target.pool,
                    workspaceId = workspace.id,
                    containerId =
                        when (edit) {
                            is WorkspaceEdit.AddWidget -> widgetId
                            is WorkspaceEdit.AddPage ->
                                if (state.destination == Destination.WIDGET) widgetId else pageId
                            else -> null
                        },
                    lensId = lensId,
                    edit = edit,
                )
        }
    }

    /**
     * A placed goal: the apps go into the pool ([PlacedApps]); the workspace gains a `home.grid` page only when it has
     * none (a workspace draws all of its placed pages wherever it has one).
     */
    private fun placed(
        setup: GoalSetup,
        context: WizardContext,
        workspace: Workspace,
        layout: LayoutWorkspaces,
        target: WizardTarget,
    ): WizardResult {
        val hasHome = WorkspaceHome.hasHomeGrid(workspace)
        val pageKey = LauncherPageId("page-${target.ids.next()}")
        val apps =
            PlacedApps.place(
                target.pool,
                workspace.id,
                setup.apps,
                pageKey,
                hasHome,
                target.candidates,
                target.ids,
                target.grid,
            )
        if (hasHome) {
            return WizardResult.Applied(
                layout,
                apps.pool,
                workspace.id,
                apps = apps,
                poolChanged = apps.pool != target.pool,
            )
        }
        val pageId = ContainerId(target.ids.next())
        val page =
            PageContainer(
                pageId,
                PageContent.Bound(ContentRecipes.binding(setup, pageKey = apps.pageKey ?: PLACEHOLDER_PAGE_KEY)),
            )
        return when (val result = WorkspaceEditor.apply(workspace, WorkspaceEdit.AddPage(page, 0), context.edit)) {
            is EditResult.Rejected -> WizardResult.Rejected(listOf(WizardProblem.DestinationRejected(result.reason)))
            is EditResult.Applied ->
                WizardResult.Applied(
                    layout.replace(workspace.id) { result.workspace },
                    apps.pool,
                    workspace.id,
                    pageId,
                    apps = apps,
                    poolChanged = apps.pool != target.pool,
                    edit = WorkspaceEdit.AddPage(page, 0),
                )
        }
    }
}
