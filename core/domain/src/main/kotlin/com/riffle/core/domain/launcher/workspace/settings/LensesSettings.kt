package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensDependent
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensNames
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.ExpressionOptions

/** One saved lens of the layout, as the list shows it. */
data class LensRow(
    val id: LensId,
    val name: String,
    val sources: List<SourceId>,
    /** Draws groups (grouped lens) rather than a flat list. */
    val grouped: Boolean,
    /** How many containers of this layout's workspaces use it. */
    val usedIn: Int,
    /** Installed by a preset rather than made by the user. */
    val fromPreset: Boolean,
)

/** Another layout a lens can be copied to, with what it already holds. */
data class LensCopyTarget(
    val layout: HomeLayoutDeviceClass,
    /** Saved lenses already in that layout. */
    val count: Int,
    /** It has no room for another. */
    val full: Boolean,
    /** The name the copy would get there (the lens's own name unless it is taken, then "<name> 2"). */
    val newName: String,
)

/** The Saved lenses page for one layout. [rows] are sorted by name; the list is empty-safe. */
data class LensesSettingsModel(
    val layout: HomeLayoutDeviceClass,
    val rows: List<LensRow>,
    val limit: Int = MAX_SAVED_LENSES,
) {
    val isFull: Boolean get() = rows.size >= limit
}

/** Where in a workspace a container that uses a saved lens is. Positions are 1-based. */
sealed interface UsePlace {
    data class Page(val position: Int, val kind: PageKind) : UsePlace

    /** A widget on the grid page at [pagePosition]. */
    data class Widget(val pagePosition: Int) : UsePlace

    /** The dock's dynamic section. */
    data object Dock : UsePlace
}

enum class PageKind {
    PAGE,
    PAGE_SET,
    FINDER,
}

/** One container that uses a saved lens: "Standard > Inbox (page-set)". */
data class UsedByRow(
    val workspaceId: WorkspaceId,
    val workspaceName: String,
    val containerId: ContainerId?,
    val place: UsePlace,
    val expression: ExpressionKind,
)

/** A user an edit would break, with why (the domain's own [WorkspaceIssue]s). */
data class BrokenUse(
    val use: UsedByRow,
    val issues: List<WorkspaceIssue>,
)

/** Why the lens being built cannot be saved yet. */
sealed interface LensProblem {
    /** A lens reads at least one source. */
    data object NoSource : LensProblem

    /** No expression of this layout can draw the lens as built. */
    data object NothingCanDraw : LensProblem

    /** The name or the library refuses it ([LibraryProblem]). */
    data class Library(val problem: LibraryProblem) : LensProblem
}

/**
 * The lens detail page as data: who uses the lens, what is wrong with the draft, which expressions can draw it and
 * which users an edit would break. [canSave] is false while [problems] is not empty or nothing changed; when
 * [broken] is not empty Save is offered as a choice (detach the broken ones, or keep the original and save a new
 * lens) and never silently breaks a container (design 4.4).
 */
data class LensDetailModel(
    val isNew: Boolean,
    val usedBy: List<UsedByRow>,
    val problems: List<LensProblem>,
    val broken: List<BrokenUse>,
    /** Expressions that can draw the draft on this layout (a grouped lens also offers the per-page ones). */
    val drawableAs: List<ExpressionKind>,
    val changed: Boolean,
) {
    val canSave: Boolean get() = problems.isEmpty() && changed

    val needsChoice: Boolean get() = canSave && broken.isNotEmpty()
}

object LensesSettingsPlanner {
    /** The layout's saved lenses by name (case-insensitive), with how many containers use each. */
    fun plan(
        set: WorkspaceSet,
        viewed: HomeLayoutDeviceClass,
    ): LensesSettingsModel {
        val layout = set.workspacesFor(viewed)
        val rows =
            layout.library.lenses.map { saved ->
                LensRow(
                    id = saved.id,
                    name = saved.name,
                    sources = saved.lens.sources,
                    grouped = saved.lens.group != LensGroup.None,
                    usedIn = LensLibraryOps.dependents(layout, saved.id).size,
                    fromPreset = saved.origin != null,
                )
            }
        return LensesSettingsModel(viewed, rows.sortedBy { it.name.lowercase() })
    }

    /** The other layouts [id] can be copied to, with what each holds. Empty for an unknown lens. */
    fun copyTargets(
        set: WorkspaceSet,
        viewed: HomeLayoutDeviceClass,
        id: LensId,
        available: Collection<HomeLayoutDeviceClass>,
    ): List<LensCopyTarget> {
        val saved = set.workspacesFor(viewed).library.find(id) ?: return emptyList()
        return available.filter { it != viewed }.distinct().map { target ->
            val library = set.workspacesFor(target).library
            LensCopyTarget(
                layout = target,
                count = library.lenses.size,
                full = library.lenses.size >= MAX_SAVED_LENSES,
                newName = LensNames.unique(saved.name, LensNames.of(library.lenses)) { " ${it + 1}" },
            )
        }
    }

