package com.riffle.app.launcher.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFeedParserTest {
    private val parser = AndroidFeedParser()

    @Test
    fun parsesBoundedRssFieldsAndNormalizesOrder() {
        val feed =
            parser.parse(
                """
                <rss version="2.0"><channel><title>News</title>
                  <item><guid>old</guid><title>Old</title><link>https://example.com/old</link><pubDate>Tue, 28 Jul 2026 10:00:00 GMT</pubDate><description>Summary</description></item>
                  <item><guid>new</guid><title>New</title><link>https://example.com/new</link><pubDate>2026-07-29T10:00:00Z</pubDate><author>Ada</author></item>
                </channel></rss>
                """.trimIndent(),
            ).getOrThrow()

        assertEquals("News", feed.title)
        assertEquals(listOf("New", "Old"), feed.items.map { item -> item.title })
        assertEquals("https://example.com/new", feed.items.first().canonicalUrl)
    }

    @Test
    fun parsesAtomEntriesAndRejectsUnsupportedRoot() {
        val feed =
            parser.parse(
                """
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title>
                  <entry><id>entry-1</id><title>Entry</title><link href="https://example.com/entry"/><updated>2026-07-29T10:00:00Z</updated><author><name>Ada</name></author><summary>Text</summary></entry>
                </feed>
                """.trimIndent(),
            ).getOrThrow()
        assertEquals("Atom", feed.title)
        assertEquals("Ada", feed.items.single().author)

        assertTrue(parser.parse("<html><title>Nope</title></html>").isFailure)
    }

    @Test
    fun preservesFieldsAfterNestedAtomXhtmlAndRssMarkup() {
        val atom =
            parser.parse(
                """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">Atom <b>News</b> Feed</div></title>
                  <entry><id>atom-1</id><title>Entry</title><summary type="xhtml"><div><p>First</p><p>Second</p></div></summary><author><name>Ada</name></author></entry>
                </feed>
                """.trimIndent(),
            ).getOrThrow()
        assertEquals("Atom News Feed", atom.title)
        assertEquals("Ada", atom.items.single().author)
        assertEquals("FirstSecond", atom.items.single().summary)

        val rss =
            parser.parse(
                """
                <rss><channel><title>RSS</title><item><guid>rss-1</guid><title>Entry</title><description><div>First <b>part</b></div> second</description><author>Ada</author></item></channel></rss>
                """.trimIndent(),
            ).getOrThrow()
        assertEquals("Ada", rss.items.single().author)
        assertEquals("First part second", rss.items.single().summary)
    }

    @Test
    fun skipsEntriesAfterInputCapAndOversizedText() {
        val entries =
            buildString {
                append("<rss><channel><title>Feed</title>")
                repeat(501) { index ->
                    append("<item><guid>id-$index</guid><title>Item $index</title></item>")
                }
                append("</channel></rss>")
            }
        val feed = parser.parse(entries).getOrThrow()
        assertEquals(50, feed.items.size)
        assertTrue(feed.items.none { item -> item.identity == "id-500" })

        val oversized =
            parser.parse(
                "<rss><channel><item><title>${"x".repeat(257)}</title></item></channel></rss>",
            ).getOrThrow()
        assertTrue(oversized.items.isEmpty())

        val oversizedNested =
            parser.parse(
                "<feed><title><div>${"x".repeat(
                    257,
                )}</div></title><entry><id>kept</id><title>Kept</title></entry></feed>",
            ).getOrThrow()
        assertEquals("Kept", oversizedNested.items.single().title)
    }
}
