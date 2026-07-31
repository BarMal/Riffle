package com.riffle.app.launcher.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeedArticleTextSanitizerTest {
    @Test
    fun stripsSimpleTags() {
        assertEquals("Hello world", stripHtmlMarkup("<p>Hello <b>world</b></p>"))
    }

    @Test
    fun stripsTagsWithAttributesAndSelfClosingTags() {
        assertEquals(
            "Before After",
            stripHtmlMarkup("Before<img src=\"https://evil.example/pixel.gif\"/>After"),
        )
        assertEquals("Line one Line two", stripHtmlMarkup("Line one<br/>Line two"))
    }

    @Test
    fun decodesCommonNamedEntities() {
        assertEquals("Fish & chips <3", stripHtmlMarkup("Fish &amp; chips &lt;3"))
        assertEquals("\"quoted\" and 'apos'", stripHtmlMarkup("&quot;quoted&quot; and &apos;apos&apos;"))
    }

    @Test
    fun decodesNumericAndHexEntities() {
        assertEquals("A B", stripHtmlMarkup("&#65; &#x42;"))
    }

    @Test
    fun collapsesWhitespaceProducedByTagRemoval() {
        assertEquals(
            "A B C",
            stripHtmlMarkup("A<div>\n</div>B<div>\n\n</div>C"),
        )
    }

    @Test
    fun leavesPlainTextUnchanged() {
        assertEquals("Just plain text.", stripHtmlMarkup("Just plain text."))
    }

    @Test
    fun neverLeavesAnAngleBracketTagBehind() {
        val sanitized = stripHtmlMarkup("<script>alert('x')</script>Safe text<style>body{}</style>")

        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertEquals("alert('x') Safe text body{}", sanitized)
    }

    @Test
    fun handlesEmptyAndBlankInput() {
        assertEquals("", stripHtmlMarkup(""))
        assertEquals("", stripHtmlMarkup("   "))
    }

    @Test
    fun ignoresAmpersandsThatAreNotWellFormedEntities() {
        assertEquals("Tom & Jerry", stripHtmlMarkup("Tom & Jerry"))
        assertEquals("R&D department", stripHtmlMarkup("R&D department"))
    }
}
