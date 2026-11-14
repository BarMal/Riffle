package com.riffle.core.domain.launcher.library

/**
 * The paged Library View: every app laid out in the cells of Home's grid, one grid per page, in order. Pure; the page
 * is whatever [columns] by [rows] holds, so the pages read as Home's siblings.
 */
object LibraryPaging {
    /** How many apps one page holds; a grid is never smaller than one cell. */
    fun pageSize(
        columns: Int,
        rows: Int,
    ): Int = columns.coerceAtLeast(1) * rows.coerceAtLeast(1)

    /** The apps of each page, in order. No apps is no pages (see [pageCount] for the pager's count). */
    fun <T> pages(
        items: List<T>,
        columns: Int,
        rows: Int,
    ): List<List<T>> = items.chunked(pageSize(columns, rows))

    /** How many pages the pager has: never fewer than one, so an empty Library still has a page to show. */
    fun pageCount(
        itemCount: Int,
        columns: Int,
        rows: Int,
    ): Int {
        val size = pageSize(columns, rows)
        return ((itemCount.coerceAtLeast(0) + size - 1) / size).coerceAtLeast(1)
    }

    /** The page holding the app at [index] (0 for anything out of range). */
    fun pageOf(
        index: Int,
        columns: Int,
        rows: Int,
    ): Int = if (index < 0) 0 else index / pageSize(columns, rows)
}
