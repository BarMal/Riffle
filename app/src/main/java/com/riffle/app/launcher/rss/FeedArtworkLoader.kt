package com.riffle.app.launcher.rss

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.app.launcher.AdaptiveStageArtworkCache
import com.riffle.app.launcher.adaptiveStageArtworkSampleSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy, local-only artwork loading for feed article cards.
 *
 * Implementations must only ever decode bytes already present in the offline
 * [FeedArticleCacheRepository] cache -- never fetch a live network image. Live network image
 * loading is explicitly out of scope for this renderer (see ADR 0001 and issue #1012); it would
 * need a separate, size-bounded network-image mechanism not part of the #1010 persistence-only
 * cache. Missing artwork must degrade gracefully: a card renders fine with no image.
 */
interface FeedArtworkLoader {
    suspend fun artworkFor(digest: String): ImageBitmap?

    fun cachedArtworkFor(digest: String): ImageBitmap? = null
}

/** Swallows unexpected decode failures rather than surfacing them as a crash; never absorbs cancellation. */
internal suspend fun FeedArtworkLoader.artworkForOrNull(digest: String): ImageBitmap? =
    try {
        artworkFor(digest)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }

object EmptyFeedArtworkLoader : FeedArtworkLoader {
    override suspend fun artworkFor(digest: String): ImageBitmap? = null
}

/**
 * Backed by [FeedArticleCacheRepository.cachedImage]. Decoded bitmaps are kept in a small
 * process-only LRU ([AdaptiveStageArtworkCache], shared with `AdaptiveStageCardSurface.kt`) keyed by the
 * article digest, so repeated composition does not repeatedly decode the same bytes. Decoding
 * reuses the same bounded, downsampled approach as `decodeAdaptiveStageArtwork` -- see
 * [decodeFeedArtworkBytes] -- just starting from raw cached bytes instead of a base64 payload.
 */
class RepositoryFeedArtworkLoader(
    private val repository: FeedArticleCacheRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FeedArtworkLoader {
    private val cache =
        AdaptiveStageArtworkCache<ImageBitmap>(
            decode = { digest -> digest?.let { key -> decodeFeedArtworkBytes(repository.cachedImage(key)) } },
        )

    override suspend fun artworkFor(digest: String): ImageBitmap? =
        withContext(dispatcher) { cache.getOrDecode(sourceKey = digest, artwork = digest) }
}

/**
 * Bounds and downsamples cached artwork bytes into a Compose [ImageBitmap], mirroring
 * `decodeAdaptiveStageArtwork`'s size caps so a malformed or oversized cached image can never block
 * or crash the renderer.
 */
@Suppress("ReturnCount")
internal fun decodeFeedArtworkBytes(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty() || bytes.size > MAX_CACHED_IMAGE_BYTES) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = adaptiveStageArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            },
        )?.asImageBitmap()
    }.getOrNull()
}
