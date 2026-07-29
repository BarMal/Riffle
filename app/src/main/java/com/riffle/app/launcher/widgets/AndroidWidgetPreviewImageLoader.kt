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
import java.util.LinkedHashMap

class AndroidWidgetPreviewImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetPreviewImageLoader {
    private val previewCache = WidgetPreviewCache<ImageBitmap>()

    override fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? =
        previewCache[identity]
            ?: runCatching { loadPreview(identity) }.getOrNull()?.also { preview ->
                previewCache[identity] = preview
            }

    override fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        return previewCache[identity]
    }

    private fun loadPreview(identity: WidgetProviderIdentity): ImageBitmap? =
        appWidgetManager.installedProviders
            .firstOrNull { provider -> provider.matches(identity) }
            ?.loadPreviewImage(context, WIDGET_PREVIEW_DENSITY)
            ?.toWidgetPreviewBitmap()
}

internal class WidgetPreviewCache<T>(
    private val maxEntries: Int = MAX_PREVIEW_CACHE_ENTRIES,
) {
    private val lock = Any()
    private val previews =
        object : LinkedHashMap<WidgetProviderIdentity, T>(maxEntries.coerceAtLeast(1), 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WidgetProviderIdentity, T>?): Boolean =
                size > maxEntries.coerceAtLeast(0)
        }

    operator fun get(identity: WidgetProviderIdentity): T? =
        synchronized(lock) {
            previews[identity]
        }

    operator fun set(
        identity: WidgetProviderIdentity,
        preview: T,
    ) {
        synchronized(lock) {
            previews[identity] = preview
        }
    }

    internal val size: Int
        get() = synchronized(lock) { previews.size }
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
