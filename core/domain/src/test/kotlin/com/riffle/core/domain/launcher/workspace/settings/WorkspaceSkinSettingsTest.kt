package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceCodec
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSkin
import com.riffle.core.domain.launcher.workspace.preset.PresetPosture
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Q15: a workspace may override the global theme preset; null or an unknown id follows the global one. */
class WorkspaceSkinSettingsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE

    private fun ws(
        id: String,
        skin: String? = null,
    ) = Workspace(
        WorkspaceId(id),
        id,
        listOf(
            PageContainer(
                ContainerId("p-$id"),
                PageContent.Bound(LensBinding(Lens(listOf(SourceId("apps.all"))), ExpressionKind.LIST)),
            ),
        ),
        skinOverrideId = skin,
    )

    private val set =
        WorkspaceSet(
            mapOf(
                phone to LayoutWorkspaces.single(ws("a")).add(ws("b", skin = "GLASS")),
                foldable to LayoutWorkspaces.single(ws("f")),
            ),
        )

    @Test
    fun `resolution order is the workspace override, then the global preset`() {
        val global = LauncherThemePreset.RETRO
        assertEquals(global, WorkspaceSkin.resolve(global, null))
        assertEquals(global, WorkspaceSkin.resolve(global, ws("a")))
        assertEquals(LauncherThemePreset.GLASS, WorkspaceSkin.resolve(global, ws("b", skin = "GLASS")))
        LauncherThemePreset.entries.forEach { preset ->
            assertEquals(preset, WorkspaceSkin.resolve(global, ws("x", skin = WorkspaceSkin.idOf(preset))))
        }
    }

    @Test
    fun `an id that names no preset follows the global preset`() {
        val global = LauncherThemePreset.MATERIAL
        listOf("glass", "skin.nova", "", "  ", "GLASS ").forEach { stored ->
            assertNull(WorkspaceSkin.overrideOf(ws("x", skin = stored)), stored)
            assertEquals(global, WorkspaceSkin.resolve(global, ws("x", skin = stored)), stored)
        }
    }

    @Test
    fun `the stored ids are the enum names, a contract that round trips the workspace codec`() {
        assertEquals(
            listOf("MATERIAL", "MINIMAL", "RETRO", "GLASS", "TERMINAL", "CUSTOM"),
            LauncherThemePreset.entries.map(WorkspaceSkin::idOf),
        )
        val skinned = ws("b", skin = WorkspaceSkin.idOf(LauncherThemePreset.TERMINAL))
        val decoded = WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(skinned))
        assertEquals(LauncherThemePreset.TERMINAL, WorkspaceSkin.overrideOf(decoded))
    }

    @Test
    fun `the page shows each row's override`() {
        val model = WorkspacesSettingsPlanner.plan(set, phone, phone)
        assertEquals(listOf(null, LauncherThemePreset.GLASS), model.rows.map { it.skin })
    }

    @Test
    fun `set skin changes only that workspace and clearing it follows the global preset again`() {
        val terminal = WorkspacesSettingsAction.SetSkin(WorkspaceId("a"), LauncherThemePreset.TERMINAL)
        val change = terminal.applyTo(set, phone)
        assertTrue(change.applied)
        assertFalse(change.undoable)
        assertEquals(WorkspacesSettingsMessage.SkinChanged("a", LauncherThemePreset.TERMINAL), change.message)
        val phoneLayout = change.set.workspacesFor(phone)
        assertEquals("TERMINAL", phoneLayout.find(WorkspaceId("a"))?.skinOverrideId)
        assertEquals("GLASS", phoneLayout.find(WorkspaceId("b"))?.skinOverrideId)
        assertEquals(set.workspacesFor(foldable), change.set.workspacesFor(foldable))

        val cleared = WorkspacesSettingsAction.SetSkin(WorkspaceId("b"), null).applyTo(change.set, phone)
        assertNull(cleared.set.workspacesFor(phone).find(WorkspaceId("b"))?.skinOverrideId)
        assertEquals(WorkspacesSettingsMessage.SkinChanged("b", null), cleared.message)
    }

    @Test
    fun `setting the same skin again or on an unknown workspace changes nothing`() {
        val same = WorkspacesSettingsAction.SetSkin(WorkspaceId("b"), LauncherThemePreset.GLASS).applyTo(set, phone)
        assertFalse(same.applied)
        assertIs<WorkspacesSettingsMessage.NothingToDo>(same.message)
        val unknown = WorkspacesSettingsAction.SetSkin(WorkspaceId("zz"), LauncherThemePreset.GLASS).applyTo(set, phone)
        assertFalse(unknown.applied)
    }

    @Test
    fun `installing a preset never sets a skin`() {
        WorkspacePresets.all.forEach { preset ->
            val change = WorkspacesSettingsAction.InstallPreset(preset.id, activate = true).applyTo(set, phone)
            assertNull(change.set.workspacesFor(phone).active.skinOverrideId, preset.id)
            assertNull(preset.variant(PresetPosture.COMPACT).skinOverrideId)
        }
    }
}
