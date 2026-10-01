package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPositionTest {
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

    private fun id(value: String) = ContainerId(value)

    private val request = ReturnRequest(homePress = false, token = 1)
    private val homePress = ReturnRequest(homePress = true, token = 2)

    // Pager a, b, c; Finder f listed first; start page b.
    private val ws = workspace(finder("f"), page("a"), page("b"), page("c"), start = "b")

    @Test
    fun openingLandsOnTheStartPageForRestoreAndStartPageAndOnTheFirstPageForFirstPage() {
        assertEquals(PreviewPosition(id("b")), PreviewPositions.initial(ws, ReturnBehavior.RESTORE))
        assertEquals(PreviewPosition(id("b")), PreviewPositions.initial(ws, ReturnBehavior.START_PAGE))
        assertEquals(PreviewPosition(id("a")), PreviewPositions.initial(ws, ReturnBehavior.FIRST_PAGE))
    }

    @Test
    fun aFinderStartPageOpensTheFinderOverTheFirstPagerPage() {
        val onSearch = workspace(page("a"), page("b"), finder("f"), start = "f")
        assertEquals(
            PreviewPosition(id("a"), finderOpen = true),
            PreviewPositions.initial(onSearch, ReturnBehavior.RESTORE),
        )
        assertEquals(PreviewPosition(id("a")), PreviewPositions.initial(onSearch, ReturnBehavior.FIRST_PAGE))
    }

    @Test
    fun aFinderOnlyWorkspaceShowsItsOnePageWithoutAnOverlay() {
        val only = workspace(finder("f"))
        assertEquals(PreviewPosition(id("f")), PreviewPositions.initial(only, ReturnBehavior.RESTORE))
        assertEquals(PreviewPosition(id("f")), PreviewPositions.navigated(PreviewPosition(id("f")), only, id("f")))
    }

    @Test
    fun theFinderIsNotAPagerPage() {
        assertEquals(-1, ws.pagerIndexOf(id("f")))
        assertEquals(0, ws.pagerIndexOf(id("a")))
        assertEquals(2, ws.pagerIndexOf(id("c")))
        val settled = PreviewPositions.settled(PreviewPosition(id("a")), ws, id("f"))
        assertEquals(id("a"), settled.page)
    }

    @Test
    fun settlingOnAPageRecordsItAsTheLastVisited() {
        assertEquals(id("c"), PreviewPositions.settled(PreviewPosition(id("a")), ws, id("c")).page)
    }

    @Test
    fun navigatingToAPagerPageClosesTheFinderAndMovesThere() {
        val open = PreviewPosition(id("a"), finderOpen = true)
        assertEquals(PreviewPosition(id("c")), PreviewPositions.navigated(open, ws, id("c")))
    }

    @Test
    fun navigatingToTheFinderOpensItWithoutMovingThePager() {
        val result = PreviewPositions.navigated(PreviewPosition(id("c")), ws, id("f"))
        assertEquals(PreviewPosition(id("c"), finderOpen = true), result)
    }

    @Test
    fun navigatingToAnUnknownPageChangesNothing() {
        val here = PreviewPosition(id("c"))
        assertEquals(here, PreviewPositions.navigated(here, ws, id("zzz")))
    }

    @Test
    fun returningRestoresTheLastVisitedPageByDefault() {
        val away = PreviewPosition(id("c"))
        assertEquals(away, PreviewPositions.returned(away, ws, ReturnBehavior.RESTORE, request))
    }

    @Test
    fun returningWithFirstPageGoesToTheFirstNonFinderPage() {
        assertEquals(
            PreviewPosition(id("a")),
            PreviewPositions.returned(PreviewPosition(id("c")), ws, ReturnBehavior.FIRST_PAGE, request),
        )
    }

    @Test
    fun returningWithStartPageGoesToTheStartPage() {
        assertEquals(
            PreviewPosition(id("b")),
            PreviewPositions.returned(PreviewPosition(id("c")), ws, ReturnBehavior.START_PAGE, request),
        )
    }

    @Test
    fun returningClosesAnOpenFinder() {
        val finderOpen = PreviewPosition(id("c"), finderOpen = true)
        assertFalse(PreviewPositions.returned(finderOpen, ws, ReturnBehavior.RESTORE, request).finderOpen)
        assertFalse(PreviewPositions.returned(finderOpen, ws, ReturnBehavior.START_PAGE, request).finderOpen)
    }

    @Test
    fun homePressAtHomeGoesToTheStartPageWhateverTheSetting() {
        ReturnBehavior.entries.forEach { behavior ->
            val result = PreviewPositions.returned(PreviewPosition(id("c")), ws, behavior, homePress)
            assertEquals("behavior: $behavior", PreviewPosition(id("b")), result)
        }
    }

    @Test
    fun homePressWithTheFinderOpenClosesItAndStaysOnThePageItWasOpenedFrom() {
        ReturnBehavior.entries.forEach { behavior ->
            val result =
                PreviewPositions.returned(PreviewPosition(id("c"), finderOpen = true), ws, behavior, homePress)
            assertEquals("behavior: $behavior", PreviewPosition(id("c")), result)
        }
    }

    @Test
    fun homePressWithTheFinderOpenOnAFinderStartPageStillGoesBackToThePageItWasOpenedFrom() {
        val onSearch = workspace(page("a"), finder("f"), start = "f")
        val open = PreviewPosition(id("a"), finderOpen = true)
        val result = PreviewPositions.returned(open, onSearch, ReturnBehavior.RESTORE, homePress)
        assertEquals(PreviewPosition(id("a")), result)
    }

    @Test
    fun anEmptyWorkspaceNeverThrowsAndHasNoPage() {
        val empty = workspace()
        assertNull(PreviewPositions.initial(empty, ReturnBehavior.RESTORE).page)
        assertTrue(!PreviewPositions.returned(PreviewPosition(), empty, ReturnBehavior.START_PAGE, request).finderOpen)
    }
}
