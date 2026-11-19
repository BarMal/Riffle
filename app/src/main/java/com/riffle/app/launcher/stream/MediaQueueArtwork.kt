package com.riffle.app.launcher.stream

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDescription
import android.net.Uri

/**
 * The thumbnail of a queue entry, from what its app put in the `MediaDescription`: the bitmap itself, else the image a
 * `content:` or `android.resource:` URI names, read through the content resolver (never the network: the app's own
 * provider or nothing) and scaled down to [MAX_EDGE_PX]. Read off the main thread, with the media read. Results are
 * kept in a small memory cache (misses too, so an image that cannot be read is not tried again at every queue
 * callback); nothing is written to disk or logged.
 */
internal class MediaQueueArtwork(private val resolver: ContentResolver) {
    private val cache =
        object : LinkedHashMap<String, Bitmap?>(CACHE_SIZE, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) = size > CACHE_SIZE
        }

    /** Whether [description] names any image at all (cheap: nothing is read). */
    fun has(description: MediaDescription): Boolean =
        description.iconBitmap != null || description.iconUri?.scheme in READABLE_SCHEMES

    fun bitmapOf(
        packageName: String,
        description: MediaDescription,
    ): Bitmap? {
        val uri = description.iconUri?.takeIf { it.scheme in READABLE_SCHEMES }
        return description.iconBitmap ?: uri?.let { cachedDecode("$packageName|$it", it) }
    }

    private fun cachedDecode(
        key: String,
        uri: Uri,
    ): Bitmap? {
        val known = synchronized(cache) { if (cache.containsKey(key)) Known(cache[key]) else null }
        if (known != null) return known.bitmap
        val loaded = runCatching { decode(uri) }.getOrNull()
        synchronized(cache) { cache[key] = loaded }
        return loaded
    }

    private class Known(val bitmap: Bitmap?)

    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        val READABLE_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE)
        const val CACHE_SIZE = 32
        const val LOAD_FACTOR = 0.75f
        const val MAX_EDGE_PX = 256
    }
}
