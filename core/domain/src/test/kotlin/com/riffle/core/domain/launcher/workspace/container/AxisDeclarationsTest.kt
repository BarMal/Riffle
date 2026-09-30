package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ContainerIssue
import com.riffle.core.domain.launcher.workspace.ContainerValidation
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AxisDeclarationsTest {
    private val flat = Lens(sources = listOf(SourceId("apps")))
    private val grouped = flat.copy(group = LensGroup.ByGroupKey)

    private fun widget(
        id: String,
        expression: ExpressionKind,
    ) = WidgetContainer(ContainerId(id), WidgetSpan(), LensBinding(flat, expression))

    @Test
    fun widgetDeclaresItsExpressionAxes() {
        val declaration = AxisDeclarations.resolve(widget("w", ExpressionKind.ICON_ROW))

        assertEquals(setOf(GestureAxis.HORIZONTAL_SCROLL), declaration.effective)
        assertFalse(declaration.hasConflict)
    }

    @Test
    fun boundPageDeclaresItsExpressionAxes() {
        val page = PageContainer(ContainerId("p"), PageContent.Bound(LensBinding(flat, ExpressionKind.LIST)))

        assertEquals(setOf(GestureAxis.VERTICAL_SCROLL), AxisDeclarations.resolve(page).owned)
    }

    @Test
    fun gridPageClaimsNothingItselfAndCombinesChildren() {
        val page =
            PageContainer(
                ContainerId("p"),
                PageContent.WidgetGrid(
                    4,
                    4,
                    listOf(
                        WidgetPlacement(widget("row", ExpressionKind.ICON_ROW), 0, 0),
                        WidgetPlacement(widget("card", ExpressionKind.CARD), 1, 1),
                        WidgetPlacement(widget("list", ExpressionKind.LIST), 2, 2),
                    ),
                ),
            )

        val declaration = AxisDeclarations.resolve(page)

        assertTrue(declaration.owned.isEmpty())
        // The card declares no axes, so it is not listed at all.
        assertEquals(setOf(ContainerId("row"), ContainerId("list")), declaration.childAxes.keys)
        assertEquals(setOf(GestureAxis.HORIZONTAL_SCROLL, GestureAxis.VERTICAL_SCROLL), declaration.effective)
        assertFalse(declaration.hasConflict)
        assertTrue(GestureAxis.HORIZONTAL_SCROLL in declaration)
    }

    @Test
    fun pageSetOwnsThePagerAndItsVerticalExpression() {
        val set = PageSetContainer(ContainerId("s"), LensBinding(grouped, ExpressionKind.CARD_STACK))

        val declaration = AxisDeclarations.resolve(set)

        assertEquals(setOf(GestureAxis.HORIZONTAL_PAGER, GestureAxis.VERTICAL_SCROLL), declaration.effective)
        assertFalse(declaration.hasConflict)
    }

    @Test
    fun conflictsMatchContainerValidationForEveryExpression() {
        ExpressionKind.entries.forEach { expression ->
            val set = PageSetContainer(ContainerId("s"), LensBinding(grouped, expression))

            val fromDeclaration = AxisDeclarations.resolve(set).conflicts.map { it.axis }.toSet()
            val fromValidation =
                ContainerValidation.validate(set)
                    .filterIsInstance<ContainerIssue.AxisConflict>()
                    .map { it.axis }
                    .toSet()

            assertEquals(fromValidation, fromDeclaration, "$expression")
        }
    }

    @Test
    fun horizontalExpressionInAPageSetConflicts() {
        val set = PageSetContainer(ContainerId("s"), LensBinding(grouped, ExpressionKind.ICON_ROW))

        val declaration = AxisDeclarations.resolve(set)

        assertEquals(listOf(AxisClaimConflict(ContainerId("s"), GestureAxis.HORIZONTAL_SCROLL)), declaration.conflicts)
    }
}
