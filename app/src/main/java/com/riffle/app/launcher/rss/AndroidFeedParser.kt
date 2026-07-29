package com.riffle.app.launcher.rss

import android.util.Xml
import com.riffle.core.domain.launcher.rss.FeedFormat
import com.riffle.core.domain.launcher.rss.FeedItemInput
import com.riffle.core.domain.launcher.rss.FeedItemNormalizer
import com.riffle.core.domain.launcher.rss.FeedParser
import com.riffle.core.domain.launcher.rss.MAX_FEED_AUTHOR_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_INPUT_ITEMS
import com.riffle.core.domain.launcher.rss.MAX_FEED_PUBLISHED_AT_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_SOURCE_ID_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_SUMMARY_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_TITLE_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_URL_LENGTH
import com.riffle.core.domain.launcher.rss.NormalizedFeed
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

@Suppress("TooManyFunctions")
class AndroidFeedParser : FeedParser {
    override fun parse(body: String): Result<NormalizedFeed> =
        runCatching {
            val parser =
                Xml.newPullParser().apply {
                    setInput(StringReader(body))
                }
            moveToRoot(parser)
            when (parser.name.lowercase()) {
                "rss", "rdf" -> parseRss(parser)
                "feed" -> parseAtom(parser)
                else -> error("Unsupported feed root.")
            }
        }

    private fun parseRss(parser: XmlPullParser): NormalizedFeed {
        var title: String? = null
        val items = mutableListOf<FeedItemInput>()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("channel", ignoreCase = true)) {
                val channel = parseRssChannel(parser)
                title = channel.title
                items += channel.items
                break
            }
            event = parser.next()
        }
        return FeedItemNormalizer.normalize(FeedFormat.RSS_2, title, items)
    }

    @Suppress("NestedBlockDepth")
    private fun parseRssChannel(parser: XmlPullParser): ParsedChannel {
        var title: String? = null
        val items = mutableListOf<FeedItemInput>()
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "title" -> title = readText(parser, MAX_FEED_TITLE_LENGTH)
                    "item" ->
                        if (items.size < MAX_FEED_INPUT_ITEMS) {
                            items += parseRssItem(parser)
                        } else {
                            skipElement(parser)
                        }
                    else -> skipElement(parser)
                }
            }
            event = parser.next()
        }
        return ParsedChannel(title, items)
    }

    private fun parseRssItem(parser: XmlPullParser): FeedItemInput {
        var sourceId: String? = null
        var canonicalUrl: String? = null
        var title: String? = null
        var publishedAt: String? = null
        var summary: String? = null
        var author: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "guid" -> sourceId = readText(parser, MAX_FEED_SOURCE_ID_LENGTH)
                    "link" -> canonicalUrl = readText(parser, MAX_FEED_URL_LENGTH)
                    "title" -> title = readText(parser, MAX_FEED_TITLE_LENGTH)
                    "pubdate" -> publishedAt = readText(parser, MAX_FEED_PUBLISHED_AT_LENGTH)
                    "description" -> summary = readText(parser, MAX_FEED_SUMMARY_LENGTH)
                    "author", "creator" -> author = readText(parser, MAX_FEED_AUTHOR_LENGTH)
                    else -> skipElement(parser)
                }
            }
            event = parser.next()
        }
        return FeedItemInput(sourceId, canonicalUrl, title, author, publishedAt, summary)
    }

    @Suppress("NestedBlockDepth")
    private fun parseAtom(parser: XmlPullParser): NormalizedFeed {
        var title: String? = null
        val items = mutableListOf<FeedItemInput>()
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "title" -> title = readText(parser, MAX_FEED_TITLE_LENGTH)
                    "entry" ->
                        if (items.size < MAX_FEED_INPUT_ITEMS) {
                            items += parseAtomEntry(parser)
                        } else {
                            skipElement(parser)
                        }
                    else -> skipElement(parser)
                }
            }
            event = parser.next()
        }
        return FeedItemNormalizer.normalize(FeedFormat.ATOM, title, items)
    }

    @Suppress("NestedBlockDepth")
    private fun parseAtomEntry(parser: XmlPullParser): FeedItemInput {
        var sourceId: String? = null
        var canonicalUrl: String? = null
        var title: String? = null
        var publishedAt: String? = null
        var summary: String? = null
        var author: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "id" -> sourceId = readText(parser, MAX_FEED_SOURCE_ID_LENGTH)
                    "title" -> title = readText(parser, MAX_FEED_TITLE_LENGTH)
                    "updated", "published" -> publishedAt = readText(parser, MAX_FEED_PUBLISHED_AT_LENGTH)
                    "summary", "content" -> summary = readText(parser, MAX_FEED_SUMMARY_LENGTH)
                    "author" -> author = parseAtomAuthor(parser)
                    "link" ->
                        if (parser.getAttributeValue(null, "rel") in arrayOf(null, "alternate")) {
                            canonicalUrl = parser.getAttributeValue(null, "href")?.take(MAX_FEED_URL_LENGTH + 1)
                            skipElement(parser)
                        } else {
                            skipElement(parser)
                        }
                    else -> skipElement(parser)
                }
            }
            event = parser.next()
        }
        return FeedItemInput(sourceId, canonicalUrl, title, author, publishedAt, summary)
    }

    private fun parseAtomAuthor(parser: XmlPullParser): String? {
        var name: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("name", ignoreCase = true)) {
                name = readText(parser, MAX_FEED_AUTHOR_LENGTH)
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser)
            }
            event = parser.next()
        }
        return name
    }

    private fun moveToRoot(parser: XmlPullParser) {
        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) event = parser.next()
        require(event == XmlPullParser.START_TAG) { "Feed document has no root." }
    }

    private fun readText(
        parser: XmlPullParser,
        maxLength: Int,
    ): String? {
        val output = StringBuilder()
        var depth = 1
        var event = parser.next()
        while (depth > 0 && event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> depth += 1
                XmlPullParser.END_TAG -> depth -= 1
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (output.length + parser.text.length > maxLength) {
                        skipElementRemainder(parser, depth)
                        return null
                    }
                    output.append(parser.text)
                }
            }
            if (depth > 0) {
                event = parser.next()
            }
        }
        return output.toString()
    }

    private fun skipElement(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        skipElementRemainder(parser)
    }

    private fun skipElementRemainder(
        parser: XmlPullParser,
        initialDepth: Int = 1,
    ) {
        var depth = initialDepth
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth += 1
                XmlPullParser.END_TAG -> depth -= 1
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private data class ParsedChannel(
        val title: String?,
        val items: List<FeedItemInput>,
    )
}
