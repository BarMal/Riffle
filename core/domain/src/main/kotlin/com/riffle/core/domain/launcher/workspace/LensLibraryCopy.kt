package com.riffle.core.domain.launcher.workspace

/** A library copied with fresh ids, and the old-id to new-id map used to rewrite refs. */
internal data class CopiedLibrary(
    val library: LensLibrary,
    val idMap: Map<LensId, LensId>,
)

/** Cross-layout copy support: refs cannot cross layouts, so a copied workspace gets a copied library. */
internal object LensLibraryCopy {
    /** Every entry (referenced or not) with a fresh id; names, order, definitions and origins are kept. */
    fun copy(
        library: LensLibrary,
        ids: WorkspaceIdFactory,
    ): CopiedLibrary {
        val copies = library.lenses.map { it.id to it.copy(id = LensId(ids.next())) }
        return CopiedLibrary(LensLibrary(copies.map { it.second }), copies.associate { (old, new) -> old to new.id })
    }

    /** Rewrites refs through [idMap]; a ref it does not know (already dangling) is left as it is. */
    fun remap(
        workspace: Workspace,
        idMap: Map<LensId, LensId>,
    ): Workspace =
        WorkspaceBindings.map(workspace) { site ->
            val ref = site.binding.ref
            if (ref == null || ref !in idMap) site.binding else site.binding.copy(ref = idMap[ref])
        }
}
