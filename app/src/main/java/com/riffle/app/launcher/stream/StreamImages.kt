@file:Suppress("MatchingDeclarationName")

package com.riffle.app.launcher.stream

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.BoundedCache
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.workspace.ItemImageKeyParser
import com.riffle.app.launcher.workspace.ParsedImageKey
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the image handles Stream content carries: launcher and package icons, and the artwork of the active media
 * sessions (`media-art:<package>`, from [MediaArtworkStore], in memory only). Every load runs on [ioDispatcher], never
 * on the main thread. Feed and notification artwork have no resolver yet; they read as null and the card draws without
 * an image.
 */
internal class AndroidStreamImageLoader(
    private val packageManager: PackageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mediaArtwork: (String) -> android.graphics.Bitmap? = MediaArtworkStore::artworkFor,
) : ExpressionImageLoader {
    private val cache = BoundedCache<String, ImageBitmap>(MAX_CACHED_IMAGES)

    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap? {
        val size = sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
        // Artwork changes under one handle (the next track), so it is never cached here.
        if (handle.key.startsWith(MediaArtworkStore.HANDLE_PREFIX)) {
            return withContext(ioDispatcher) { artwork(handle, size) }
        }
        val cacheKey = "${handle.key}@$size"
        return cache[cacheKey] ?: withContext(ioDispatcher) { icon(handle, size) }?.also { cache[cacheKey] = it }
    }

    private fun artwork(
        handle: ItemImageHandle,
        size: Int,
    ): ImageBitmap? =
        runCatching {
            mediaArtwork(handle.key)?.let { bitmap ->
                val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
                if (scale >= 1f) {
                    bitmap
                } else {
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                }.asImageBitmap()
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    private fun icon(
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
                    android.content.ComponentName(key.identity.packageName.value, key.identity.activityName.value),
                )
            is ParsedImageKey.PackageIcon -> packageManager.getApplicationIcon(key.packageName)
            ParsedImageKey.Unsupported -> null
        }

    private companion object {
        const val MAX_CACHED_IMAGES = 96
        const val MIN_SIZE_PX = 24
        const val MAX_SIZE_PX = 1024
    }
}

/** An icon at [size]: the loaded image, or a quiet placeholder while it loads (or if it cannot). Decorative. */
@Composable
internal fun StreamIcon(
    handle: ItemImageHandle?,
    env: StreamEnvironment,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RiffleShapes.small,
) {
    val image = rememberStreamImage(handle, env.imageLoader, size)
    val base = modifier.size(size).clip(shape)
    val loaded = image.value
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = base, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = base.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * Artwork that fills its parent. Draws nothing until it has loaded, and nothing at all when the handle cannot be
 * resolved, so a card never shows an empty image box for an image that will not come.
 */
@Composable
internal fun StreamArtwork(
    handle: ItemImageHandle?,
    env: StreamEnvironment,
    sizeHint: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val image = rememberStreamImage(handle, env.imageLoader, sizeHint)
    val loaded = image.value ?: return
    Image(
        bitmap = loaded,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/** The loaded image for [handle], reloaded when the handle changes (the next track's artwork). Null until loaded. */
@Composable
internal fun rememberStreamImage(
    handle: ItemImageHandle?,
    loader: ExpressionImageLoader,
    size: Dp,
): androidx.compose.runtime.State<ImageBitmap?> {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val state = remember(handle, loader, sizePx) { mutableStateOf<ImageBitmap?>(null) }
    var image by state
    LaunchedEffect(handle, loader, sizePx) {
        image = handle?.let { loader.load(it, sizePx) }
    }
    return state
}

/** A wide lead image of [height]; draws nothing until it has loaded and nothing when it cannot be resolved. */
@Composable
internal fun StreamLeadImage(
    handle: ItemImageHandle?,
    env: StreamEnvironment,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RiffleShapes.medium,
) {
    val image = rememberStreamImage(handle, env.imageLoader, height * 2)
    val loaded = image.value ?: return
    Image(
        bitmap = loaded,
        contentDescription = null,
        modifier = modifier.fillMaxWidth().height(height).clip(shape),
        contentScale = ContentScale.Crop,
    )
}
