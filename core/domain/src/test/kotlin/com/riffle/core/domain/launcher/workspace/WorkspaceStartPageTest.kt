package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStartPageTest {
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

    // ---- pager / finder model ----

    @Test
    fun finderIsNotAPagerPageWhateverItsPosition() {
        val ws = workspace(finder("f"), page("a"), page("b"))
        assertEquals(listOf(id("a"), id("b")), ws.pagerPages.map { it.id })
        assertEquals(id("f"), ws.finderPage?.id)
        assertEquals(id("a"), ws.firstPageId)
        assertTrue(ws.isFinder(id("f")))
        assertFalse(ws.isFinder(id("a")))
    }

    @Test
    fun aWorkspaceWithoutAFinderHasEveryPageInThePager() {
        val ws = workspace(page("a"), page("b"))
        assertEquals(listOf(id("a"), id("b")), ws.pagerPages.map { it.id })
        assertNull(ws.finderPage)
    }

    @Test
    fun aFinderOnlyWorkspaceKeepsItsOnlyPageInThePager() {
        val ws = workspace(finder("f"))
        assertEquals(listOf(id("f")), ws.pagerPages.map { it.id })
        assertEquals(id("f"), ws.firstPageId)
    }

    @Test
    fun noPagesMeansNoFirstOrStartPage() {
        val ws = workspace()
        assertNull(ws.firstPageId)
        assertNull(ws.effectiveStartPageId)
    }

    // ---- start page ----

    @Test
    fun startPageDefaultsToTheFirstNonFinderPage() {
        assertEquals(id("a"), workspace(finder("f"), page("a"), page("b")).effectiveStartPageId)
    }

    @Test
    fun explicitStartPageWins() {
        assertEquals(id("b"), workspace(page("a"), page("b"), start = "b").effectiveStartPageId)
    }

    @Test
    fun startPageMayNameTheFinder() {
        assertEquals(id("f"), workspace(page("a"), finder("f"), start = "f").effectiveStartPageId)
    }

    @Test
    fun aStaleStartPageIsIgnoredNotAnError() {
        val ws = workspace(page("a"), page("b"), start = "gone")
        assertEquals(id("a"), ws.effectiveStartPageId)
        assertTrue(WorkspaceValidation.validate(ws).isEmpty())
    }

    @Test
    fun validationTreatsTheFinderAsReachableByItsOwnSurface() {
        assertTrue(WorkspaceValidation.validate(workspace(page("a"), finder("f"))).isEmpty())
        assertTrue(WorkspaceValidation.validate(workspace(finder("f"))).isEmpty())
        assertTrue(WorkspaceValidation.validate(workspace(page("a"), finder("f"), start = "f")).isEmpty())
    }

    // ---- codec ----

    @Test
    fun startPageRoundTripsAndIsEncodedOnlyWhenPresent() {
        val with = workspace(page("a"), page("b"), start = "b")
        val encoded = WorkspaceCodec.encode(with)
        assertEquals(StoredValue.Str("b"), encoded.fields["start"])
        assertEquals(with, WorkspaceCodec.decodeWorkspace(encoded))

        val without = workspace(page("a"))
        assertFalse("start" in WorkspaceCodec.encode(without).fields)
        assertNull(WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(without))?.startPageId)
    }

    @Test
    fun missingOrMalformedStartDecodesToNoStartPage() {
        val encoded = WorkspaceCodec.encode(workspace(page("a"), start = "a"))
        val bad =
            listOf(null, StoredValue.Str(""), StoredValue.Str("  "), StoredValue.Num(3), StoredValue.Arr(emptyList()))
        bad.forEach { value ->
            val fields = encoded.fields.toMutableMap()
            if (value == null) fields.remove("start") else fields["start"] = value
            val decoded = WorkspaceCodec.decodeWorkspace(StoredValue.Obj(fields))
            assertNull(decoded?.startPageId, "bad value: $value")
        }
    }

    @Test
    fun schemaVersionIsUnchanged() {
        assertEquals(1, CURRENT_WORKSPACE_SCHEMA_VERSION)
    }

    @Test
    fun aCopyKeepsThePointOfTheStartPageOnItsNewId() {
        var n = 0
        val ids = WorkspaceIdFactory { "n${n++}" }
        val copy = WorkspaceCopy.withFreshIds(workspace(page("a"), page("b"), start = "b"), ids)
        assertEquals(copy.pages[1].id, copy.startPageId)
    }

    @Test
    fun aCopyDropsAStaleStartPage() {
        val copy = WorkspaceCopy.withFreshIds(workspace(page("a"), start = "gone"), WorkspaceIdFactory { "x" })
        assertNull(copy.startPageId)
    }
}
