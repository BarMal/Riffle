package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.llBinding
import com.riffle.core.domain.launcher.workspace.llGrid
import com.riffle.core.domain.launcher.workspace.llLens
import com.riffle.core.domain.launcher.workspace.llPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceUsageTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET

    private fun workspace(
        id: String,
        name: String,
        pages: List<PageHost>,
        dock: LensBinding? = null,
    ) = Workspace(WorkspaceId(id), name, pages, WorkspaceDock(dock))

    private fun layout(
        vararg workspaces: Workspace,
        library: LensLibrary = LensLibrary(),
    ) = LayoutWorkspaces(workspaces.toList(), workspaces.first().id, workspaces.first().id, library)

    @Test
    fun `no workspaces read nothing`() {
        val usage = SourceUsagePlanner.plan(emptyMap())

        assertEquals(0, usage.countOf(SourceIds.ALL_APPS))
        assertEquals(emptyList(), usage.placesOf(SourceIds.RSS))
    }

    @Test
    fun `pages widgets page sets and the dock each count as a place`() {
        val notes = llBinding(lens = llLens(SourceIds.NOTIFICATIONS))
        val ws =
            workspace(
                "w1",
                "Nova",
                listOf(
                    llPage("p1", llBinding(lens = llLens(SourceIds.ALL_APPS))),
                    llGrid("p2", "wg", llBinding(ExpressionKind.ICON_ROW, llLens(SourceIds.ALL_APPS, limit = 3))),
                    PageSetContainer(ContainerId("ps"), notes),
                ),
                dock = llBinding(ExpressionKind.ICON_ROW, llLens(SourceIds.ALL_APPS, limit = 4)),
            )

        val usage = SourceUsagePlanner.plan(mapOf(phone to layout(ws)))

        val apps = usage.placesOf(SourceIds.ALL_APPS)
        assertEquals(3, usage.countOf(SourceIds.ALL_APPS))
        assertEquals(
            listOf(SourcePlaceKind.PAGE, SourcePlaceKind.WIDGET, SourcePlaceKind.DOCK),
            apps.map { it.kind },
        )
        assertEquals(listOf(1, 2, null), apps.map { it.pageNumber })
        assertEquals(listOf(null, 1, null), apps.map { it.widgetNumber })
        assertNull(apps.last().containerId)
        val set = usage.placesOf(SourceIds.NOTIFICATIONS).single()
        assertEquals(SourcePlaceKind.PAGE_SET, set.kind)
        assertEquals(3, set.pageNumber)
        assertEquals("Nova", set.workspaceName)
    }

    @Test
    fun `a lens naming a source twice counts once and a multi source lens counts for each source`() {
        val lens = Lens(listOf(SourceIds.ALL_APPS, SourceIds.RECENT_APPS, SourceIds.ALL_APPS))
        val ws = workspace("w", "A", listOf(llPage("p", llBinding(lens = lens))))

        val usage = SourceUsagePlanner.plan(mapOf(phone to layout(ws)))

        assertEquals(1, usage.countOf(SourceIds.ALL_APPS))
        assertEquals(1, usage.countOf(SourceIds.RECENT_APPS))
        assertEquals(0, usage.countOf(SourceIds.CALENDAR))
    }

    @Test
    fun `a binding that references a saved lens reads what the saved lens reads`() {
        val id = LensId("saved-1")
        val saved = SavedLens(id, "Mail", llLens(SourceIds.NOTIFICATIONS))
        // The snapshot is stale on purpose: the library is the truth while the reference resolves.
        val ws = workspace("w", "A", listOf(llPage("p", llBinding(lens = llLens(SourceIds.ALL_APPS), ref = id))))

        val usage = SourceUsagePlanner.plan(mapOf(phone to layout(ws, library = LensLibrary(listOf(saved)))))

        assertEquals(0, usage.countOf(SourceIds.ALL_APPS))
        assertEquals("Mail", usage.placesOf(SourceIds.NOTIFICATIONS).single().savedLensName)
    }

    @Test
    fun `a dangling reference falls back to its own snapshot`() {
        val ws =
            workspace(
                "w",
                "A",
                listOf(llPage("p", llBinding(lens = llLens(SourceIds.RSS), ref = LensId("gone")))),
            )

        val usage = SourceUsagePlanner.plan(mapOf(phone to layout(ws)))

        val place = usage.placesOf(SourceIds.RSS).single()
        assertNull(place.savedLensName)
    }

    @Test
    fun `layouts are listed in device class order whatever the map order`() {
        val ws1 = workspace("w1", "Phone one", listOf(llPage("p", llBinding(lens = llLens(SourceIds.RSS)))))
        val ws2 = workspace("w2", "Tablet one", listOf(llPage("q", llBinding(lens = llLens(SourceIds.RSS)))))

        val usage = SourceUsagePlanner.plan(linkedMapOf(tablet to layout(ws2), phone to layout(ws1)))

        assertEquals(listOf(phone, tablet), usage.layoutsOf(SourceIds.RSS))
        assertEquals(listOf("Phone one", "Tablet one"), usage.placesOf(SourceIds.RSS).map { it.workspaceName })
    }

    @Test
    fun `every workspace of a layout is counted in order`() {
        val a = workspace("a", "A", listOf(llPage("a1", llBinding(lens = llLens(SourceIds.CALENDAR)))))
        val b =
            workspace(
                "b",
                "B",
                listOf(llPage("b1"), llPage("b2", llBinding(lens = llLens(SourceIds.CALENDAR)))),
            )

        val usage = SourceUsagePlanner.plan(mapOf(phone to layout(a, b)))

        val places = usage.placesOf(SourceIds.CALENDAR)
        assertEquals(listOf("A", "B"), places.map { it.workspaceName })
        assertEquals(listOf(1, 2), places.map { it.pageNumber })
        assertTrue(places.all { it.layout == phone })
    }
}
