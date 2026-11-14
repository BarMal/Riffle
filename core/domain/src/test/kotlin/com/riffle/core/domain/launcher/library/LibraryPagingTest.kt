package com.riffle.core.domain.launcher.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryPagingTest {
    private val apps = (1..26).map { "app$it" }

    @Test
    fun aPageHoldsAsManyAppsAsTheGridHasCells() {
        assertEquals(20, LibraryPaging.pageSize(columns = 4, rows = 5))
        val pages = LibraryPaging.pages(apps, columns = 4, rows = 5)
        assertEquals(listOf(20, 6), pages.map { it.size })
        assertEquals(apps, pages.flatten())
    }

    @Test
    fun appsKeepTheirOrderAcrossPages() {
        val pages = LibraryPaging.pages(apps, columns = 2, rows = 3)
        assertEquals(5, pages.size)
        assertEquals(listOf("app1", "app2", "app3", "app4", "app5", "app6"), pages.first())
        assertEquals(listOf("app25", "app26"), pages.last())
    }

    @Test
    fun exactlyFullPagesLeaveNoEmptyOneBehind() {
        val pages = LibraryPaging.pages((1..40).toList(), columns = 4, rows = 5)
        assertEquals(listOf(20, 20), pages.map { it.size })
        assertEquals(2, LibraryPaging.pageCount(40, 4, 5))
        assertEquals(3, LibraryPaging.pageCount(41, 4, 5))
    }

    @Test
    fun noAppsIsNoPagesButThePagerStillHasOnePage() {
        assertTrue(LibraryPaging.pages(emptyList<String>(), 4, 5).isEmpty())
        assertEquals(1, LibraryPaging.pageCount(0, 4, 5))
        assertEquals(1, LibraryPaging.pageCount(-3, 4, 5))
    }

    @Test
    fun aDegenerateGridStillHoldsOneAppPerPage() {
        assertEquals(1, LibraryPaging.pageSize(0, 0))
        assertEquals(3, LibraryPaging.pages(listOf(1, 2, 3), columns = 0, rows = -2).size)
    }

    @Test
    fun thePageOfAnAppFollowsItsPosition() {
        assertEquals(0, LibraryPaging.pageOf(0, 4, 5))
        assertEquals(0, LibraryPaging.pageOf(19, 4, 5))
        assertEquals(1, LibraryPaging.pageOf(20, 4, 5))
        assertEquals(0, LibraryPaging.pageOf(-1, 4, 5))
    }
}
