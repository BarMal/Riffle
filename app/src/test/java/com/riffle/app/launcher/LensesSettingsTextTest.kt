package com.riffle.app.launcher

import com.riffle.app.launcher.editor.EditorReasonText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.settings.BrokenUse
import com.riffle.core.domain.launcher.workspace.settings.LensCopyTarget
import com.riffle.core.domain.launcher.workspace.settings.LensProblem
import com.riffle.core.domain.launcher.workspace.settings.LensRow
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsMessage
import com.riffle.core.domain.launcher.workspace.settings.PageKind
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.UsePlace
import com.riffle.core.domain.launcher.workspace.settings.UsedByRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensesSettingsTextTest {
    private val notes = SourceIds.NOTIFICATIONS
    private val row =
        LensRow(
            LensId("a"),
            "By app",
            listOf(notes, SourceIds.ALL_APPS),
            grouped = true,
            usedIn = 2,
            fromPreset = false,
        )

    @Test
    fun aRowSaysItsSourcesShapeAndUsage() {
        assertEquals("Notifications, All apps · grouped · used in 2 places", LensesSettingsText.rowSummary(row))
        assertEquals(
            "Notifications, All apps · flat · not used · from a preset",
            LensesSettingsText.rowSummary(row.copy(grouped = false, usedIn = 0, fromPreset = true)),
        )
        assertTrue(LensesSettingsText.rowSummary(row.copy(usedIn = 1)).endsWith("used in 1 place"))
    }

    @Test
    fun theSpokenSummaryHasTheNamePositionAndWhatTappingDoes() {
        assertEquals(
            "By app. Notifications, All apps · grouped · used in 2 places. Lens 3 of 7. Double tap to open",
            LensesSettingsText.rowSpokenSummary(row, 3, 7),
        )
    }

    @Test
    fun theListHeadingNamesTheLayoutAndTheCountAgainstTheLimit() {
        assertEquals(
            "Saved lenses for Phone (folded): 1 lens of 100",
            LensesSettingsText.listHeading(HomeLayoutDeviceClass.PHONE, 1),
        )
        assertEquals("3 lenses", LensesSettingsText.lenses(3))
    }

    @Test
    fun everyProblemHasAClearSentenceThatNamesTheLimits() {
        LibraryProblem.entries.forEach { assertTrue(LensesMessageText.problem(it).endsWith(".")) }
        assertTrue(LensesMessageText.problem(LibraryProblem.NAME_TOO_LONG).contains("40"))
        assertTrue(LensesMessageText.problem(LibraryProblem.LIBRARY_FULL).contains("100"))
        assertEquals("Choose at least one source.", LensesMessageText.problem(LensProblem.NoSource))
        assertEquals(
            "Another saved lens already has that name.",
            LensesMessageText.problem(LensProblem.Library(LibraryProblem.NAME_TAKEN)),
        )
        assertEquals("1 of 40", LensesMessageText.nameCounter(1))
    }

    @Test
    fun announcementsSayWhatChangedAndWhatHappenedToTheContainers() {
        val phone = HomeLayoutDeviceClass.PHONE
        assertEquals(
            "Deleted \"X\". Containers that used it keep their lens, no longer shared.",
            LensesMessageText.message(LensesSettingsMessage.Deleted("X", 2, 2, null)),
        )
        assertEquals(
            "Deleted \"X\". 3 containers switched to \"Y\".",
            LensesMessageText.message(LensesSettingsMessage.Deleted("X", 3, 0, "Y")),
        )
        assertEquals(
            "Deleted \"X\". 1 container switched to \"Y\"; 1 container kept the current lens.",
            LensesMessageText.message(LensesSettingsMessage.Deleted("X", 2, 1, "Y")),
        )
        assertEquals("Deleted \"X\"", LensesMessageText.message(LensesSettingsMessage.Deleted("X", 0, 0, null)))
        assertEquals(
            "Copied \"X\" to Phone (folded) as \"X 2\"",
            LensesMessageText.message(LensesSettingsMessage.CopiedToLayout("X", phone, "X 2")),
        )
        assertEquals(
            "Not saved: it would stop 2 containers from working.",
            LensesMessageText.message(LensesSettingsMessage.BreaksContainers(2)),
        )
        assertEquals(
            "Saved \"X\". 1 container kept the old lens.",
            LensesMessageText.message(LensesSettingsMessage.Saved("X", 1)),
        )
    }

    @Test
    fun usedByRowsReadWorkspaceThenPlaceThenLook() {
        val page =
            UsedByRow(
                WorkspaceId("w"),
                "Standard",
                ContainerId("p"),
                UsePlace.Page(2, PageKind.PAGE_SET),
                ExpressionKind.CARD_STACK,
            )

        assertEquals("Standard > Page 2 (a page per group), Card stack", LensesDetailText.usedBy(page))
        assertEquals(
            "Work > Dock section, Icon row",
            LensesDetailText.usedBy(
                page.copy(workspaceName = "Work", place = UsePlace.Dock, expression = ExpressionKind.ICON_ROW),
            ),
        )
        assertEquals("Widget on page 3", LensesDetailText.place(UsePlace.Widget(3)))
        assertEquals("Finder page 1", LensesDetailText.place(UsePlace.Page(1, PageKind.FINDER)))
        assertTrue(LensesDetailText.usedBySpoken(page, canEdit = true).endsWith("Double tap to edit this workspace"))
        assertFalse(LensesDetailText.usedBySpoken(page, canEdit = false).contains("Double tap"))
    }

    @Test
    fun aBrokenContainerIsListedWithTheDomainsReason() {
        val use =
            UsedByRow(
                WorkspaceId("w"),
                "Standard",
                ContainerId("p"),
                UsePlace.Page(1, PageKind.PAGE_SET),
                ExpressionKind.CARD_STACK,
            )
        val broken = BrokenUse(use, listOf(WorkspaceIssue.NoPages))

        assertEquals(
            "Standard > Page 1 (a page per group), Card stack: A workspace needs at least one page",
            LensesDetailText.broken(broken),
        )
    }

    @Test
    fun sourceStatusIsAlwaysWordsAndOffExplainsItself() {
        assertNull(EditorReasonText.sourceStatusLabel(SourceStatus.READY))
        assertNull(EditorReasonText.sourceStatusLabel(null))
        assertEquals("Needs access", EditorReasonText.sourceStatusLabel(SourceStatus.NEEDS_PERMISSION))
        assertEquals("Off", EditorReasonText.sourceStatusLabel(SourceStatus.OFF))
        assertEquals("Unavailable", EditorReasonText.sourceStatusLabel(SourceStatus.UNAVAILABLE))
        assertEquals("Checking", EditorReasonText.sourceStatusLabel(SourceStatus.LOADING))
        assertTrue(EditorReasonText.SOURCE_OFF_NOTE.contains("Settings > Sources"))
    }

    @Test
    fun theCopyDialogExplainsAOneTimeCopyAndTheCounts() {
        val tablet = HomeLayoutDeviceClass.TABLET
        val body = LensesDialogText.copyBody("Notes", LensCopyTarget(tablet, 4, false, "Notes 2"))

        assertTrue(body.contains("one-time copy, not a link"))
        assertTrue(body.contains("\"Notes 2\""))
        assertTrue(body.contains("Tablet has 4 lenses"))
        assertTrue(
            LensesDialogText.copyBody("Notes", LensCopyTarget(tablet, 100, true, "Notes")).contains("Delete one"),
        )
        assertEquals("Tablet (4 lenses)", LensesDialogText.copyTargetLabel(LensCopyTarget(tablet, 4, false, "x")))
        assertEquals(
            "Tablet (100 lenses, full)",
            LensesDialogText.copyTargetLabel(LensCopyTarget(tablet, 100, true, "x")),
        )
    }

    @Test
    fun theDeleteDialogSaysHowManyContainersUseTheLens() {
        assertTrue(LensesDialogText.deleteBody("X", 0).contains("No container uses it"))
        assertTrue(LensesDialogText.deleteBody("X", 1).contains("1 container"))
        assertTrue(LensesDialogText.deleteBody("X", 4).contains("4 containers"))
        assertEquals("Every container can use it.", LensesDialogText.replacementImpact(0))
        assertTrue(LensesDialogText.replacementImpact(2).contains("2 containers cannot draw it"))
    }
}
