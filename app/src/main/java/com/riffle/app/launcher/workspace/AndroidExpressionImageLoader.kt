package com.riffle.app.launcher.workspace

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.BoundedCache
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves app, shortcut and notification-package icons from the `ItemImageKeys` scheme. Every lookup that
 * is not already cached hops to [ioDispatcher] before touching the package manager or decoding, so nothing
 * here ever decodes on the main thread. Decoded bitmaps are cached (bounded, keyed by handle and size).
 * Artwork keys (feed articles, notification large icons) are not resolved yet and read as null, so the
 * expression draws its placeholder.
 */
internal class AndroidExpressionImageLoader(
    private val packageManager: PackageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExpressionImageLoader {
    private val cache = BoundedCache<String, ImageBitmap>(MAX_CACHED_IMAGES)

    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap? {
        val size = sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
        val cacheKey = "${handle.key}@$size"
        return cache[cacheKey]
            ?: withContext(ioDispatcher) { decode(handle, size) }?.also { cache[cacheKey] = it }
    }

    private fun decode(
        handle: ItemImageHandle,
        size: Int,
    ): ImageBitmap? =
        runCatching { drawableFor(ItemImageKeyParser.parse(handle))?.toBitmap(size, size)?.asImageBitmap() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    private fun drawableFor(key: ParsedImageKey): Drawable? =
        when (key) {
            is ParsedImageKey.ActivityIcon ->
                packageManager.getActivityIcon(
                    ComponentName(key.identity.packageName.value, key.identity.activityName.value),
                )
            is ParsedImageKey.PackageIcon -> packageManager.getApplicationIcon(key.packageName)
            ParsedImageKey.Unsupported -> null
        }

    private companion object {
        const val MAX_CACHED_IMAGES = 96
        const val MIN_SIZE_PX = 24
        const val MAX_SIZE_PX = 512
    }
}
