package com.riffle.app.launcher.stream

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * The artwork of the active media sessions, kept in memory only (never written to disk, logged or put in
 * diagnostics) and dropped as soon as a session ends. The media source gives an item the handle
 * `media-art:<package>`; resolving it is [artworkFor], off the main thread like every item image.
 */
internal object MediaArtworkStore {
    private val artwork = ConcurrentHashMap<String, Bitmap>()

    fun artworkFor(handleKey: String): Bitmap? = artwork[handleKey.removePrefix(HANDLE_PREFIX)]

    fun replaceAll(byPackage: Map<String, Bitmap>) {
        artwork.keys.retainAll(byPackage.keys)
        artwork.putAll(byPackage)
    }

    fun clear() = artwork.clear()

    const val HANDLE_PREFIX = "media-art:"
}
