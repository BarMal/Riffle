package com.riffle.core.domain.launcher.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconPackAppFilterTest {
    private val sample =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <!-- a comment <item component="ComponentInfo{com.example.hidden/com.example.hidden.Main}" drawable="no"/> -->
            <iconback img1="back_one" img2="back_two" />
            <iconmask img1="mask" />
            <iconupon img1="upon" />
            <scale factor="0.8" />
            <item component="ComponentInfo{com.example.notes/com.example.notes.Main}" drawable="notes" />
            <item component="ComponentInfo{com.example.notes/com.example.notes.Main}" drawable="notes_alt" />
            <item component="ComponentInfo{com.example.maps/.Main}" drawable="maps"/>
            <item component="com.example.mail/com.example.mail.Inbox" drawable='mail'/>
            <calendar component="ComponentInfo{com.example.cal/com.example.cal.Main}" prefix="cal_" />
        </resources>
        """.trimIndent()

    @Test
    fun readsTheMappingAndTheDressing() {
        val mapping = IconPackAppFilter.parse("com.example.pack", sample)

        assertEquals("notes", mapping.drawableFor("com.example.notes", "com.example.notes.Main"))
        assertEquals("maps", mapping.drawableFor("com.example.maps", "com.example.maps.Main"))
        assertEquals("mail", mapping.drawableFor("com.example.mail", "com.example.mail.Inbox"))
        assertNull(mapping.drawableFor("com.example.hidden", "com.example.hidden.Main"))
        assertNull(mapping.drawableFor("com.example.cal", "com.example.cal.Main"))
        assertEquals(listOf("back_one", "back_two"), mapping.iconBack)
        assertEquals("mask", mapping.iconMask)
        assertEquals("upon", mapping.iconUpon)
        assertEquals(0.8f, mapping.scale)
        assertTrue(mapping.dressesUnthemedApps)
    }

    @Test
    fun aBackPlateIsStablePerPackage() {
        val mapping = IconPackAppFilter.parse("com.example.pack", sample)

        assertEquals(mapping.backFor("com.example.notes"), mapping.backFor("com.example.notes"))
    }

    @Test
    fun malformedXmlYieldsWhatCouldBeRead() {
        val xml =
            """<resources><item component="ComponentInfo{a.b/a.b.C}" drawable="ok"/>""" +
                """<item component="ComponentInfo{"""

        val mapping = IconPackAppFilter.parse("com.example.pack", xml)

        assertEquals(mapOf("a.b/a.b.C" to "ok"), mapping.drawables)
    }

    @Test
    fun garbageAndEmptyInputYieldAnEmptyMapping() {
        assertEquals(IconPackMapping("p"), IconPackAppFilter.parse("p", ""))
        assertEquals(IconPackMapping("p"), IconPackAppFilter.parse("p", "\u0000\u0001 not xml <<<>>> &&"))
    }

    @Test
    fun entriesWithoutAComponentOrADrawableAreSkipped() {
        val xml =
            """<resources><item drawable="a"/><item component="x"/><item component="ComponentInfo{p/q}" drawable="../evil"/>
            <item component="ComponentInfo{p/q}" drawable="fine"/></resources>"""

        assertEquals(mapOf("p/q" to "fine"), IconPackAppFilter.parse("k", xml).drawables)
    }

    @Test
    fun aHugeFileIsNotRead() {
        val entry = "<item component=\"ComponentInfo{a/b}\" drawable=\"x\"/>"
        val huge = "<resources>" + entry.repeat(200_000) + "</resources>"

        assertTrue(huge.length > IconPackAppFilter.MAX_XML_CHARS)
        assertEquals(IconPackMapping("k"), IconPackAppFilter.parse("k", huge))
    }

    @Test
    fun theNumberOfEntriesIsBounded() {
        val xml =
            buildString {
                append("<resources>")
                repeat(IconPackAppFilter.MAX_ENTRIES + 50) {
                    append("<item component=\"ComponentInfo{a/b$it}\" drawable=\"x$it\"/>")
                }
                append("</resources>")
            }

        assertEquals(IconPackAppFilter.MAX_ENTRIES, IconPackAppFilter.parse("k", xml).drawables.size)
    }

    @Test
    fun theScaleIsHeldToARange() {
        assertEquals(1.6f, IconPackAppFilter.parse("k", """<scale factor="40"/>""").scale)
        assertEquals(1f, IconPackAppFilter.parse("k", """<scale factor="NaN"/>""").scale)
        assertEquals(0.3f, IconPackAppFilter.parse("k", """<scale factor="0"/>""").scale)
    }

    @Test
    fun entitiesInAttributesAreDecoded() {
        val tags = IconPackAppFilter.tagsOf("""<item drawable="a&amp;b"/>""")

        assertEquals("a&b", tags.single().attributes["drawable"])
    }

    @Test
    fun theDrawableListIsUniqueAndInFileOrder() {
        val tags = IconPackAppFilter.tagsOf("""<r><item drawable="b"/><item drawable="a"/><item drawable="b"/></r>""")

        assertEquals(listOf("b", "a"), IconPackAppFilter.drawableList(tags))
    }
}
