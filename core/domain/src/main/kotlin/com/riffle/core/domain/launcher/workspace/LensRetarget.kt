package com.riffle.core.domain.launcher.workspace

/** A container (or the dock section, when [containerId] is null) whose binding references a saved lens. */
data class LensDependent(
    val workspaceId: WorkspaceId,
    val containerId: ContainerId?,
    val expression: ExpressionKind,
    /** True for a page-set, whose lens is checked per group. */
    val perGroup: Boolean,
)

/** A dependent that an edit would leave with a problem it did not have before. */
data class BrokenDependent(
    val dependent: LensDependent,
    val issues: List<WorkspaceIssue>,
)

data class EditImpact(
    val dependents: List<LensDependent>,
    val wouldBreak: List<BrokenDependent>,
) {
    val isClean: Boolean get() = wouldBreak.isEmpty()
}

internal data class Retargeted(
    val layout: LayoutWorkspaces,
    val impact: EditImpact,
)

/**
 * The shared dry run: points every dependent of one saved lens at another definition and finds, through
 * [WorkspaceValidation], the dependents that would become invalid. Those are detached (they keep their
 * current lens inline) in the returned layout; the rest follow. The library itself is not touched.
 */
internal object LensRetarget {
    fun dependents(
        layout: LayoutWorkspaces,
        id: LensId,
    ): List<LensDependent> =
        layout.workspaces.flatMap { workspace ->
            WorkspaceBindings.sites(workspace).filter { it.binding.ref == id }.map(::dependent)
        }

    fun run(
        layout: LayoutWorkspaces,
        from: LensId,
        to: SavedLens,
        sources: List<SourceDescriptor>?,
        capabilities: LayoutCapabilities,
    ): Retargeted {
        val switched =
            layout.mapBindings { site ->
                if (site.binding.ref == from) LensBinding(to.lens, site.binding.expression, to.id) else site.binding
            }
        val broken = introduced(layout, switched, sources, capabilities)
        val result =
            layout.mapBindings { site ->
                when {
                    site.binding.ref != from -> site.binding
                    site.key in broken -> site.binding.copy(ref = null)
                    else -> LensBinding(to.lens, site.binding.expression, to.id)
                }
            }
        val dependents = dependents(layout, from)
        val wouldBreak =
            dependents.mapNotNull { dependent ->
                broken[SiteKey(dependent.workspaceId, dependent.containerId)]?.let { BrokenDependent(dependent, it) }
            }
        return Retargeted(result, EditImpact(dependents, wouldBreak))
    }

    private fun dependent(site: BindingSite) =
        LensDependent(site.workspaceId, site.containerId, site.binding.expression, site.kind == SiteKind.PAGE_SET)

    /** Issues [after] has that [before] did not, by the container they name (null = the dock). */
    private fun introduced(
        before: LayoutWorkspaces,
        after: LayoutWorkspaces,
        sources: List<SourceDescriptor>?,
        capabilities: LayoutCapabilities,
    ): Map<SiteKey, List<WorkspaceIssue>> {
        val result = LinkedHashMap<SiteKey, MutableList<WorkspaceIssue>>()
        before.workspaces.zip(after.workspaces).forEach { (old, new) ->
            val known = WorkspaceValidation.validate(old, capabilities, sources).toSet()
            WorkspaceValidation.validate(new, capabilities, sources).filterNot { it in known }.forEach { issue ->
                val container =
                    when (issue) {
                        is WorkspaceIssue.Container -> issue.issue.containerId
                        else -> null
                    }
                val relevant = issue is WorkspaceIssue.Container || issue is WorkspaceIssue.DockPairing
                if (relevant) result.getOrPut(SiteKey(new.id, container)) { mutableListOf() }.add(issue)
            }
        }
        return result
    }
}
