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

class AndroidWidgetPreviewImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetPreviewImageLoader {
    private val previews = ConcurrentHashMap<WidgetProviderIdentity, ImageBitmap>()

    override fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? =
        previews[identity] ?: loadPreview(identity)?.also { preview -> previews[identity] = preview }

    override fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? = previews[identity]

    private fun loadPreview(identity: WidgetProviderIdentity): ImageBitmap? =
        appWidgetManager.installedProviders
            .firstOrNull { provider -> provider.matches(identity) }
            ?.let { provider ->
                runCatching {
                    provider
                        .loadPreviewImage(context, WIDGET_PREVIEW_DENSITY)
                        ?.toWidgetPreviewBitmap()
                }.getOrNull()
            }
}

private fun AppWidgetProviderInfo.matches(identity: WidgetProviderIdentity): Boolean =
    provider.packageName == identity.packageName.value &&
        provider.className == identity.className.value &&
        (profile?.toAppProfile() ?: AppProfile.personal()) == identity.profile

private fun Drawable.toWidgetPreviewBitmap(): ImageBitmap {
    val width = intrinsicWidth.takeIf { value -> value > 0 } ?: WIDGET_PREVIEW_BITMAP_WIDTH
    val height = intrinsicHeight.takeIf { value -> value > 0 } ?: WIDGET_PREVIEW_BITMAP_HEIGHT

    return toBitmap(
        width = width.coerceAtMost(WIDGET_PREVIEW_BITMAP_WIDTH),
        height = height.coerceAtMost(WIDGET_PREVIEW_BITMAP_HEIGHT),
    ).asImageBitmap()
}

private const val WIDGET_PREVIEW_DENSITY = 0
private const val WIDGET_PREVIEW_BITMAP_WIDTH = 320
private const val WIDGET_PREVIEW_BITMAP_HEIGHT = 180
