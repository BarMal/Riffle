package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BindingSite
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensOrigin
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PresetLensInstallerTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private var counter = 0

    private fun ids(prefix: String): WorkspaceIdFactory = WorkspaceIdFactory { "$prefix-${counter++}" }

    private fun base(): LayoutWorkspaces = WorkspaceMigration.defaultFor(phone)

    private fun userEdit(lens: Lens) = lens.copy(sort = LensSort(LensSortField.TITLE, SortDirection.DESCENDING))

    private fun sites(workspace: Workspace): List<BindingSite> = WorkspaceBindings.sites(workspace)

    /** The workspace with ids stripped, so two installs of one preset compare equal. */
    private fun shape(
        layout: LayoutWorkspaces,
        workspace: Workspace,
    ) = sites(workspace).map { site ->
        val saved = site.binding.ref?.let(layout.library::find)
        Triple(site.binding.lens, site.binding.expression, saved?.origin)
    }

    @Test
    fun `install writes the preset lenses to the library with origins and refs`() {
        val installed = PresetLensInstaller.install(base(), WorkspacePresets.ios, PresetPosture.COMPACT, ids("a"))
        val layout = installed.layout
        val workspace = checkNotNull(layout.find(installed.workspaceId))
        val origins = layout.library.lenses.map { it.origin }
        assertTrue(origins.isNotEmpty())
        assertTrue(origins.all { it is LensOrigin.Preset && it.presetId == "ios" })
        // Everything except the home grid page is a saved lens.
        val refs = sites(workspace).filter { it.binding.ref != null }
        assertEquals(sites(workspace).size - 1, refs.size)
        assertTrue(refs.all { layout.library.find(checkNotNull(it.binding.ref))?.lens == it.binding.lens })
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty())
        assertTrue(WorkspaceValidation.validate(workspace).isEmpty())
    }

    @Test
    fun `installing a preset twice reuses its library entries`() {
        val once = PresetLensInstaller.install(base(), WorkspacePresets.timeScape, PresetPosture.COMPACT, ids("a"))
        val twice =
            PresetLensInstaller.install(
                once.layout,
                WorkspacePresets.timeScape,
                PresetPosture.COMPACT,
                ids("b"),
            )
        assertEquals(once.layout.library, twice.layout.library)
        val first = checkNotNull(twice.layout.find(once.workspaceId))
        val second = checkNotNull(twice.layout.find(twice.workspaceId))
        assertEquals(shape(twice.layout, first), shape(twice.layout, second))
        assertEquals(
            sites(first).map { it.binding.ref },
            sites(second).map { it.binding.ref },
        )
    }

    @Test
    fun `a name that clashes with a user lens gets the preset suffix and the user lens is untouched`() {
        val user =
            LensLibrary().tryAdd(
                "Finder",
                com.riffle.core.domain.launcher.workspace.Lens(
                    listOf(com.riffle.core.domain.launcher.workspace.SourceId("apps")),
                ),
                ids("u"),
            ) as LibraryAdd.Added
        val layout = base().copy(library = user.library)
        val installed = PresetLensInstaller.install(layout, WorkspacePresets.nova, PresetPosture.COMPACT, ids("a"))
        val names = installed.layout.library.lenses.map { it.name }
        assertEquals("Finder", names.first())
        assertTrue("Finder (Nova)" in names)
        assertEquals(user.library.lenses.first(), installed.layout.library.lenses.first())
        val again =
            PresetLensInstaller.install(
                installed.layout,
                WorkspacePresets.nova,
                PresetPosture.COMPACT,
                ids("b"),
            )
        assertEquals(installed.layout.library, again.layout.library)
    }

    @Test
    fun `reinstall keeps a user's edit to a preset lens and falls back to inline when it no longer pairs`() {
        val installed = PresetLensInstaller.install(base(), WorkspacePresets.nova, PresetPosture.COMPACT, ids("a"))
        val finder = installed.layout.library.lenses.first { (it.origin as? LensOrigin.Preset)?.key == "finder" }
        val edited = finder.lens.copy(limit = 3)
        val applied =
            LensLibraryOps.applyEdit(
                installed.layout,
                finder.id,
                edited,
            ) as com.riffle.core.domain.launcher.workspace.LensLibraryResult.Applied
        val again = PresetLensInstaller.install(applied.layout, WorkspacePresets.nova, PresetPosture.COMPACT, ids("b"))
        val second = checkNotNull(again.layout.find(again.workspaceId))
        assertTrue(sites(second).any { it.binding.ref == finder.id && it.binding.lens == edited })
        assertTrue(WorkspaceValidation.validate(second).isEmpty())
    }

    @Test
    fun `reset restores the arrangement, re-adds a deleted lens and keeps edits unless asked`() {
        val installed = PresetLensInstaller.install(base(), WorkspacePresets.ios, PresetPosture.COMPACT, ids("a"))
        val workspaceId = installed.workspaceId
        val original = checkNotNull(installed.layout.find(workspaceId))
        val lib = installed.layout.library
        val first = lib.lenses.first()
        val second = lib.lenses.last()

        // The user edits one preset lens, deletes another, and rearranges the workspace.
        val edited =
            LensLibraryOps.applyEdit(installed.layout, first.id, userEdit(first.lens))
                as com.riffle.core.domain.launcher.workspace.LensLibraryResult.Applied
        val deleted =
            LensLibraryOps.remove(edited.layout, second.id)
                as com.riffle.core.domain.launcher.workspace.LensLibraryResult.Applied
        val rearranged = deleted.layout.replace(workspaceId) { it.copy(pages = it.pages.take(1)) }

        val kept =
            PresetLensInstaller.reset(
                rearranged,
                workspaceId,
                WorkspacePresets.ios,
                PresetPosture.COMPACT,
                false,
                ids("r"),
            )
        val keptWs = checkNotNull(kept.find(workspaceId))
        assertEquals(original.name, keptWs.name)
        assertEquals(original.pages.size, keptWs.pages.size)
        assertEquals(userEdit(first.lens), kept.library.find(first.id)?.lens)
        assertTrue(kept.library.lenses.any { it.origin == second.origin })
        assertTrue(LensLibraryOps.danglingRefs(kept).isEmpty())
        assertTrue(WorkspaceValidation.validate(keptWs).isEmpty())

        val restored =
            PresetLensInstaller.reset(
                rearranged,
                workspaceId,
                WorkspacePresets.ios,
                PresetPosture.COMPACT,
                true,
                ids("s"),
            )
        assertEquals(first.lens, restored.library.find(first.id)?.lens)
        assertTrue(LensLibraryOps.danglingRefs(restored).isEmpty())
        assertNotNull(restored.find(workspaceId))
    }

    @Test
    fun `addPreset on a set goes through the installer`() {
        val set =
            PresetInstaller.addPreset(
                WorkspaceSet(),
                phone,
                WorkspacePresets.niagara,
                activate = true,
                ids = ids("n"),
            )
        val layout = set.workspacesFor(phone)
        assertEquals("Niagara", layout.active.name)
        assertTrue(layout.library.lenses.isNotEmpty())
    }
}
