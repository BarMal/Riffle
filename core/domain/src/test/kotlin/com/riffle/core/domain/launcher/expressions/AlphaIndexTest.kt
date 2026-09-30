package com.riffle.core.domain.launcher.expressions

import kotlin.test.Test
import kotlin.test.assertEquals

class AlphaIndexTest {
    @Test
    fun lettersAreUpperCasedAndFoldDiacritics() {
        assertEquals('A', AlphaIndex.letterOf("alpha"))
        assertEquals('A', AlphaIndex.letterOf("Ärzte"))
        assertEquals('E', AlphaIndex.letterOf("  élan"))
    }

    @Test
    fun nonLatinBlankAndMissingTitlesFileUnderOther() {
        assertEquals(AlphaIndex.OTHER, AlphaIndex.letterOf("7-Zip"))
        assertEquals(AlphaIndex.OTHER, AlphaIndex.letterOf("日本"))
        assertEquals(AlphaIndex.OTHER, AlphaIndex.letterOf("   "))
        assertEquals(AlphaIndex.OTHER, AlphaIndex.letterOf(null))
    }

    @Test
    fun sectionsAreOrderedAndOtherComesLast() {
        val sections = AlphaIndex.sections(listOf("zulu", "1Password", "bravo", "Alpha", "apple")) { it }
        assertEquals(listOf('A', 'B', 'Z', AlphaIndex.OTHER), sections.map { it.letter })
        assertEquals(listOf("Alpha", "apple"), sections.first().entries)
    }

    @Test
    fun sortingIgnoresCaseAndIsStableForTies() {
        val entries = listOf("b" to 1, "B" to 2, "a" to 3)
        val sections = AlphaIndex.sections(entries) { it.first }
        assertEquals(listOf(3), sections[0].entries.map { it.second })
        assertEquals(listOf(1, 2), sections[1].entries.map { it.second })
    }

    @Test
    fun emptyInputHasNoSections() {
        assertEquals(emptyList(), AlphaIndex.sections(emptyList<String>()) { it })
    }

    @Test
    fun headerIndicesCountHeaderRowsAndEntries() {
        val sections = AlphaIndex.sections(listOf("a1", "a2", "b1", "c1", "c2", "c3")) { it }
        assertEquals(mapOf('A' to 0, 'B' to 3, 'C' to 5), AlphaIndex.headerIndices(sections))
    }

    @Test
    fun thinKeepsAllLettersWhenTheyFit() {
        val letters = listOf('A', 'B', 'C')
        assertEquals(letters, AlphaIndex.thin(letters, 3))
        assertEquals(letters, AlphaIndex.thin(letters, 10))
    }

    @Test
    fun thinKeepsFirstAndLastWhenShort() {
        val letters = ('A'..'Z').toList()
        val thinned = AlphaIndex.thin(letters, 7)
        assertEquals(7, thinned.size)
        assertEquals('A', thinned.first())
        assertEquals('Z', thinned.last())
        assertEquals(thinned.distinct(), thinned)
    }

    @Test
    fun thinHandlesDegenerateCapacities() {
        assertEquals(emptyList(), AlphaIndex.thin(listOf('A', 'B'), 0))
        assertEquals(listOf('A'), AlphaIndex.thin(listOf('A', 'B'), 1))
    }

    @Test
    fun cellAtClampsAndMaps() {
        assertEquals(0, AlphaIndex.cellAt(-0.5f, 4))
        assertEquals(0, AlphaIndex.cellAt(0.24f, 4))
        assertEquals(1, AlphaIndex.cellAt(0.26f, 4))
        assertEquals(3, AlphaIndex.cellAt(1f, 4))
        assertEquals(3, AlphaIndex.cellAt(2f, 4))
        assertEquals(0, AlphaIndex.cellAt(0.5f, 0))
        assertEquals(0, AlphaIndex.cellAt(Float.NaN, 4))
    }
}
