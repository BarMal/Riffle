package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.BindingSite
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryResult
import com.riffle.core.domain.launcher.workspace.LensNames
import com.riffle.core.domain.launcher.workspace.LensOrigin
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.SiteKind
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceCopy
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds

data class InstalledPreset(
    val layout: LayoutWorkspaces,
    val workspaceId: WorkspaceId,
)

/** One lens of a preset variant, with the library identity it is installed under. */
private data class PlannedLens(
    val origin: LensOrigin.Preset,
    val name: String,
    val lens: Lens,
)

private data class Promoted(
    val library: LensLibrary,
    val workspace: Workspace,
)

/**
 * Installs presets into a layout with their lenses as saved lenses (docs/product/workspaces-lens-library.md).
 *
 * The catalog stays inline data: a preset variant is promoted at install time. Every binding except the
 * home-grid pages (which are tied to the page they show) becomes an entry of the layout's library with
 * origin `Preset(presetId, key)`, `key` being the container's stable part id in the catalog ("finder",
 * "w-media", "dock"). An entry with that origin that already exists is reused as the user left it, so
 * installing the same preset again adds no lens; a name that clashes with another lens gets " (<Preset>)".
 */
object PresetLensInstaller {
    /** Adds [preset]'s [posture] variant to [layout], writing its lenses to the layout's library. */
    fun install(
        layout: LayoutWorkspaces,
        preset: WorkspacePreset,
        posture: PresetPosture,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
        activate: Boolean = false,
    ): InstalledPreset {
        val promoted = promote(layout.library, preset, preset.variant(posture), ids)
        val workspace = WorkspaceCopy.withFreshIds(promoted.workspace, ids)
        return InstalledPreset(layout.copy(library = promoted.library).add(workspace, activate), workspace.id)
    }

    /**
     * Rebuilds [workspaceId]'s arrangement (pages, dock section, gestures) from the catalog, keeping its
     * id and name. Saved lenses are kept as the user left them, except a preset lens the user deleted,
     * which is re-added. With [restoreLenses] every lens with this preset's origin is also reset to the
     * catalog definition (dependents follow; any it would break are detached).
     */
    fun reset(
        layout: LayoutWorkspaces,
        workspaceId: WorkspaceId,
        preset: WorkspacePreset,
        posture: PresetPosture,
        restoreLenses: Boolean = false,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): LayoutWorkspaces {
        val variant = preset.variant(posture)
        if (layout.find(workspaceId) == null) return layout
        val base = if (restoreLenses) restore(layout, planned(preset, variant)) else layout
        val promoted = promote(base.library, preset, variant, ids)
        val fresh = WorkspaceCopy.withFreshIds(promoted.workspace, ids)
        return base.copy(library = promoted.library).replace(workspaceId) {
            it.copy(pages = fresh.pages, dock = fresh.dock, gestureBindings = fresh.gestureBindings)
        }
    }

    private fun restore(
        layout: LayoutWorkspaces,
        plans: List<PlannedLens>,
    ): LayoutWorkspaces =
        plans.fold(layout) { acc, plan ->
            val entry = acc.library.lenses.firstOrNull { it.origin == plan.origin }
            if (entry == null || entry.lens == plan.lens) {
                acc
            } else {
                val result = LensLibraryOps.applyEdit(acc, entry.id, plan.lens, BreakPolicy.DETACH_BROKEN)
                (result as? LensLibraryResult.Applied)?.layout ?: acc
            }
        }

    private fun promote(
        library: LensLibrary,
        preset: WorkspacePreset,
        variant: Workspace,
        ids: WorkspaceIdFactory,
    ): Promoted {
        var current = library
        val mapped =
            WorkspaceBindings.map(variant) { site ->
                val plan = plan(preset, variant, site)
                if (plan == null) {
                    site.binding
                } else {
                    val (next, binding) = ensure(current, plan, site, preset.name, ids)
                    current = next
                    binding
                }
            }
        return Promoted(current, mapped)
    }

    private fun ensure(
        library: LensLibrary,
        plan: PlannedLens,
        site: BindingSite,
        presetName: String,
        ids: WorkspaceIdFactory,
    ): Pair<LensLibrary, LensBinding> {
        val expression = site.binding.expression
        val existing = library.lenses.firstOrNull { it.origin == plan.origin }
        if (existing != null) {
            val reused = LensBinding(existing.lens, expression, existing.id)
            return library to if (accepts(reused, site.kind)) reused else site.binding
        }
        val taken = library.lenses.map { it.name.lowercase() }.toSet()
        val name = LensNames.unique(plan.name, taken) { if (it == 1) " ($presetName)" else " ($presetName $it)" }
        return when (val added = library.tryAdd(name, plan.lens, ids, plan.origin)) {
            is LibraryAdd.Added -> added.library to LensBinding(plan.lens, expression, added.id)
            is LibraryAdd.Rejected -> library to site.binding
        }
    }

    /** A reused (possibly user-edited) lens must still pair with the preset's expression; else stay inline. */
    private fun accepts(
        binding: LensBinding,
        kind: SiteKind,
    ): Boolean =
        if (kind == SiteKind.PAGE_SET) {
            binding.lens.group != LensGroup.None &&
                LensExpressionValidity.checkPerGroup(binding.lens, binding.expression).isValid
        } else {
            LensExpressionValidity.check(binding.lens, binding.expression).isValid
        }

    private fun planned(
        preset: WorkspacePreset,
        variant: Workspace,
    ): List<PlannedLens> = WorkspaceBindings.sites(variant).mapNotNull { plan(preset, variant, it) }

    private fun plan(
        preset: WorkspacePreset,
        variant: Workspace,
        site: BindingSite,
    ): PlannedLens? =
        if (WorkspaceSourceIds.HOME_GRID in site.binding.lens.sources) {
            null
        } else {
            val key = site.containerId?.value?.removePrefix("${variant.id.value}:") ?: "dock"
            val label = key.removePrefix("w-").replace('-', ' ').replaceFirstChar { it.uppercase() }
            PlannedLens(LensOrigin.Preset(preset.id, key), label, site.binding.lens)
        }
}
