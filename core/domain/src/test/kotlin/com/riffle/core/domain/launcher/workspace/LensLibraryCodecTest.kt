package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensLibraryCodecTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private fun withLibrary(): LayoutWorkspaces {
        val added =
            LensLibrary().tryAdd("Recent", llLens(limit = 5), llCounter("lib"), LensOrigin.Preset("nova", "finder"))
                as LibraryAdd.Added
        val ws =
            Workspace(
                WorkspaceId("w"),
                "w",
                listOf(llPage("p", llBinding(ExpressionKind.LIST, llLens(limit = 5), added.id))),
            )
        return LayoutWorkspaces.single(ws).copy(library = added.library)
    }

    private fun roundTrip(set: WorkspaceSet) = WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set))

    @Test
    fun `schema 2 round trips the library, refs and origins`() {
        val set = WorkspaceSet(mapOf(phone to withLibrary()))
        val encoded = WorkspaceSetCodec.encode(set)
        assertEquals(StoredValue.Num(2), encoded.fields["version"])
        assertEquals(set, WorkspaceSetCodec.decode(encoded))
    }

    @Test
    fun `an empty library is not written and a v1 blob decodes unchanged`() {
        val plain = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(llWorkspace())))
        val layoutValue = (WorkspaceSetCodec.encode(plain).fields["layouts"] as StoredValue.Arr).items.single()
        assertNull((layoutValue as StoredValue.Obj).fields["library"])
        val v1 = WorkspaceSetCodec.encode(plain).let { StoredValue.Obj(it.fields + ("version" to num(1))) }
        val decoded = assertNotNull(WorkspaceSetCodec.decode(v1)).workspacesFor(phone)
        assertEquals(plain.workspacesFor(phone), decoded)
        assertTrue(decoded.library.lenses.isEmpty())
        assertTrue(decoded.allBindings().all { it.ref == null })
    }

    @Test
    fun `a reader that ignores library and ref still draws every container`() {
        val encoded = WorkspaceSetCodec.encode(WorkspaceSet(mapOf(phone to withLibrary())))
        val stripped = strip(encoded) as StoredValue.Obj
        val decoded = assertNotNull(WorkspaceSetCodec.decode(stripped)).workspacesFor(phone)
        assertEquals(1, decoded.active.pages.size)
        assertEquals(llLens(limit = 5), decoded.allBindings().single().lens)
        assertTrue(decoded.issues().isEmpty())
    }

    private fun strip(value: StoredValue): StoredValue =
        when (value) {
            is StoredValue.Obj ->
                StoredValue.Obj(
                    value.fields.filterKeys { it != "library" && it != "ref" }.mapValues { strip(it.value) },
                )
            is StoredValue.Arr -> StoredValue.Arr(value.items.map(::strip))
            else -> value
        }

    @Test
    fun `a stale snapshot is refreshed from the library on decode`() {
        val layout = withLibrary()
        val id = layout.library.lenses.single().id
        val stale = layout.copy(library = layout.library.update(id, llLens(limit = 9)))
        val decoded = assertNotNull(roundTrip(WorkspaceSet(mapOf(phone to stale)))).workspacesFor(phone)
        assertEquals(llLens(limit = 9), decoded.allBindings().single().lens)
    }

    @Test
    fun `a dangling ref survives a round trip and keeps drawing its snapshot`() {
        val layout = withLibrary()
        val dangling = layout.copy(library = LensLibrary())
        val decoded = assertNotNull(roundTrip(WorkspaceSet(mapOf(phone to dangling)))).workspacesFor(phone)
        assertEquals(1, LensLibraryOps.danglingRefs(decoded).size)
        assertEquals(llLens(limit = 5), decoded.allBindings().single().lens)
        assertTrue(decoded.issues().isEmpty())
    }

    @Test
    fun `hostile library data decodes safely`() {
        val layout = withLibrary()
        val good = LensLibraryCodec.encode(layout.library).array("lenses").single()
        val hostile =
            arr(
                listOf(
                    good,
                    good,
                    str("junk"),
                    obj("id" to str("   "), "name" to str("blank id"), "lens" to LensCodec.encode(llLens())),
                    obj("id" to str("no-lens"), "name" to str("x")),
                    obj("id" to str("no-sources"), "lens" to obj("sources" to arr(emptyList()))),
                    obj(
                        "id" to str("odd"),
                        "name" to str("y".repeat(200)),
                        "lens" to LensCodec.encode(llLens()),
                        "origin" to obj("type" to str("from-the-future")),
                    ),
                    obj("id" to str("z"), "name" to str("RECENT"), "lens" to LensCodec.encode(llLens())),
                ),
            )
        val decoded = LensLibraryCodec.decode(obj("lenses" to hostile))
        val expected = listOf("recent", "y".repeat(40), "recent 2")
        assertEquals(expected, decoded.lenses.map { it.name.lowercase() })
        assertEquals(LensOrigin.Preset("nova", "finder"), decoded.lenses[0].origin)
        assertNull(decoded.lenses[1].origin)
        assertTrue(LensLibraryCodec.decode(null).lenses.isEmpty())
        assertTrue(LensLibraryCodec.decode(obj("lenses" to str("x"))).lenses.isEmpty())
    }

    @Test
    fun `a preset origin keeps its installed definition, and an older or broken one reads as unknown`() {
        val installed = llLens(limit = 5)
        val library =
            (
                LensLibrary().tryAdd(
                    "Recent",
                    installed,
                    llCounter("lib"),
                    LensOrigin.Preset("nova", "finder", installed),
                ) as LibraryAdd.Added
            ).library
        val decoded = LensLibraryCodec.decode(LensLibraryCodec.encode(library))
        assertEquals(library, decoded)
        assertTrue(decoded.lenses.single().isUntouchedPresetLens)

        fun entry(origin: StoredValue.Obj) =
            obj("id" to str("a"), "name" to str("A"), "lens" to LensCodec.encode(installed), "origin" to origin)
        val older = obj("type" to str("preset"), "preset" to str("nova"), "key" to str("finder"))
        val broken =
            obj(
                "type" to str("preset"),
                "preset" to str("nova"),
                "key" to str("finder"),
                "installed" to obj("sources" to arr(emptyList())),
            )
        listOf(older, broken).forEach { origin ->
            val saved = LensLibraryCodec.decode(obj("lenses" to arr(listOf(entry(origin))))).lenses.single()
            assertEquals(LensOrigin.Preset("nova", "finder"), saved.origin)
            assertTrue(!saved.isUntouchedPresetLens)
        }
        // An entry without an origin is the user's own.
        val own = SavedLens(LensId("u"), "Mine", installed)
        assertTrue(!own.isUntouchedPresetLens)
    }

    @Test
    fun `a hostile ref id decodes as a dangling ref, a blank one as inline`() {
        val ws = llWorkspace()
        val value = WorkspaceCodec.encode(ws.copy(pages = listOf(llPage("p", llBinding(ref = LensId("../../x"))))))
        val decoded = assertNotNull(WorkspaceCodec.decodeWorkspace(value))
        assertEquals(LensId("../../x"), WorkspaceBindings.sites(decoded).first().binding.ref)
        val blank = LensCodec.decodeBinding(obj("lens" to LensCodec.encode(llLens()), "ref" to str("  ")))
        assertNull(blank?.ref)
        val wrongType = LensCodec.decodeBinding(obj("lens" to LensCodec.encode(llLens()), "ref" to num(3)))
        assertNull(wrongType?.ref)
    }
}
