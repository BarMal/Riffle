package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.LayoutFallbackNotice
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesSettingsTextTest {
    private fun row(
        name: String = "Inbox",
        active: Boolean = false,
        default: Boolean = false,
        pages: Int = 3,
        preset: String? = null,
    ) = WorkspaceRow(
        id = WorkspaceId(name),
        name = name,
        isActive = active,
        isDefault = default,
        pageCount = pages,
        presetName = preset,
        canDelete = true,
        canMoveUp = true,
        canMoveDown = true,
    )

    @Test
    fun everyLayoutHasAHumanNameNotAnEnumName() {
        val names = HomeLayoutDeviceClass.entries.map { WorkspacesSettingsText.layoutName(it) }

        assertEquals(
            // N10: a landscape phone is the phone layout, so it carries the same name.
            listOf("Phone (folded)", "Phone (folded)", "Foldable (unfolded)", "Tablet", "Desktop"),
            names,
        )
        assertTrue(names.none { name -> name.any { it == '_' } || name == name.uppercase() })
    }

    @Test
    fun theLayoutNoteSaysWhichLayoutIsBeingEdited() {
        assertTrue(
            WorkspacesSettingsText.editingLayout(HomeLayoutDeviceClass.PHONE, isCurrent = true)
                .contains("Phone (folded)"),
        )
        val other = WorkspacesSettingsText.editingLayout(HomeLayoutDeviceClass.FOLDABLE, isCurrent = false)
        assertTrue(other.contains("Foldable (unfolded)") && other.contains("not the layout this device is showing"))
    }

    @Test
    fun rowSummaryNamesActiveDefaultPresetAndPages() {
        assertEquals(
            "Active · Default · Nova preset · 3 pages",
            WorkspacesSettingsText.rowSummary(row(active = true, default = true, preset = "Nova")),
        )
        assertEquals("1 page", WorkspacesSettingsText.rowSummary(row(pages = 1)))
    }

    @Test
    fun theSpokenSummaryStatesWhatTappingDoes() {
        assertEquals(
            "Inbox, Active · 3 pages",
            WorkspacesSettingsText.rowSpokenSummary(row(active = true)),
        )
        assertTrue(WorkspacesSettingsText.rowSpokenSummary(row()).endsWith("Double tap to switch to it"))
    }

    @Test
    fun theFallbackNoticeSaysWhatTheLayoutCannotDrawAndWhatIsUsedInstead() {
        val text =
            WorkspacesSettingsText.fallbackNotice(
                LayoutFallbackNotice("Index", "Standard", listOf(ExpressionKind.INDEX, ExpressionKind.CARD_STACK), 0),
            )

        assertTrue(text.contains("can't draw Index and Card stack"))
        assertTrue(text.contains("using the default workspace, \"Standard\""))
    }

    @Test
    fun theCopyExplanationIsAOneTimeCopyThatStatesWhatIsReplaced() {
        val text =
            WorkspacesDialogText.copyBody(
                source = HomeLayoutDeviceClass.FOLDABLE,
                target = HomeLayoutDeviceClass.PHONE,
                replaced = 3,
                copied = 1,
            )

        assertTrue(text.contains("one-time copy"))
        assertTrue(text.contains("replaces the 3 workspaces on Phone (folded)"))
        assertTrue(text.contains("1 workspace on Foldable (unfolded)"))
        assertTrue(text.contains("independent"))
        assertTrue(text.contains("home screen items are not copied"))
        assertTrue(text.contains("undo"))
    }

    @Test
    fun destructiveConfirmationsSayWhatStaysAndPromiseUndo() {
        val delete = WorkspacesDialogText.deleteBody("Work")
        assertTrue(delete.contains("\"Work\"") && delete.contains("home screen") && delete.contains("undo"))
        val reset = WorkspacesDialogText.resetBody("Work", "Nova")
        assertTrue(reset.contains("Nova preset") && reset.contains("The name stays") && reset.contains("undo"))
    }

    @Test
    fun everyMessageReadsAsAFullSentenceForTalkBack() {
        val messages =
            listOf(
                WorkspacesSettingsMessage.Activated("A"),
                WorkspacesSettingsMessage.Renamed("A"),
                WorkspacesSettingsMessage.Duplicated("A"),
                WorkspacesSettingsMessage.Moved("A"),
                WorkspacesSettingsMessage.MadeDefault("A"),
                WorkspacesSettingsMessage.Deleted("A"),
                WorkspacesSettingsMessage.Reset("A", "Nova"),
                WorkspacesSettingsMessage.Installed("A"),
                WorkspacesSettingsMessage.SkinChanged("A", LauncherThemePreset.GLASS),
                WorkspacesSettingsMessage.Copied(HomeLayoutDeviceClass.TABLET, replaced = 2, copied = 1),
                WorkspacesSettingsMessage.CannotDeleteLast,
                WorkspacesSettingsMessage.InvalidName,
                WorkspacesSettingsMessage.NoKnownPreset,
                WorkspacesSettingsMessage.NothingToDo,
            )

        messages.forEach { assertTrue(WorkspacesDialogText.message(it).isNotBlank()) }
        assertEquals("Deleted \"A\"", WorkspacesDialogText.message(WorkspacesSettingsMessage.Deleted("A")))
        assertEquals(
            "Copied 1 workspace from Tablet",
            WorkspacesDialogText.message(WorkspacesSettingsMessage.Copied(HomeLayoutDeviceClass.TABLET, 2, 1)),
        )
        assertFalse(WorkspacesDialogText.message(WorkspacesSettingsMessage.CannotDeleteLast).isEmpty())
    }

    @Test
    fun theAppearanceOverrideIsNamedLikeSettingsAppearanceAndFollowingGlobalIsSaid() {
        assertEquals("Glass", WorkspacesSettingsText.skinLabel(LauncherThemePreset.GLASS))
        assertEquals("Follow global appearance", WorkspacesSettingsText.skinLabel(null))
        assertEquals(
            "Active · Terminal theme · 3 pages",
            WorkspacesSettingsText.rowSummary(row(active = true).copy(skin = LauncherThemePreset.TERMINAL)),
        )
        assertEquals(
            "\"A\" uses the Retro theme",
            WorkspacesDialogText.message(WorkspacesSettingsMessage.SkinChanged("A", LauncherThemePreset.RETRO)),
        )
        assertEquals(
            "\"A\" follows the global appearance",
            WorkspacesDialogText.message(WorkspacesSettingsMessage.SkinChanged("A", null)),
        )
    }
}
