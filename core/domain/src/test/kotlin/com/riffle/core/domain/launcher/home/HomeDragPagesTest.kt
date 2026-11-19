package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeDragPagesTest {
    private val grid = GridDimensions(4, 2)
    private val item =
        AppShortcutItem(
            id = LauncherItemId("a"),
            appIdentity = AppIdentity(AppPackageName("pkg.a"), AppActivityName("Main")),
            label = "a",
            placement = GridPlacement(GridCell(0, 0)),
        )
    private val full = LauncherPage(LauncherPageId("full"), grid = grid, items = listOf(item))
    private val empty = LauncherPage(LauncherPageId("empty"), grid = grid, items = emptyList())

    @Test
    fun holdingOnTheLastPageOfItemsOffersANewOne() {
        assertTrue(HomeDragPages.offersNewPage(listOf(full), 0))
        assertTrue(HomeDragPages.offersNewPage(listOf(empty, full), 1))
    }

    @Test
    fun anEmptyLastPageIsAlreadyAPlaceToDrop() {
        assertFalse(HomeDragPages.offersNewPage(listOf(full, empty), 1))
    }

    @Test
    fun onlyOverTheLastPage() {
        assertFalse(HomeDragPages.offersNewPage(listOf(full, full.copy(id = LauncherPageId("f2"))), 0))
        assertFalse(HomeDragPages.offersNewPage(emptyList(), 0))
    }

    @Test
    fun theCandidateIsKeptOnlyWhenTheDragEndsOnIt() {
        val id = LauncherPageId("c")
        assertTrue(HomeDragPages.keepsCandidate(id, id))
        assertEquals(false, HomeDragPages.keepsCandidate(id, LauncherPageId("other")))
        assertEquals(false, HomeDragPages.keepsCandidate(id, null))
    }
}