    /** The lenses [id] could be replaced by when deleted: every other one, by name. */
    fun replacements(
        layout: LayoutWorkspaces,
        id: LensId,
    ): List<SavedLens> = layout.library.lenses.filter { it.id != id }.sortedBy { it.name.lowercase() }
}

object LensDetailPlanner {
    /**
     * Plans the detail page for [id] (null: a new lens) with the draft [name] and [lens] (null while no source is
     * chosen). [descriptors] are the registry's; null skips source-dependent checks, as everywhere in the domain.
     */
    fun plan(
        layout: LayoutWorkspaces,
        id: LensId?,
        name: String,
        lens: Lens?,
        descriptors: List<SourceDescriptor>?,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): LensDetailModel {
        val saved = id?.let(layout.library::find)
        val drawable = lens?.let { drawableAs(it, descriptors, capabilities) }.orEmpty()
        val problems = problems(layout, saved, name, lens, drawable)
        val changed = saved == null || lens != saved.lens || name.trim() != saved.name
        val impact =
            if (saved != null && lens != null && lens != saved.lens) {
                LensLibraryOps.previewEdit(layout, saved.id, lens, descriptors, capabilities)
            } else {
                null
            }
        return LensDetailModel(
            isNew = saved == null,
            usedBy = saved?.let { usedBy(layout, it.id) }.orEmpty(),
            problems = problems,
            broken = impact?.wouldBreak.orEmpty().map { BrokenUse(row(layout, it.dependent), it.issues) },
            drawableAs = drawable,
            changed = changed,
        )
    }

    /** Every container of [layout] that uses [id], in workspace and page order. */
    fun usedBy(
        layout: LayoutWorkspaces,
        id: LensId,
    ): List<UsedByRow> = LensLibraryOps.dependents(layout, id).map { row(layout, it) }

    /** The expressions that can draw [lens] here: the whole lens, or (grouped) each group on its own page. */
    fun drawableAs(
        lens: Lens,
        descriptors: List<SourceDescriptor>?,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
    ): List<ExpressionKind> =
        ExpressionOptions.forLens(lens, EditContext(capabilities, descriptors)).filter { it.enabled }.map { it.kind }

    private fun problems(
        layout: LayoutWorkspaces,
        saved: SavedLens?,
        name: String,
        lens: Lens?,
        drawable: List<ExpressionKind>,
    ): List<LensProblem> =
        listOfNotNull(
            LensProblem.NoSource.takeIf { lens == null },
            LensProblem.NothingCanDraw.takeIf { lens != null && drawable.isEmpty() },
            libraryProblem(layout, saved, name)?.let { LensProblem.Library(it) },
        )

    /** The name's problem, else (for a new lens) a full library. */
    private fun libraryProblem(
        layout: LayoutWorkspaces,
        saved: SavedLens?,
        name: String,
    ): LibraryProblem? =
        layout.library.nameProblem(name, saved?.id)
            ?: LibraryProblem.LIBRARY_FULL.takeIf { saved == null && layout.library.lenses.size >= MAX_SAVED_LENSES }

    private fun row(
        layout: LayoutWorkspaces,
        dependent: LensDependent,
    ): UsedByRow {
        val workspace = layout.find(dependent.workspaceId)
        return UsedByRow(
            workspaceId = dependent.workspaceId,
            workspaceName = workspace?.name.orEmpty(),
            containerId = dependent.containerId,
            place = workspace?.let { placeOf(it, dependent) } ?: UsePlace.Dock,
            expression = dependent.expression,
        )
    }

    private fun placeOf(
        workspace: Workspace,
        dependent: LensDependent,
    ): UsePlace {
        val containerId = dependent.containerId ?: return UsePlace.Dock
        val index =
            workspace.pages.indexOfFirst { page ->
                page.id == containerId ||
                    ((page as? PageContainer)?.content as? PageContent.WidgetGrid)
                        ?.placements?.any { it.widget.id == containerId } == true
            }
        val page = workspace.pages.getOrNull(index)
        val position = index + 1
        return when {
            page == null -> UsePlace.Dock
            page.id != containerId -> UsePlace.Widget(position)
            page is PageSetContainer -> UsePlace.Page(position, PageKind.PAGE_SET)
            (page as? PageContainer)?.role == PageRole.FINDER -> UsePlace.Page(position, PageKind.FINDER)
            else -> UsePlace.Page(position, PageKind.PAGE)
        }
    }
}
