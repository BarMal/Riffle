package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.WidgetPreviewImageLoader
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class AndroidWidgetPreviewImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetPreviewImageLoader {
    private val previews = ConcurrentHashMap<WidgetProviderIdentity, ImageBitmap>()
    private val previewOrder = ConcurrentLinkedDeque<WidgetProviderIdentity>()

    override fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? =
        previews[identity]?.also { touch(identity) }
            ?: runCatching { loadPreview(identity) }.getOrNull()?.also { preview ->
                previews[identity] = preview
                touch(identity)
                trimCache()
            }

    override fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        return previews[identity]?.also { touch(identity) }
    }

    private fun touch(identity: WidgetProviderIdentity) {
        previewOrder.remove(identity)
        previewOrder.addLast(identity)
    }

    private fun trimCache() {
        while (previewOrder.size > MAX_PREVIEW_CACHE_ENTRIES) {
            previewOrder.pollFirst()?.let(previews::remove)
        }
    }

    private fun loadPreview(identity: WidgetProviderIdentity): ImageBitmap? =
        appWidgetManager.installedProviders
            .firstOrNull { provider -> provider.matches(identity) }
            ?.loadPreviewImage(context, WIDGET_PREVIEW_DENSITY)
            ?.toWidgetPreviewBitmap()
}

private fun AppWidgetProviderInfo.matches(identity: WidgetProviderIdentity): Boolean =
    provider.packageName == identity.packageName.value &&
        provider.className == identity.className.value &&
        (profile?.toAppProfile() ?: AppProfile.personal()) == identity.profile

private fun Drawable.toWidgetPreviewBitmap(): ImageBitmap {
    val size = widgetPreviewBitmapSize(intrinsicWidth = intrinsicWidth, intrinsicHeight = intrinsicHeight)

    return toBitmap(
        width = size.width,
        height = size.height,
    ).asImageBitmap()
}

internal data class WidgetPreviewBitmapSize(
    val width: Int,
    val height: Int,
)

internal fun widgetPreviewBitmapSize(
    intrinsicWidth: Int,
    intrinsicHeight: Int,
): WidgetPreviewBitmapSize {
    val width = intrinsicWidth.takeIf { value -> value > 0 } ?: WIDGET_PREVIEW_BITMAP_WIDTH
    val height = intrinsicHeight.takeIf { value -> value > 0 } ?: WIDGET_PREVIEW_BITMAP_HEIGHT
    val scale =
        minOf(
            1f,
            WIDGET_PREVIEW_BITMAP_WIDTH / width.toFloat(),
            WIDGET_PREVIEW_BITMAP_HEIGHT / height.toFloat(),
        )

    return WidgetPreviewBitmapSize(
        width = (width * scale).toInt().coerceAtLeast(1),
        height = (height * scale).toInt().coerceAtLeast(1),
    )
}

private const val WIDGET_PREVIEW_DENSITY = 0
private const val WIDGET_PREVIEW_BITMAP_WIDTH = 320
private const val WIDGET_PREVIEW_BITMAP_HEIGHT = 180
private const val MAX_PREVIEW_CACHE_ENTRIES = 48
