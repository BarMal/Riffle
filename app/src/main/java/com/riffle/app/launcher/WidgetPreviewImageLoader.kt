package com.riffle.app.launcher

import androidx.compose.ui.graphics.ImageBitmap
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CancellationException

interface WidgetPreviewImageLoader {
    suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap?

    fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? = null
}

internal fun WidgetPreviewImageLoader.cachedPreviewForOrNull(identity: WidgetProviderIdentity): ImageBitmap? =
    runCatching { cachedPreviewFor(identity) }.getOrNull()

internal suspend fun WidgetPreviewImageLoader.previewForOrNull(identity: WidgetProviderIdentity): ImageBitmap? =
    try {
        previewFor(identity)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }

object EmptyWidgetPreviewImageLoader : WidgetPreviewImageLoader {
    override suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? = null
}
