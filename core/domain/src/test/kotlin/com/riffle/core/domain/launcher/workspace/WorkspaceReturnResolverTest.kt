package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceReturnResolverTest {
    private val flat = Lens(sources = listOf(SourceId("apps")))

    private fun page(id: String) =
        PageContainer(ContainerId(id), PageContent.Bound(LensBinding(flat, ExpressionKind.LIST)))

    private fun finder(id: String) =
        PageContainer(
            ContainerId(id),
            PageContent.Bound(LensBinding(flat, ExpressionKind.ALPHA_LIST)),
            PageRole.FINDER,
        )

    private fun workspace(
        vararg pages: PageHost,
        start: String? = null,
    ) = Workspace(WorkspaceId("w"), "W", pages.toList(), startPageId = start?.let(::ContainerId))

    private fun id(value: String?) = value?.let(::ContainerId)

    private fun resolve(
        ws: Workspace,
        behavior: ReturnBehavior,
        last: String?,
        event: ReturnEvent = ReturnEvent.RETURN,
    ) = WorkspaceReturnResolver.resolve(ws, behavior, id(last), event)

    // Pager: a, b, c. Start: b. Finder: f (listed first, so "first page" must skip it).
    private val ws = workspace(finder("f"), page("a"), page("b"), page("c"), start = "b")

    @Test
    fun defaultIsRestore() {
        assertEquals(ReturnBehavior.RESTORE, HomeBehaviourSettings().returnBehavior)
    }

    @Test
    fun restoreReturnsToTheLastVisitedPage() {
        assertEquals(id("c"), resolve(ws, ReturnBehavior.RESTORE, "c"))
    }

    @Test
    fun restoreWithNothingVisitedFallsToTheStartPage() {
        assertEquals(id("b"), resolve(ws, ReturnBehavior.RESTORE, null))
    }

    @Test
    fun restoreIgnoresAVanishedPage() {
        assertEquals(id("b"), resolve(ws, ReturnBehavior.RESTORE, "gone"))
    }

    @Test
    fun restoreNeverLandsOnTheFinderAsAPlaceToComeBackTo() {
        assertEquals(id("b"), resolve(ws, ReturnBehavior.RESTORE, "f"))
    }

    @Test
    fun firstPageIsTheFirstNonFinderPage() {
        assertEquals(id("a"), resolve(ws, ReturnBehavior.FIRST_PAGE, "c"))
        assertEquals(id("a"), resolve(ws, ReturnBehavior.FIRST_PAGE, null))
    }

    @Test
    fun startPageIsTheStartPageWhateverWasVisited() {
        assertEquals(id("b"), resolve(ws, ReturnBehavior.START_PAGE, "c"))
    }

    @Test
    fun startPageWithoutAnExplicitOneIsTheFirstNonFinderPage() {
        val plain = workspace(finder("f"), page("a"), page("b"))
        assertEquals(id("a"), resolve(plain, ReturnBehavior.START_PAGE, "b"))
    }

    @Test
    fun aStaleStartPageFallsToTheFirstNonFinderPage() {
        val stale = workspace(finder("f"), page("a"), page("b"), start = "gone")
        assertEquals(id("a"), resolve(stale, ReturnBehavior.START_PAGE, null))
    }

    @Test
    fun theFinderMayBeTheStartPage() {
        val onSearch = workspace(page("a"), finder("f"), start = "f")
        assertEquals(id("f"), resolve(onSearch, ReturnBehavior.START_PAGE, "a"))
        assertEquals(id("a"), resolve(onSearch, ReturnBehavior.FIRST_PAGE, "a"))
    }

    @Test
    fun homePressAtHomeGoesToTheStartPageWhateverTheSetting() {
        ReturnBehavior.entries.forEach { behavior ->
            assertEquals(id("b"), resolve(ws, behavior, "c", ReturnEvent.HOME_PRESS), "behavior: $behavior")
        }
    }

    @Test
    fun homePressWithTheFinderOpenGoesBackToWhereItWasOpenedFrom() {
        ReturnBehavior.entries.forEach { behavior ->
            assertEquals(id("c"), resolve(ws, behavior, "c", ReturnEvent.HOME_PRESS_FINDER_OPEN), "behavior: $behavior")
        }
    }

    @Test
    fun homePressWithTheFinderOpenAsStartPageGoesToTheStartPage() {
        val onSearch = workspace(page("a"), finder("f"), start = "f")
        assertEquals(id("f"), resolve(onSearch, ReturnBehavior.RESTORE, "f", ReturnEvent.HOME_PRESS_FINDER_OPEN))
    }

    @Test
    fun aFinderOnlyWorkspaceResolvesToItsOnlyPage() {
        val only = workspace(finder("f"))
        ReturnBehavior.entries.forEach { assertEquals(id("f"), resolve(only, it, null)) }
    }

    @Test
    fun aWorkspaceWithoutPagesResolvesToNull() {
        ReturnBehavior.entries.forEach { assertNull(resolve(workspace(), it, "a")) }
    }

    @Test
    fun theResultIsAlwaysAPageOfTheSameWorkspaceOrNull() {
        val shapes =
            listOf(
                ws,
                workspace(),
                workspace(finder("f")),
                workspace(page("a"), start = "zzz"),
                workspace(page("a"), finder("f"), start = "f"),
            )
        val lasts = listOf(null, "a", "b", "f", "nope")
        val cases =
            shapes.flatMap { shape ->
                ReturnBehavior.entries.flatMap { behavior ->
                    ReturnEvent.entries.flatMap { event -> lasts.map { last -> Case(shape, behavior, event, last) } }
                }
            }
        cases.forEach { case ->
            val result = resolve(case.workspace, case.behavior, case.last, case.event)
            val inWorkspace = result == null || case.workspace.pages.any { it.id == result }
            assertTrue(inWorkspace, "$case -> $result")
        }
    }

    private data class Case(
        val workspace: Workspace,
        val behavior: ReturnBehavior,
        val event: ReturnEvent,
        val last: String?,
    )
}
