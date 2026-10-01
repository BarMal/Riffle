package com.riffle.core.domain.launcher.workspace.menu

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceMenuPlannerTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val flat = Lens(sources = listOf(SourceId("apps")))
    private val grouped = Lens(sources = listOf(SourceId("apps")), group = LensGroup.ByGroupKey)

    private fun page(
        id: String,
        expression: ExpressionKind = ExpressionKind.LIST,
        role: PageRole = PageRole.STANDARD,
    ) = PageContainer(ContainerId(id), PageContent.Bound(LensBinding(flat, expression)), role)

    private fun finder(id: String = "finder") = page(id, ExpressionKind.ALPHA_LIST, PageRole.FINDER)

    private fun pageSet(id: String) = PageSetContainer(ContainerId(id), LensBinding(grouped, ExpressionKind.CARD_STACK))

    private fun workspace(
        id: String,
        vararg pages: PageHost,
    ) = Workspace(WorkspaceId(id), "Name $id", pages.toList())

    private fun layoutSet(
        active: String,
        vararg workspaces: Workspace,
    ) = WorkspaceSet(
        mapOf(phone to LayoutWorkspaces(workspaces.toList(), WorkspaceId(active), workspaces.first().id)),
    )

    @Test
    fun workspaceSystemOffYieldsNoMenu() {
        assertNull(WorkspaceMenuPlanner.plan(null, phone))
        assertNull(WorkspaceMenuPlanner.plan(WorkspaceSet(), phone))
    }

    @Test
    fun switchEntriesFollowLayoutOrderAndMarkTheActiveOne() {
        val set = layoutSet("b", workspace("a", page("p")), workspace("b", page("p")), workspace("c", page("p")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone))
        assertEquals(listOf("a", "b", "c"), model.switchEntries.map { it.id.value })
        assertEquals(listOf(false, true, false), model.switchEntries.map { it.isActive })
        assertEquals(listOf(false, true, false), model.switchEntries.map { it.isDisplayed })
        assertTrue(model.hasSwitchChoice)
        assertNull(model.fallback)
        assertEquals(WorkspaceId("b"), model.editTarget)
    }

    @Test
    fun singleWorkspaceHasNoSwitchChoiceButStillEditsAndJumps() {
        val set = layoutSet("a", workspace("a", page("p1"), page("p2")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone))
        assertFalse(model.hasSwitchChoice)
        assertEquals(1, model.switchEntries.size)
        assertEquals(listOf(1, 2), model.jumpEntries.map { it.pageNumber })
    }

    @Test
    fun finderEntryPresentOnlyWhenTheWorkspaceHasAFinderPage() {
        val with = layoutSet("a", workspace("a", page("p"), finder("f")))
        assertEquals(ContainerId("f"), WorkspaceMenuPlanner.plan(with, phone)?.finder?.pageId)
        val without = layoutSet("a", workspace("a", page("p")))
        assertNull(WorkspaceMenuPlanner.plan(without, phone)?.finder)
    }

    @Test
    fun finderPageIsNotRepeatedAsAJumpEntryAndPageNumbersCountIt() {
        val model =
            assertNotNull(WorkspaceMenuPlanner.plan(layoutSet("a", workspace("a", finder("f"), page("p"))), phone))
        assertEquals(listOf(WorkspacePageKey.Page(ContainerId("p"))), model.jumpEntries.map { it.key })
        assertEquals(2, model.jumpEntries.single().pageNumber)
    }

    @Test
    fun pageSetExpandsToItsGroupsInOrder() {
        val groups = mapOf(ContainerId("inbox") to listOf(PageSetGroupRef("a", "Mail"), PageSetGroupRef("b", null)))
        val set = layoutSet("a", workspace("a", page("now"), pageSet("inbox")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone, groups = groups))
        assertEquals(
            listOf(
                WorkspacePageKey.Page(ContainerId("now")),
                WorkspacePageKey.Group(ContainerId("inbox"), "a"),
                WorkspacePageKey.Group(ContainerId("inbox"), "b"),
            ),
            model.jumpEntries.map { it.key },
        )
        assertEquals(listOf(null, "Mail", null), model.jumpEntries.map { it.groupLabel })
        assertEquals(listOf(1, 2, 2), model.jumpEntries.map { it.pageNumber })
        assertEquals(0, model.omittedGroupCount)
    }

    @Test
    fun pageSetGroupsAreBoundedAndDeduplicatedAndTheOverflowIsCounted() {
        val groups = mapOf(ContainerId("inbox") to (1..12).map { PageSetGroupRef("g${it % 11}", "G$it") })
        val set = layoutSet("a", workspace("a", pageSet("inbox")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone, groups = groups, maxGroupsPerPageSet = 5))
        assertEquals(5, model.jumpEntries.size)
        // 12 refs with one repeated key: 11 distinct, 5 shown.
        assertEquals(6, model.omittedGroupCount)
    }

    @Test
    fun pageSetWithNoEvaluatedGroupsContributesNothing() {
        val model = assertNotNull(WorkspaceMenuPlanner.plan(layoutSet("a", workspace("a", pageSet("inbox"))), phone))
        assertTrue(model.jumpEntries.isEmpty())
    }

    @Test
    fun negativeGroupBoundIsTreatedAsZero() {
        val groups = mapOf(ContainerId("inbox") to listOf(PageSetGroupRef("a", null)))
        val set = layoutSet("a", workspace("a", pageSet("inbox")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone, groups = groups, maxGroupsPerPageSet = -3))
        assertTrue(model.jumpEntries.isEmpty())
        assertEquals(1, model.omittedGroupCount)
    }

    @Test
    fun emptyWorkspaceStillOffersSwitchAndEdit() {
        val model = assertNotNull(WorkspaceMenuPlanner.plan(layoutSet("a", workspace("a")), phone))
        assertTrue(model.jumpEntries.isEmpty())
        assertNull(model.finder)
        assertEquals(WorkspaceId("a"), model.editTarget)
    }

    @Test
    fun fallbackResolvedWorkspaceShowsTheDefaultAndTheReason() {
        val default = workspace("default", page("d"))
        val fancy = workspace("fancy", page("p", ExpressionKind.CARD_STACK), finder("f"))
        val caps = LayoutCapabilities(setOf(ExpressionKind.LIST, ExpressionKind.ALPHA_LIST))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(layoutSet("fancy", default, fancy), phone, caps))
        val fallback = assertNotNull(model.fallback)
        assertEquals(WorkspaceId("fancy"), fallback.requested)
        assertEquals(WorkspaceId("default"), fallback.displayed)
        assertTrue(WorkspaceIssue.UnsupportedExpression(ExpressionKind.CARD_STACK) in fallback.reasons)
        // The stored active stays marked; the displayed one is the default and drives jump/finder/edit.
        assertEquals(listOf(false, true), model.switchEntries.map { it.isActive })
        assertEquals(listOf(true, false), model.switchEntries.map { it.isDisplayed })
        assertEquals(listOf(WorkspacePageKey.Page(ContainerId("d"))), model.jumpEntries.map { it.key })
        assertNull(model.finder)
        assertEquals(WorkspaceId("default"), model.editTarget)
    }

    @Test
    fun aValidNonDefaultActiveWorkspaceWithAFinderIsDisplayedWithoutFallback() {
        val set = layoutSet("b", workspace("a", page("p")), workspace("b", page("p"), finder("f")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, phone))
        assertNull(model.fallback)
        assertEquals(WorkspaceId("b"), model.editTarget)
        assertEquals(ContainerId("f"), model.finder?.pageId)
    }

    @Test
    fun deviceClassWithoutAStoredLayoutReadsTheBuiltInDefault() {
        val set = layoutSet("a", workspace("a", page("p")))
        val model = assertNotNull(WorkspaceMenuPlanner.plan(set, HomeLayoutDeviceClass.TABLET))
        assertFalse(model.switchEntries.map { it.id.value }.contains("a"))
        assertEquals(1, model.switchEntries.size)
    }
}
