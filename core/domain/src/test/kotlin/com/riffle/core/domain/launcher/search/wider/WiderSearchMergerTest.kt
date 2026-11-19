package com.riffle.core.domain.launcher.search.wider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiderSearchMergerTest {
    private fun setting(
        name: String,
        score: Int = 80,
    ) = ScoredResult(WiderSearchResult.SystemSetting(name, name, "action.$name"), score)

    private fun contact(
        name: String,
        score: Int = 80,
    ) = ScoredResult(WiderSearchResult.Contact("content://contacts/$name", name), score)

    private fun snapshot(vararg groups: Pair<SearchSourceId, List<ScoredResult>>) =
        WiderSearchSnapshot(query = "q", results = groups.toMap())

    private val order = SearchSourceId.DEFAULT_GROUP_ORDER

    @Test
    fun appsStayFirstAndGroupsFollowInTheSetOrder() {
        val merged =
            WiderSearchMerger.merge(
                primary = listOf("App A", "App B"),
                snapshot =
                    snapshot(
                        SearchSourceId.CONTACTS to listOf(contact("Zoe")),
                        SearchSourceId.SYSTEM_SETTINGS to listOf(setting("wifi")),
                    ),
                order = listOf(SearchSourceId.CONTACTS, SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.FILES),
                perGroup = 5,
            )
        assertEquals(listOf("App A", "App B"), merged.primary)
        assertEquals(listOf(SearchSourceId.CONTACTS, SearchSourceId.SYSTEM_SETTINGS), merged.groups.map { it.source })
        val reversed =
            WiderSearchMerger.merge(
                listOf("App A"),
                snapshot(
                    SearchSourceId.CONTACTS to listOf(contact("Zoe")),
                    SearchSourceId.SYSTEM_SETTINGS to listOf(setting("wifi")),
                ),
                order,
                5,
            )
        assertEquals(listOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS), reversed.groups.map { it.source })
    }

    @Test
    fun aGroupIsCappedAndSaysHowManyMoreThereAre() {
        val results = (1..7).map { setting("s$it", score = 100 - it) }
        val capped =
            WiderSearchMerger.merge(
                primary = emptyList<String>(),
                snapshot = snapshot(SearchSourceId.SYSTEM_SETTINGS to results),
                order = order,
                perGroup = 3,
            ).groups.single()
        assertEquals(listOf("s1", "s2", "s3"), capped.rows.map { (it as WiderSearchResult.SystemSetting).id })
        assertEquals(4, capped.hiddenCount)

        val expanded =
            WiderSearchMerger.merge(
                primary = emptyList<String>(),
                snapshot = snapshot(SearchSourceId.SYSTEM_SETTINGS to results),
                order = order,
                perGroup = 3,
                expanded = setOf(SearchSourceId.SYSTEM_SETTINGS),
            ).groups.single()
        assertEquals(7, expanded.rows.size)
        assertEquals(0, expanded.hiddenCount)
    }

    @Test
    fun aGroupThatFitsHasNoShowMore() {
        val group =
            WiderSearchMerger.merge(
                emptyList<String>(),
                snapshot(SearchSourceId.CONTACTS to listOf(contact("A"), contact("B"))),
                order,
                perGroup = 3,
            ).groups.single()
        assertEquals(0, group.hiddenCount)
    }

    @Test
    fun resultsInAGroupAreBestFirstAndTiesKeepTheSourcesOrder() {
        val group =
            WiderSearchMerger.merge(
                emptyList<String>(),
                snapshot(
                    SearchSourceId.CONTACTS to listOf(contact("Low", 60), contact("First", 80), contact("Second", 80)),
                ),
                order,
                perGroup = 5,
            ).groups.single()
        assertEquals(
            listOf("First", "Second", "Low"),
            group.rows.map { (it as WiderSearchResult.Contact).name },
        )
    }

    @Test
    fun aTrailingRowStaysLastAndIsNotCounted() {
        val files =
            (1..4).map {
                ScoredResult(WiderSearchResult.FileHit("content://f/$it", "f$it", null, FileKind.DOCUMENT), 80)
            } + ScoredResult(WiderSearchResult.FilesApp("q"), 0)
        val group =
            WiderSearchMerger.merge(
                emptyList<String>(),
                snapshot(SearchSourceId.FILES to files),
                order,
                perGroup = 3,
            ).groups.single()
        assertEquals(4, group.rows.size)
        assertTrue(group.rows.last() is WiderSearchResult.FilesApp)
        assertEquals(1, group.hiddenCount)
    }

    @Test
    fun theCalculatorAnswerLeadsEverythingAndIsNotAGroup() {
        val answer = WiderSearchResult.CalculatorAnswer("1+1", "2")
        val merged =
            WiderSearchMerger.merge(
                primary = listOf("App"),
                snapshot =
                    snapshot(
                        SearchSourceId.CALCULATOR to listOf(ScoredResult(answer, 100)),
                        SearchSourceId.CONTACTS to listOf(contact("Zoe")),
                    ),
                order = order,
                perGroup = 5,
            )
        assertEquals(listOf<WiderSearchResult>(answer), merged.answers)
        assertEquals(answer, merged.topWiderResult)
        assertEquals(listOf(SearchSourceId.CONTACTS), merged.groups.map { it.source })
    }

    @Test
    fun aSourceWithoutItsPermissionIsOneAllowRowInItsPlace() {
        val merged =
            WiderSearchMerger.merge(
                primary = listOf("App"),
                snapshot = snapshot(SearchSourceId.SYSTEM_SETTINGS to listOf(setting("wifi"))),
                order = order,
                perGroup = 5,
                needsPermission = setOf(SearchSourceId.CONTACTS),
            )
        assertEquals(
            listOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS),
            merged.groups.map { it.source },
        )
        assertTrue(merged.groups.last().needsPermission)
        assertTrue(merged.groups.last().rows.isEmpty())
    }

    @Test
    fun aSourceThatStillOffersARowKeepsItBesideTheAllowRow() {
        val merged =
            WiderSearchMerger.merge(
                primary = emptyList<String>(),
                snapshot = snapshot(SearchSourceId.FILES to listOf(ScoredResult(WiderSearchResult.FilesApp("q"), 0))),
                order = order,
                perGroup = 5,
                needsPermission = setOf(SearchSourceId.FILES),
            )
        val files = merged.groups.single()
        assertTrue(files.needsPermission)
        assertTrue(files.rows.single() is WiderSearchResult.FilesApp)
    }

    @Test
    fun headersShowOnlyWhenMoreThanOneGroupHasResults() {
        fun merged(
            primary: List<String>,
            vararg groups: Pair<SearchSourceId, List<ScoredResult>>,
        ) = WiderSearchMerger.merge(primary, snapshot(*groups), order, 5)
        assertFalse(merged(listOf("App")).showHeaders)
        assertFalse(merged(emptyList(), SearchSourceId.CONTACTS to listOf(contact("Zoe"))).showHeaders)
        assertFalse(merged(listOf("App")).showHeaders)
        assertTrue(merged(listOf("App"), SearchSourceId.CONTACTS to listOf(contact("Zoe"))).showHeaders)
        assertTrue(
            merged(
                emptyList(),
                SearchSourceId.CONTACTS to listOf(contact("Zoe")),
                SearchSourceId.SYSTEM_SETTINGS to listOf(setting("wifi")),
            ).showHeaders,
        )
    }

    @Test
    fun enterActsOnAnAppFirstAndOnAGroupRowOnlyWhenThereAreNoApps() {
        val withApps =
            WiderSearchMerger.merge(
                listOf("App"),
                snapshot(SearchSourceId.CONTACTS to listOf(contact("Zoe"))),
                order,
                5,
            )
        assertNull(withApps.topWiderResult)
        val without =
            WiderSearchMerger.merge(
                emptyList<String>(),
                snapshot(SearchSourceId.CONTACTS to listOf(contact("Zoe"))),
                order,
                5,
            )
        assertEquals("Zoe", (without.topWiderResult as WiderSearchResult.Contact).name)
    }

    @Test
    fun anIncompleteSavedOrderIsCompletedWithoutRepeats() {
        assertEquals(
            listOf(SearchSourceId.FILES, SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS),
            WiderSearchMerger.completeOrder(
                listOf(SearchSourceId.FILES, SearchSourceId.FILES, SearchSourceId.CALCULATOR),
            ),
        )
    }
}
