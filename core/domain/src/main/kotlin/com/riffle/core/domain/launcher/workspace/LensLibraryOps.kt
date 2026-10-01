package com.riffle.core.domain.launcher.workspace

/** What to do with dependents an edit would break. */
enum class BreakPolicy {
    /** Refuse the edit and report the impact. */
    REJECT,

    /** Apply the edit; broken dependents are detached and keep drawing their old lens inline. */
    DETACH_BROKEN,
}

/** What deleting a saved lens does to the containers that use it. Deleting is never blocked. */
sealed interface RemovePolicy {
    /** Every dependent becomes inline, keeping its lens, then the entry is removed. */
    data object Detach : RemovePolicy

    /** Dependents switch to [id]; the ones it would break are detached instead. */
    data class ReplaceWith(val id: LensId) : RemovePolicy
}

sealed interface LensLibraryRejection {
    data class Problem(val problem: LibraryProblem) : LensLibraryRejection

    data class BreaksDependents(val impact: EditImpact) : LensLibraryRejection
}

sealed interface LensLibraryResult {
    /** [detached] lists dependents that were detached rather than broken. */
    data class Applied(
        val layout: LayoutWorkspaces,
        val detached: List<LensDependent> = emptyList(),
    ) : LensLibraryResult

    data class Rejected(val reason: LensLibraryRejection) : LensLibraryResult
}

/** A binding whose [ref] names no entry of its layout's library. It keeps drawing its snapshot. */
data class DanglingRef(
    val workspaceId: WorkspaceId,
    val containerId: ContainerId?,
    val ref: LensId,
)

data class Rehydrated(
    val layout: LayoutWorkspaces,
    val dangling: List<DanglingRef>,
)

/**
 * Library operations scoped to one layout: each takes and returns that layout's [LayoutWorkspaces], so a
 * library edit and the refresh of its dependents' snapshots are one value (one atomic store write).
 *
 * Invariant kept by every operation here: if a binding's ref resolves, its inline lens equals the
 * library's lens, and a binding never loses its drawable lens. Library lookups are per layout; a ref
 * cannot resolve against another layout's library.
 */
object LensLibraryOps {
    fun dependents(
        layout: LayoutWorkspaces,
        id: LensId,
    ): List<LensDependent> = LensRetarget.dependents(layout, id)

    /** The binding to draw: the library's lens when [LensBinding.ref] resolves, else its own snapshot. */
    fun resolve(
        library: LensLibrary,
        binding: LensBinding,
    ): LensBinding {
        val saved = binding.ref?.let(library::find)
        return if (saved == null) binding else binding.copy(lens = saved.lens)
    }

    fun danglingRefs(layout: LayoutWorkspaces): List<DanglingRef> =
        layout.workspaces.flatMap { workspace ->
            WorkspaceBindings.sites(workspace).mapNotNull { site ->
                site.binding.ref?.takeIf { layout.library.find(it) == null }
                    ?.let { DanglingRef(site.workspaceId, site.containerId, it) }
            }
        }

    /**
     * Dry run of editing [id]'s lens to [newLens]: the dependents, and those that would become invalid
     * (by [WorkspaceValidation]) with the reasons. Changes nothing.
     */
    fun previewEdit(
        layout: LayoutWorkspaces,
        id: LensId,
        newLens: Lens,
        sources: List<SourceDescriptor>? = null,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): EditImpact {
        val saved = layout.library.find(id) ?: return EditImpact(emptyList(), emptyList())
        return LensRetarget.run(layout, id, saved.copy(lens = newLens), sources, capabilities).impact
    }

    /**
     * Writes [newLens] to the library and refreshes the snapshot of every dependent, atomically. Under
     * [BreakPolicy.REJECT] an edit that would invalidate a dependent is refused with the impact.
     */
    fun applyEdit(
        layout: LayoutWorkspaces,
        id: LensId,
        newLens: Lens,
        policy: BreakPolicy = BreakPolicy.REJECT,
        sources: List<SourceDescriptor>? = null,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): LensLibraryResult {
        val saved = layout.library.find(id)
        val retargeted = saved?.let { LensRetarget.run(layout, id, it.copy(lens = newLens), sources, capabilities) }
        return when {
            retargeted == null -> reject(LibraryProblem.UNKNOWN_LENS)
            !retargeted.impact.isClean && policy == BreakPolicy.REJECT ->
                LensLibraryResult.Rejected(LensLibraryRejection.BreaksDependents(retargeted.impact))
            else ->
                LensLibraryResult.Applied(
                    retargeted.layout.copy(library = layout.library.update(id, newLens)),
                    retargeted.impact.wouldBreak.map { it.dependent },
                )
        }
    }

    /** Deletes [id] without ever blocking: dependents are detached or moved to a replacement ([policy]). */
    fun remove(
        layout: LayoutWorkspaces,
        id: LensId,
        policy: RemovePolicy = RemovePolicy.Detach,
        sources: List<SourceDescriptor>? = null,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): LensLibraryResult {
        val replacement = (policy as? RemovePolicy.ReplaceWith)?.id?.takeIf { it != id }?.let(layout.library::find)
        val library = layout.library.remove(id)
        return when {
            layout.library.find(id) == null -> reject(LibraryProblem.UNKNOWN_LENS)
            policy is RemovePolicy.ReplaceWith && replacement == null -> reject(LibraryProblem.UNKNOWN_LENS)
            replacement == null -> LensLibraryResult.Applied(detach(layout) { it == id }.copy(library = library))
            else -> {
                val retargeted = LensRetarget.run(layout, id, replacement, sources, capabilities)
                LensLibraryResult.Applied(
                    retargeted.layout.copy(library = library),
                    retargeted.impact.wouldBreak.map { it.dependent },
                )
            }
        }
    }

    /**
     * Re-establishes the snapshot invariant (a ref that resolves sets the inline lens to the library's)
     * and reports refs that do not resolve, which keep their snapshot. Used after decode and import.
     */
    fun rehydrate(layout: LayoutWorkspaces): Rehydrated {
        val hydrated = layout.mapBindings { resolve(layout.library, it.binding) }
        return Rehydrated(hydrated, danglingRefs(hydrated))
    }

    /** Turns every dangling ref into an inline lens (the snapshot it already draws). */
    fun detachDangling(layout: LayoutWorkspaces): LayoutWorkspaces = detach(layout) { layout.library.find(it) == null }

    /** "Make independent": every ref in [workspaceId] becomes inline; the library is untouched. */
    fun makeIndependent(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
    ): LayoutWorkspaces =
        layout.replace(workspaceId) { workspace ->
            WorkspaceBindings.map(workspace) { it.binding.copy(ref = null) }
        }
}

private fun reject(problem: LibraryProblem): LensLibraryResult =
    LensLibraryResult.Rejected(LensLibraryRejection.Problem(problem))

private fun detach(
    layout: LayoutWorkspaces,
    which: (LensId) -> Boolean,
): LayoutWorkspaces =
    layout.mapBindings { site ->
        val ref = site.binding.ref
        if (ref != null && which(ref)) site.binding.copy(ref = null) else site.binding
    }
