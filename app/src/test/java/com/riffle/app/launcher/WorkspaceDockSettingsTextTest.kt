package com.riffle.app.launcher

import com.riffle.app.launcher.editor.EditorReasonText
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.NewAppDefaults
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wording of the per-workspace dock and new-app settings (11.2, 9.8). */
class WorkspaceDockSettingsTextTest {
    @Test
    fun dockSummariesNameWhatTheOverrideDoes() {
        assertEquals("Dock: same as the layout", WorkspaceDockSettingsText.dockSummary(null))
        assertEquals("Dock: same as the layout", WorkspaceDockSettingsText.dockSummary(DockPresentation()))
        assertEquals("Dock: hidden", WorkspaceDockSettingsText.dockSummary(DockPresentation(hidden = true)))
        assertEquals(
            "Dock: Left edge, 44 dp",
            WorkspaceDockSettingsText.dockSummary(DockPresentation(edge = DockPosition.LEFT, iconSizeDp = 44)),
        )
    }

    @Test
    fun announcementsNameTheWorkspace() {
        assertEquals(
            "\"Work\" dock: hidden",
            WorkspacesDialogText.message(
                WorkspacesSettingsMessage.DockChanged("Work", DockPresentation(hidden = true)),
            ),
        )
        assertEquals(
            WorkspaceDockSettingsText.HIDDEN_NEEDS_GESTURE,
            WorkspacesDialogText.message(WorkspacesSettingsMessage.DockHiddenNeedsMenuGesture),
        )
        assertEquals(
            "In \"Home\", new apps also get a home icon (the preset's default)",
            WorkspacesDialogText.message(
                WorkspacesSettingsMessage.NewAppsChanged("Home", NewAppDefaults.HOME_AND_PAGES, followsPreset = true),
            ),
        )
        assertEquals(
            "In \"Home\", new apps go only to All apps",
            WorkspacesDialogText.message(
                WorkspacesSettingsMessage.NewAppsChanged("Home", WorkspaceNewApps(), followsPreset = false),
            ),
        )
    }

    @Test
    fun theStrandingReasonIsTheSameInTheEditorAndSettings() {
        assertEquals(
            WorkspaceDockSettingsText.HIDDEN_NEEDS_GESTURE,
            EditorReasonText.workspace(WorkspaceIssue.DockHiddenWithoutMenuEntry),
        )
    }

    @Test
    fun choicesStayWithinTheDockRanges() {
        WorkspaceDockSettingsText.iconSizeChoices.filterNotNull().forEach {
            assertTrue(it in MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP)
        }
        assertEquals(null, WorkspaceDockSettingsText.edgeChoices.first())
        assertEquals("Same as the layout's dock", WorkspaceDockSettingsText.edgeLabel(null))
        assertEquals("Only in All apps", WorkspaceDockSettingsText.placementLabel(NewAppPlacement.FINDER_ONLY))
    }
}
