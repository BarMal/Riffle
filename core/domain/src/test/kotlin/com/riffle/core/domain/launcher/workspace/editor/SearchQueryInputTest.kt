package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryInputTest {
    @Test
    fun `typed text is capped at the query limit`() {
        val long = "a".repeat(SearchQueryInput.MAX_LENGTH + 20)

        assertEquals(SearchQueryInput.MAX_LENGTH, SearchQueryInput.capped(long).length)
        assertEquals("short", SearchQueryInput.capped("short"))
    }

    @Test
    fun `capping never splits a surrogate pair`() {
        val text = "a".repeat(SearchQueryInput.MAX_LENGTH - 1) + "😀"

        val capped = SearchQueryInput.capped(text)

        assertEquals(SearchQueryInput.MAX_LENGTH - 1, capped.length)
        assertFalse(capped.last().isHighSurrogate())
    }

    @Test
    fun `applied text is trimmed and blank means the global query`() {
        assertEquals("news", SearchQueryInput.applied("  news  "))
        assertEquals("", SearchQueryInput.applied("   "))
        assertEquals("", SearchQueryInput.applied(""))
    }

    @Test
    fun `needsApply ignores differences the flow would normalise away`() {
        assertFalse(SearchQueryInput.needsApply("news ", "news"))
        assertTrue(SearchQueryInput.needsApply("news", ""))
        assertTrue(SearchQueryInput.needsApply("", "news"))
        assertFalse(SearchQueryInput.needsApply("   ", ""))
    }

    @Test
    fun `counter and limit follow the typed length`() {
        assertEquals("0 / 128", SearchQueryInput.counter(""))
        assertEquals("5 / 128", SearchQueryInput.counter("hello"))
        assertFalse(SearchQueryInput.atLimit("hello"))
        assertTrue(SearchQueryInput.atLimit("a".repeat(SearchQueryInput.MAX_LENGTH)))
    }

    @Test
    fun `the draft round trips an applied query and clears on blank`() {
        val draft = LensDraft(sources = listOf(SourceIds.SEARCH))

        val set = draft.withQuery(SourceIds.SEARCH, SearchQueryInput.applied("  hello "))
        assertEquals("hello", set.queryFor(SourceIds.SEARCH))
        assertFalse(SearchQueryInput.needsApply("hello", set.queryFor(SourceIds.SEARCH)))

        val cleared = set.withQuery(SourceIds.SEARCH, SearchQueryInput.applied(""))
        assertEquals("", cleared.queryFor(SourceIds.SEARCH))
        assertTrue(cleared.parameters.isEmpty())
    }
}
