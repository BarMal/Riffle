package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryResult
import com.riffle.core.domain.launcher.workspace.LensOrigin
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.backup.WorkspaceBackup
import com.riffle.core.domain.launcher.workspace.withOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner decision (#1363): reinstalling or resetting a preset refreshes the library lenses the user never edited. */
class PresetLensRefreshTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private var counter = 0

    private fun ids(prefix: String): WorkspaceIdFactory = WorkspaceIdFactory { "$prefix-${counter++}" }

    private fun base(): LayoutWorkspaces = WorkspaceMigration.defaultFor(phone)

    /** The catalog lens a later release ships for Nova's Finder. */
    private fun newer(lens: Lens) = lens.copy(sort = LensSort(LensSortField.TITLE, SortDirection.DESCENDING))

    /** Nova as a later release would ship it: the Finder lens changed, everything else equal. */
    private fun newerNova(): WorkspacePreset {
        val nova = WorkspacePresets.nova

        fun bump(workspace: Workspace) =
            WorkspaceBindings.map(workspace) { site ->
                if (site.containerId?.value?.endsWith(":finder") == true) {
                    site.binding.copy(lens = newer(site.binding.lens))
                } else {
                    site.binding
                }
            }
        return nova.copy(compact = bump(nova.compact), expanded = bump(nova.expanded))
    }

    private fun LayoutWorkspaces.finder(): SavedLens =
        library.lenses.first { (it.origin as? LensOrigin.Preset)?.key == "finder" }

    private fun installNova(): LayoutWorkspaces =
        PresetLensInstaller.install(base(), WorkspacePresets.nova, PresetPosture.COMPACT, ids("a")).layout

    private fun assertRefsFollowLibrary(layout: LayoutWorkspaces) {
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty())
        layout.workspaces.forEach { workspace ->
            assertTrue(WorkspaceValidation.validate(workspace).isEmpty())
            WorkspaceBindings.sites(workspace).forEach { site ->
                site.binding.ref?.let { ref -> assertEquals(layout.library.find(ref)?.lens, site.binding.lens) }
            }
        }
    }

    @Test
    fun `install records the catalog definition each entry was installed with`() {
        val layout = installNova()
        val finder = layout.finder()
        assertEquals(finder.lens, (finder.origin as LensOrigin.Preset).installed)
        assertTrue(finder.isUntouchedPresetLens)
    }

    @Test
    fun `reinstalling refreshes an untouched entry to the new catalog definition and its dependents follow`() {
        val first = installNova()
        val before = first.finder()
        val again = PresetLensInstaller.install(first, newerNova(), PresetPosture.COMPACT, ids("b"))
        val after = again.layout.finder()

        assertEquals(before.id, after.id)
        assertEquals(newer(before.lens), after.lens)
        assertEquals(newer(before.lens), (after.origin as LensOrigin.Preset).installed)
        assertEquals(1, again.layout.library.lenses.count { (it.origin as? LensOrigin.Preset)?.key == "finder" })
        // Both the first install's workspace and the new one draw the refreshed lens through the same ref.
        val refs = again.layout.workspaces.flatMap(WorkspaceBindings::sites).filter { it.binding.ref == after.id }
        assertEquals(2, refs.size)
        assertRefsFollowLibrary(again.layout)
    }

    @Test
    fun `reinstalling leaves an edited entry alone`() {
        val first = installNova()
        val finder = first.finder()
        val edited = finder.lens.copy(limit = 3)
        val applied = LensLibraryOps.applyEdit(first, finder.id, edited) as LensLibraryResult.Applied

        val again = PresetLensInstaller.install(applied.layout, newerNova(), PresetPosture.COMPACT, ids("b"))

        assertEquals(edited, again.layout.finder().lens)
        assertEquals(finder.lens, (again.layout.finder().origin as LensOrigin.Preset).installed)
        assertFalse(again.layout.finder().isUntouchedPresetLens)
        assertRefsFollowLibrary(again.layout)
    }

    @Test
    fun `a rename alone keeps the entry untouched and the user's name is kept`() {
        val first = installNova()
        val finder = first.finder()
        val renamed = first.copy(library = first.library.rename(finder.id, "My drawer"))
        assertTrue(renamed.finder().isUntouchedPresetLens)

        val again = PresetLensInstaller.install(renamed, newerNova(), PresetPosture.COMPACT, ids("b"))

        assertEquals("My drawer", again.layout.finder().name)
        assertEquals(newer(finder.lens), again.layout.finder().lens)
    }

    @Test
    fun `reset refreshes untouched entries without being asked and keeps edited ones`() {
        val installed = PresetLensInstaller.install(base(), WorkspacePresets.nova, PresetPosture.COMPACT, ids("a"))
        val reset =
            PresetLensInstaller.reset(installed.layout, installed.workspaceId, newerNova(), PresetPosture.COMPACT)
        assertEquals(newer(installed.layout.finder().lens), reset.finder().lens)
        assertRefsFollowLibrary(reset)

        val finder = installed.layout.finder()
        val edited =
            LensLibraryOps.applyEdit(installed.layout, finder.id, finder.lens.copy(limit = 3))
                as LensLibraryResult.Applied
        val kept = PresetLensInstaller.reset(edited.layout, installed.workspaceId, newerNova(), PresetPosture.COMPACT)
        assertEquals(finder.lens.copy(limit = 3), kept.finder().lens)

        // Restoring lenses overrides the edit and makes the entry untouched again.
        val restored =
            PresetLensInstaller.reset(
                edited.layout,
                installed.workspaceId,
                newerNova(),
                PresetPosture.COMPACT,
                restoreLenses = true,
            )
        assertEquals(newer(finder.lens), restored.finder().lens)
        assertTrue(restored.finder().isUntouchedPresetLens)
        assertRefsFollowLibrary(restored)
    }

    @Test
    fun `an entry without a recorded definition counts as edited unless it equals the catalog`() {
        val first = installNova()
        val finder = first.finder()
        val legacyOrigin = LensOrigin.Preset("nova", "finder")
        val legacy = first.copy(library = first.library.withOrigin(finder.id, legacyOrigin))
        assertFalse(legacy.finder().isUntouchedPresetLens)

        // Not refreshed: nothing says the user left it alone.
        val changed = PresetLensInstaller.install(legacy, newerNova(), PresetPosture.COMPACT, ids("b")).layout
        assertEquals(finder.lens, changed.finder().lens)
        assertNull((changed.finder().origin as LensOrigin.Preset).installed)

        // Equal to the catalog: the definition is recorded, so a later release can refresh it.
        val same = PresetLensInstaller.install(legacy, WorkspacePresets.nova, PresetPosture.COMPACT, ids("c")).layout
        assertEquals(finder.lens, (same.finder().origin as LensOrigin.Preset).installed)
    }

    @Test
    fun `the installed definition survives the codec and a backup round trip`() {
        val layout = installNova()
        val set = WorkspaceSet(mapOf(phone to layout))
        val decoded = WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set))
        assertEquals(layout.library, decoded!!.workspacesFor(phone).library)

        val restored =
            WorkspaceBackup.restoreSet(
                WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(WorkspaceBackup.exportSet(set)!!)),
            )!!.set.workspacesFor(phone)
        assertEquals(layout.library, restored.library)
        assertTrue(restored.finder().isUntouchedPresetLens)
        // After the restore, a newer catalog still refreshes the untouched entry.
        val again = PresetLensInstaller.install(restored, newerNova(), PresetPosture.COMPACT, ids("b"))
        assertNotEquals(layout.finder().lens, again.layout.finder().lens)
    }
}
