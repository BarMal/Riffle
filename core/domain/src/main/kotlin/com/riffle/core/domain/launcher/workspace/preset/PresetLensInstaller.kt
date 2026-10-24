package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.BindingSite
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
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
import com.riffle.core.domain.launcher.workspace.withOrigin

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
 * origin `Preset(presetId, key, installed)`, `key` being the container's stable part id in the catalog
 * ("finder", "w-media", "dock") and `installed` the catalog definition it was installed with. An entry with
 * that origin that already exists is reused, so installing the same preset again adds no lens; a name that
 * clashes with another lens gets " (<Preset>)".
 *
 * Reinstalling or resetting refreshes untouched entries (owner decision, #1363): an entry whose lens still
 * equals its installed definition takes the current catalog definition (dependents follow, through
 * [LensLibraryOps.applyEdit]); an entry the user edited is left alone; a rename alone is not an edit. An
 * entry without a recorded definition (installed before it was recorded) counts as edited, unless it already
 * equals the catalog, in which case the definition is recorded.
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
        val variant = preset.variant(posture)
        val base = refresh(layout, planned(preset, variant), all = false)
        val promoted = promote(base.library, preset, variant, ids)
        val workspace = WorkspaceCopy.withFreshIds(promoted.workspace, ids)
        return InstalledPreset(base.copy(library = promoted.library).add(workspace, activate), workspace.id)
    }

    /**
     * Rebuilds [workspaceId]'s arrangement (pages, dock section, gestures) from the catalog, keeping its
     * id and name. Edited saved lenses are kept as the user left them (untouched ones are refreshed, as on
     * install), and a preset lens the user deleted is re-added. With [restoreLenses] every lens with this
     * preset's origin is reset to the catalog definition, edited or not (dependents follow; any it would
     * break are detached).
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
        val base = refresh(layout, planned(preset, variant), all = restoreLenses)
        val promoted = promote(base.library, preset, variant, ids)
        val fresh = WorkspaceCopy.withFreshIds(promoted.workspace, ids)
        return base.copy(library = promoted.library).replace(workspaceId) {
            it.copy(pages = fresh.pages, dock = fresh.dock, gestureBindings = fresh.gestureBindings)
        }
    }

    /**
     * Sets each existing entry of [plans] to the catalog definition when it is untouched (or every one when
     * [all]) and records that definition as installed. Edited entries are left exactly as they are.
     */
    private fun refresh(
        layout: LayoutWorkspaces,
        plans: List<PlannedLens>,
        all: Boolean,
    ): LayoutWorkspaces =
        plans.distinctBy { it.origin.key }.fold(layout) { acc, plan ->
            val entry = acc.library.lenses.firstOrNull { plan.origin.sameCatalogLens(it.origin) }
            when {
                entry == null -> acc
                entry.lens == plan.lens -> acc.recordInstalled(entry.id, plan.origin)
                all || entry.isUntouchedPresetLens -> {
                    val result = LensLibraryOps.applyEdit(acc, entry.id, plan.lens, BreakPolicy.DETACH_BROKEN)
                    (result as? LensLibraryResult.Applied)?.layout?.recordInstalled(entry.id, plan.origin) ?: acc
                }
                else -> acc
            }
        }

    private fun LayoutWorkspaces.recordInstalled(
        id: LensId,
        origin: LensOrigin.Preset,
    ): LayoutWorkspaces =
        if (library.find(id)?.origin == origin) this else copy(library = library.withOrigin(id, origin))

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
        val existing = library.lenses.firstOrNull { plan.origin.sameCatalogLens(it.origin) }
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
            PlannedLens(LensOrigin.Preset(preset.id, key, site.binding.lens), label, site.binding.lens)
        }
}
