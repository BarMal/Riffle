package com.riffle.app.launcher

import androidx.compose.ui.graphics.ImageBitmap
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

interface WidgetPreviewImageLoader {
    fun previewFor(identity: WidgetProviderIdentity): ImageBitmap?

    fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? = null
}

object EmptyWidgetPreviewImageLoader : WidgetPreviewImageLoader {
    override fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? = null
}
