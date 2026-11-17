package com.riffle.app.launcher.pair

import androidx.compose.ui.graphics.ImageBitmap
import com.riffle.app.launcher.BoundedCache
import com.riffle.core.domain.launcher.apps.AppIdentity

/** What a fused pair icon depends on: the two apps (in order), the size it is drawn at and the layout direction. */
internal data class PairIconKey(
    val first: AppIdentity,
    val second: AppIdentity,
    val sizePx: Int,
    val rtl: Boolean,
)

/** The fused icons made so far, bounded; the same key always returns the same instance. */
internal class PairIconCache(
    maxEntries: Int = MAX_ENTRIES,
) {
    private val cache = BoundedCache<PairIconKey, ImageBitmap>(maxEntries)

    fun cached(key: PairIconKey): ImageBitmap? = cache[key]

    /** The icon for [key], made with [compose] only when it is not held; callers run this off the main thread. */
    fun getOrCompose(
        key: PairIconKey,
        compose: () -> ImageBitmap,
    ): ImageBitmap = cache[key] ?: compose().also { cache[key] = it }

    companion object {
        const val MAX_ENTRIES = 48

        val shared = PairIconCache()
    }
}
