package com.riffle.core.domain.launcher.workspace

@JvmInline
value class WorkspaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace ids must not be blank." }
    }
}

/**
 * A saved arrangement of containers plus dock definition, gesture bindings and skin override.
 *
 * Independent per layout (posture): each layout stores its own workspaces with no live link between
 * them. Per-layout storage, copy-from-other-layout and migration belong to the persistence slice.
 * Holds no item content.
 */
data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val pages: List<PageHost> = emptyList(),
    val dock: WorkspaceDock = WorkspaceDock(),
    /** Gesture id -> action id. Opaque here; the gesture settings own the vocabulary. */
    val gestureBindings: Map<String, String> = emptyMap(),
    /** Null follows the global skin. */
    val skinOverrideId: String? = null,
    /**
     * The catalog id of the preset this workspace was installed from, when known: only so Settings can offer
     * "Reset to preset". Never inferred; null for workspaces migrated or created before it was recorded.
     */
    val presetId: String? = null,
    /**
     * The page the launcher opens on (see [WorkspaceReturnResolver]). Null means the first pager page. An id
     * that names no page is ignored, never an error. May name the Finder page, which opens on search.
     */
    val startPageId: ContainerId? = null,
)

/** Pinned dock entries stay in `DockModel`; the workspace only adds the dynamic section's lens. */
data class WorkspaceDock(
    val dynamicSection: LensBinding? = null,
)

sealed interface WorkspaceIssue {
    data class Container(val issue: ContainerIssue) : WorkspaceIssue

    data class DockPairing(val issues: List<LensIssue>) : WorkspaceIssue

    data object NoPages : WorkspaceIssue

    data object MultipleFinderPages : WorkspaceIssue

    /** The layout cannot draw this expression. */
    data class UnsupportedExpression(val kind: ExpressionKind) : WorkspaceIssue
}

/** What a layout (posture) can draw. Defaults to every expression. */
data class LayoutCapabilities(
    val expressions: Set<ExpressionKind> = ExpressionKind.entries.toSet(),
)

object WorkspaceValidation {
    fun validate(
        workspace: Workspace,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        sources: List<SourceDescriptor>? = null,
    ): List<WorkspaceIssue> =
        buildList {
            if (workspace.pages.isEmpty()) add(WorkspaceIssue.NoPages)
            if (workspace.pages.count { it is PageContainer && it.role == PageRole.FINDER } > 1) {
                add(WorkspaceIssue.MultipleFinderPages)
            }
            val seen = HashSet<ContainerId>()
            workspace.pages.forEach { page ->
                if (!seen.add(page.id)) add(WorkspaceIssue.Container(ContainerIssue.DuplicateContainerId(page.id)))
                ContainerValidation.validate(page, sources).forEach { add(WorkspaceIssue.Container(it)) }
            }
            workspace.dock.dynamicSection?.let { binding ->
                val validity = LensExpressionValidity.check(binding.lens, binding.expression, sources)
                if (validity is LensValidity.Invalid) add(WorkspaceIssue.DockPairing(validity.issues))
            }
            expressionsUsed(workspace)
                .filter { it !in capabilities.expressions }
                .forEach { add(WorkspaceIssue.UnsupportedExpression(it)) }
        }

    fun expressionsUsed(workspace: Workspace): Set<ExpressionKind> =
        buildSet {
            workspace.pages.forEach { page ->
                when (page) {
                    is PageSetContainer -> add(page.binding.expression)
                    is PageContainer ->
                        when (val content = page.content) {
                            is PageContent.Bound -> add(content.binding.expression)
                            is PageContent.WidgetGrid ->
                                content.placements.forEach {
                                    add(
                                        it.widget.binding.expression,
                                    )
                                }
                        }
                }
            }
            workspace.dock.dynamicSection?.let { add(it.expression) }
        }
}

sealed interface WorkspaceResolution {
    val workspace: Workspace

    data class Resolved(override val workspace: Workspace) : WorkspaceResolution

    /** The requested workspace cannot be drawn on this layout; [workspace] is the default, and the UI says so. */
    data class FellBack(
        override val workspace: Workspace,
        val requested: WorkspaceId,
        val issues: List<WorkspaceIssue>,
    ) : WorkspaceResolution
}

object WorkspaceResolver {
    fun resolve(
        requested: Workspace,
        default: Workspace,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        sources: List<SourceDescriptor>? = null,
    ): WorkspaceResolution {
        val issues = WorkspaceValidation.validate(requested, capabilities, sources)
        return if (issues.isEmpty() || requested.id == default.id) {
            WorkspaceResolution.Resolved(requested)
        } else {
            WorkspaceResolution.FellBack(default, requested.id, issues)
        }
    }
}
