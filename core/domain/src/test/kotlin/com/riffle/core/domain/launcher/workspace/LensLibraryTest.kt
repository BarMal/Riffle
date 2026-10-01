package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LensLibraryTest {
    private fun library(vararg names: String): LensLibrary =
        names.fold(LensLibrary()) { lib, name ->
            (lib.tryAdd(name, llLens(), llCounter(name)) as LibraryAdd.Added).library
        }

    @Test
    fun `names are trimmed and unique case-insensitively`() {
        val lib = library("Inbox")
        assertEquals(LibraryProblem.NAME_TAKEN, lib.nameProblem("  inbox "))
        assertEquals(LibraryProblem.BLANK_NAME, lib.nameProblem("   "))
        assertEquals(LibraryProblem.NAME_TOO_LONG, lib.nameProblem("x".repeat(41)))
        assertNull(lib.nameProblem("x".repeat(40)))
        val rejected = lib.tryAdd("INBOX", llLens())
        assertEquals(LibraryAdd.Rejected(LibraryProblem.NAME_TAKEN), rejected)
        val added = lib.tryAdd("  Work  ", llLens(), llCounter("z"))
        assertEquals("Work", assertIs<LibraryAdd.Added>(added).library.lenses.last().name)
    }

    @Test
    fun `rename keeps the name unique and no-ops otherwise`() {
        val lib = library("A", "B")
        val a = lib.lenses[0].id
        assertSame(lib, lib.rename(a, "b"))
        assertSame(lib, lib.rename(LensId("nope"), "C"))
        assertEquals("a", lib.rename(a, "a").find(a)?.name)
        assertEquals("C", lib.rename(a, " C ").find(a)?.name)
    }

    @Test
    fun `duplicate inserts after the source with a copy name and no origin`() {
        val withOrigin =
            (
                LensLibrary().tryAdd(
                    "Finder",
                    llLens(),
                    llCounter("a"),
                    LensOrigin.Preset("nova", "finder"),
                ) as LibraryAdd.Added
            )
                .library
        val once = withOrigin.duplicate(withOrigin.lenses[0].id, llCounter("b"))
        assertEquals(listOf("Finder", "Finder copy"), once.lenses.map { it.name })
        assertNull(once.lenses[1].origin)
        val twice = once.duplicate(once.lenses[0].id, llCounter("c"))
        assertEquals(listOf("Finder", "Finder copy 2", "Finder copy"), twice.lenses.map { it.name })
    }

    @Test
    fun `the library is capped`() {
        var lib = LensLibrary()
        repeat(MAX_SAVED_LENSES) { lib = (lib.tryAdd("L$it", llLens(), llCounter("c$it")) as LibraryAdd.Added).library }
        assertEquals(LibraryAdd.Rejected(LibraryProblem.LIBRARY_FULL), lib.tryAdd("extra", llLens()))
        assertSame(lib, lib.duplicate(lib.lenses[0].id))
        assertNull(lib.addCopy(lib.lenses[0]))
    }

    @Test
    fun `addCopy gives a fresh id and a collision-free name and keeps the origin`() {
        val origin = LensOrigin.Preset("nova", "finder")
        val source = SavedLens(LensId("s"), "Finder", llLens(), origin)
        val lib = library("Finder")
        val (copied, id) = checkNotNull(lib.addCopy(source, llCounter("q")))
        assertEquals(listOf("Finder", "Finder 2"), copied.lenses.map { it.name })
        assertEquals(origin, copied.find(id)?.origin)
        assertTrue(copied.lenses.map { it.id }.toSet().size == 2)
    }

    @Test
    fun `move and remove`() {
        val lib = library("A", "B", "C")
        val a = lib.lenses[0].id
        assertEquals(listOf("B", "C", "A"), lib.move(a, 99).lenses.map { it.name })
        assertEquals(listOf("B", "C"), lib.remove(a).lenses.map { it.name })
    }

    @Test
    fun `repaired drops duplicate ids, renames clashes and bounds the size`() {
        val entries =
            listOf(
                SavedLens(LensId("1"), "Same", llLens()),
                SavedLens(LensId("1"), "Dup id", llLens()),
                SavedLens(LensId("2"), "same", llLens()),
                SavedLens(LensId("3"), "", llLens()),
                SavedLens(LensId("4"), "y".repeat(80), llLens()),
            )
        val repaired = LensNames.repaired(entries)
        assertEquals(listOf("1", "2", "3", "4"), repaired.lenses.map { it.id.value })
        assertEquals(repaired.lenses.size, repaired.lenses.map { it.name.lowercase() }.toSet().size)
        assertTrue(repaired.lenses.all { it.name.length in 1..MAX_SAVED_LENS_NAME })
        val many = LensNames.repaired((0 until 150).map { SavedLens(LensId("i$it"), "n", llLens()) })
        assertEquals(MAX_SAVED_LENSES, many.lenses.size)
    }
}
