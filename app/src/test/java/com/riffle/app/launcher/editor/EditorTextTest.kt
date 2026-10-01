package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ContainerIssue
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensIssue
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.ResultShape
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditRejection
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.ExpressionChoice
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextTest {
    private fun assertAllNonBlank(values: List<String>) = values.forEach { assertTrue("blank label", it.isNotBlank()) }

    @Test
    fun everyDomainValueHasWording() {
        assertAllNonBlank(ExpressionKind.entries.map { EditorText.expressionLabel(it) })
        assertAllNonBlank(ExpressionKind.entries.map { EditorText.expressionHint(it) })
        assertAllNonBlank(ContainerKind.entries.map { EditorText.containerLabel(it) })
        assertAllNonBlank(ContainerKind.entries.map { EditorText.containerHint(it) })
        assertAllNonBlank(LensPreset.entries.map { EditorText.presetLabel(it) })
        assertAllNonBlank(SourceCapability.entries.map { EditorText.badgeLabel(it) })
        assertAllNonBlank(EditorStep.entries.map { EditorText.stepTitle(it) })
        assertAllNonBlank(EditorStep.entries.map { EditorText.stepHeading(it) })
    }

    @Test
    fun builtInSourcesReadAsWordsAndUnknownOnesFallBackToTheirId() {
        assertEquals("Notifications", EditorText.sourceLabel(SourceIds.NOTIFICATIONS))
        assertEquals("All apps", EditorText.sourceLabel(SourceIds.ALL_APPS))
        assertEquals("custom.source", EditorText.sourceLabel(SourceId("custom.source")))
        assertAllNonBlank(SourceIds.BUILT_IN.map { EditorText.sourceLabel(it) })
    }

    @Test
    fun everyReasonIsReadable() {
        val apps = SourceId("apps")
        val lensIssues =
            listOf(
                LensIssue.MissingRequiredField(ItemField.ICON),
                LensIssue.ShapeNotAccepted(setOf(ResultShape.GROUPED), setOf(ResultShape.FLAT, ResultShape.SINGLE)),
                LensIssue.SourceNotGroupable(apps),
                LensIssue.UnknownSource(apps),
            )
        val id = ContainerId("c")
        val containerIssues =
            listOf(
                ContainerIssue.InvalidPairing(id, lensIssues),
                ContainerIssue.PageSetNeedsGroupedLens(id),
                ContainerIssue.AxisConflict(id, GestureAxis.HORIZONTAL_SCROLL),
                ContainerIssue.WidgetOutOfBounds(id),
                ContainerIssue.WidgetOverlap(id, ContainerId("d")),
                ContainerIssue.DuplicateContainerId(id),
                ContainerIssue.InvalidFinder(id),
            )
        val workspaceIssues =
            containerIssues.map { WorkspaceIssue.Container(it) } +
                listOf(
                    WorkspaceIssue.DockPairing(lensIssues),
                    WorkspaceIssue.NoPages,
                    WorkspaceIssue.MultipleFinderPages,
                    WorkspaceIssue.UnsupportedExpression(ExpressionKind.CARD),
                )
        assertAllNonBlank(lensIssues.map { EditorReasonText.lens(it) })
        assertAllNonBlank(workspaceIssues.map { EditorReasonText.workspace(it) })
        val rejections =
            listOf(
                EditRejection.Invalid(workspaceIssues),
                EditRejection.UnknownPage(id),
                EditRejection.UnknownWidget(id),
                EditRejection.NotAGridPage(id),
                EditRejection.NotABoundPage(id),
                EditRejection.BlankName,
                EditRejection.BlankSkinId,
            )
        assertAllNonBlank(rejections.map { EditorReasonText.rejection(it) })
    }

    @Test
    fun anOfferedExpressionHasNoReasonAndADisabledOneExplainsWhy() {
        assertEquals("", EditorReasonText.expression(ExpressionChoice(ExpressionKind.LIST, enabled = true)))
        val blocked =
            ExpressionChoice(
                ExpressionKind.CARD,
                enabled = false,
                lensIssues = listOf(LensIssue.ShapeNotAccepted(setOf(ResultShape.FLAT), setOf(ResultShape.SINGLE))),
            )
        assertTrue(EditorReasonText.expression(blocked).contains("single item"))
        val layout = ExpressionChoice(ExpressionKind.CARD, enabled = false, unsupportedByLayout = true)
        assertEquals("Not available on this screen", EditorReasonText.expression(layout))
    }

    @Test
    fun descriptionsNameWhatIsThere() {
        val binding = LensBinding(Lens(listOf(SourceIds.NOTIFICATIONS)), ExpressionKind.LIST)
        assertEquals("List of Notifications", EditorDescribe.bindingText(binding))
        assertEquals("Page 3", EditorDescribe.pageName(2))
        val finder = PageContainer(ContainerId("f"), PageContent.Bound(binding), PageRole.FINDER)
        assertEquals("Finder: List of Notifications", EditorDescribe.page(finder))
        val grid = PageContainer(ContainerId("g"), PageContent.WidgetGrid(4, 6, emptyList()))
        assertEquals("Widgets: 0 in a 4 by 6 grid", EditorDescribe.page(grid))
        assertEquals(EditorText.DOCK_SECTION_EMPTY, EditorDescribe.dock(null))
    }

    @Test
    fun stepProgressIsPlain() {
        assertEquals("Step 2 of 4", EditorText.stepProgress(2, 4))
    }
}
