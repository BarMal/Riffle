package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
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
        skinOverrideId = "skin",
        gestureBindings = mapOf("swipe_up" to "open_finder"),
    )

    private class Counter : WorkspaceIdFactory {
        var n = 0

        override fun next() = "n${n++}"
    }

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

    @Test
    fun copyFromOtherLayoutDeepCopiesWithFreshIdsAndMapsActiveAndDefault() {
        val source =
            LayoutWorkspaces.single(ws("a")).add(ws("b")).activate(WorkspaceId("b")).withDefault(WorkspaceId("a"))
        val set = WorkspaceSet(mapOf(foldable to source)).copyFromOtherLayout(foldable, phone, Counter())

        val copied = set.workspacesFor(phone)
        // a -> n0, page n1, widget n2, page n3 ; b -> n4 ...
        assertEquals(listOf("n0", "n4"), copied.workspaces.map { it.id.value })
        assertEquals(WorkspaceId("n4"), copied.activeId)
        assertEquals(WorkspaceId("n0"), copied.defaultId)
        assertEquals(listOf("a", "b"), copied.workspaces.map { it.name })

        val sourceIds = collectIds(source)
        val copiedIds = collectIds(copied)
        assertTrue(sourceIds.intersect(copiedIds).isEmpty())
        assertEquals(sourceIds.size, copiedIds.size)
        // Content other than ids is copied as is, and the source layout is untouched.
        assertEquals("skin", copied.active.skinOverrideId)
        assertEquals(mapOf("swipe_up" to "open_finder"), copied.active.gestureBindings)
        assertSame(source, set.workspacesFor(foldable))
    }

    @Test
    fun copiedLayoutHasNoLiveLinkToItsSource() {
        val set = WorkspaceSet(mapOf(foldable to LayoutWorkspaces.single(ws("a"))))
        val copied = set.copyFromOtherLayout(foldable, phone, Counter())
        val edited = copied.update(foldable) { it.rename(WorkspaceId("a"), "Changed") }
        assertEquals("a", edited.workspacesFor(phone).active.name)
    }

    @Test
    fun copyReplacesTargetAndIgnoresSameLayout() {
        val set =
            WorkspaceSet(
                mapOf(foldable to LayoutWorkspaces.single(ws("a")), phone to LayoutWorkspaces.single(ws("z"))),
            )
        val copied = set.copyFromOtherLayout(foldable, phone, Counter())
        assertEquals(1, copied.workspacesFor(phone).workspaces.size)
        assertEquals("a", copied.workspacesFor(phone).active.name)
        assertSame(set, set.copyFromOtherLayout(phone, phone))
    }

    @Test
    fun resolveActiveReturnsActiveWhenDrawable() {
        val set = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(ws("a")).add(ws("b"), activate = true)))
        val resolution = set.resolveActive(phone)
        assertIs<WorkspaceResolution.Resolved>(resolution)
        assertEquals(WorkspaceId("b"), resolution.workspace.id)
    }

    @Test
    fun resolveActiveFallsBackToDefaultWithReasonWhenLayoutCannotDrawIt() {
        val fancy = ws("b", ExpressionKind.CARD_STACK)
        val set = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(ws("a")).add(fancy, activate = true)))
        val caps = LayoutCapabilities(ExpressionKind.entries.toSet() - ExpressionKind.CARD_STACK)
        val resolution = set.resolveActive(phone, caps)
        assertIs<WorkspaceResolution.FellBack>(resolution)
        assertEquals(WorkspaceId("a"), resolution.workspace.id)
        assertEquals(WorkspaceId("b"), resolution.requested)
        assertEquals(listOf(WorkspaceIssue.UnsupportedExpression(ExpressionKind.CARD_STACK)), resolution.issues)
        // The same set draws it on a layout that can.
        assertIs<WorkspaceResolution.Resolved>(set.resolveActive(phone))
    }

    private fun collectIds(layout: LayoutWorkspaces): Set<String> =
        layout.workspaces.flatMap { w ->
            listOf(w.id.value) +
                w.pages.flatMap { p ->
                    listOf(p.id.value) +
                        ((p as? PageContainer)?.content as? PageContent.WidgetGrid)
                            ?.placements.orEmpty().map { it.widget.id.value }
                }
        }.toSet()
}
