package com.riffle.app.launcher.rss

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

internal fun encodeCachedFeedArticle(article: CachedFeedArticle): JSONObject =
    JSONObject()
        .put("digest", article.digest)
        .put("title", article.title)
        .put("author", article.author)
        .put("publishedAtEpochMillis", article.publishedAtEpochMillis)
        .put("summary", article.summary)
        .put("canonicalUrl", article.canonicalUrl)
        .put("imageUrl", article.imageUrl)
        .put("sourceOrder", article.sourceOrder)

internal fun decodeCachedFeedArticle(json: JSONObject): CachedFeedArticle =
    CachedFeedArticle(
        digest = json.getString("digest"),
        title = json.getString("title"),
        author = json.optString("author").takeIf(String::isNotBlank),
        publishedAtEpochMillis = json.optLongOrNull("publishedAtEpochMillis"),
        summary = json.optString("summary").takeIf(String::isNotBlank),
        canonicalUrl = json.optString("canonicalUrl").takeIf(String::isNotBlank),
        imageUrl = json.optString("imageUrl").takeIf(String::isNotBlank),
        sourceOrder = json.getInt("sourceOrder"),
    )

internal fun encodeCachedImage(image: CachedImage): JSONObject =
    JSONObject()
        .put("digest", image.digest)
        .put("bytesBase64", Base64.getEncoder().encodeToString(image.bytes))
        .put("cachedAtEpochMillis", image.cachedAtEpochMillis)

internal fun JSONArray.decodeCachedImages(): List<CachedImage> =
    (0 until length()).mapNotNull { index -> runCatching { decodeCachedImage(getJSONObject(index)) }.getOrNull() }

private fun decodeCachedImage(json: JSONObject): CachedImage =
    CachedImage(
        digest = json.getString("digest"),
        bytes = Base64.getDecoder().decode(json.getString("bytesBase64")),
        cachedAtEpochMillis = json.getLong("cachedAtEpochMillis"),
    )

private fun JSONObject.optLongOrNull(name: String): Long? =
    when {
        !has(name) || isNull(name) -> null
        get(name) is Number -> getLong(name)
        else -> null
    }
