package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspaceSetTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE
    private val apps = Lens(sources = listOf(SourceId("apps")))

    private val iconRow = LensBinding(apps, ExpressionKind.ICON_ROW)

    private fun widgetPage(id: String) =
        PageContainer(
            ContainerId(id),
            PageContent.WidgetGrid(
                columns = 4,
                rows = 4,
                placements =
                    listOf(
                        WidgetPlacement(
                            WidgetContainer(ContainerId("w-$id"), WidgetSpan(2, 1), iconRow),
                            column = 0,
                            row = 0,
                        ),
                    ),
            ),
        )

    private fun ws(
        id: String,
        expression: ExpressionKind = ExpressionKind.LIST,
    ) = Workspace(
        WorkspaceId(id),
        id,
        listOf(
            widgetPage("pg-$id"),
            PageContainer(ContainerId("l-$id"), PageContent.Bound(LensBinding(apps, expression))),
        ),
        gestureBindings = mapOf("swipe_up" to "open_finder"),
    )

    @Test
    fun unstoredLayoutReadsAsBuiltInDefault() {
        val layout = WorkspaceSet().workspacesFor(phone)
        assertEquals(1, layout.workspaces.size)
        assertEquals(layout.activeId, layout.defaultId)
        assertTrue(WorkspaceValidation.validate(layout.active).isEmpty())
    }

    @Test
    fun layoutsAreIndependent() {
        val set = WorkspaceSet().update(phone) { it.add(ws("x"), activate = true) }
        assertEquals(WorkspaceId("x"), set.workspacesFor(phone).activeId)
        assertNotEquals(WorkspaceId("x"), set.workspacesFor(foldable).activeId)
    }
}
