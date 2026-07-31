package com.riffle.app.launcher.rss

/**
 * Minimal, dependency-free HTML markup stripper for feed article text.
 *
 * `FeedItemNormalizer` (core/domain `FeedModel.kt`) only collapses whitespace and caps field
 * length -- it never strips HTML tags or decodes entities, so raw markup from an RSS/Atom
 * `<title>`/`<description>`/`<summary>` can reach [CachedFeedArticle] fields unchanged. Per
 * ADR 0001, the launcher never embeds a WebView or HTML engine and never renders raw HTML; this
 * utility produces plain text safe to hand to a Compose `Text()` composable. It is intentionally
 * simple (regex tag removal plus a small named/numeric entity decode table), not a general HTML
 * parser.
 */
fun stripHtmlMarkup(raw: String): String {
    val withoutTags = raw.replace(HTML_TAG_REGEX, " ")
    val withoutEntities = decodeHtmlEntities(withoutTags)
    return withoutEntities.replace(WHITESPACE_REGEX, " ").trim()
}

private val HTML_TAG_REGEX = Regex("</?[a-zA-Z!][^>]*>")
private val WHITESPACE_REGEX = Regex("\\s+")

private val NAMED_HTML_ENTITIES =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

private const val MAX_ENTITY_REFERENCE_LENGTH = 10

private fun decodeHtmlEntities(value: String): String {
    if ('&' !in value) return value
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        val decoded = if (character == '&') decodeEntityAt(value, index) else null
        if (decoded != null) {
            builder.append(decoded.text)
            index = decoded.nextIndex
        } else {
            builder.append(character)
            index++
        }
    }
    return builder.toString()
}

private data class DecodedEntity(val text: String, val nextIndex: Int)

@Suppress("ReturnCount")
private fun decodeEntityAt(
    value: String,
    ampersandIndex: Int,
): DecodedEntity? {
    val semicolon = value.indexOf(';', ampersandIndex + 1)
    if (semicolon !in (ampersandIndex + 1)..(ampersandIndex + MAX_ENTITY_REFERENCE_LENGTH)) return null
    val entity = value.substring(ampersandIndex + 1, semicolon)
    val decoded = decodeEntityReference(entity) ?: return null
    return DecodedEntity(decoded, semicolon + 1)
}

private fun decodeEntityReference(entity: String): String? =
    when {
        entity.startsWith("#x", ignoreCase = true) ->
            entity.drop(2).toIntOrNull(radix = 16)?.let(::codePointToStringOrNull)

        entity.startsWith("#") ->
            entity.drop(1).toIntOrNull()?.let(::codePointToStringOrNull)

        else -> NAMED_HTML_ENTITIES[entity.lowercase()]
    }

private fun codePointToStringOrNull(codePoint: Int): String? =
    runCatching {
        String(Character.toChars(codePoint))
    }.getOrNull()
