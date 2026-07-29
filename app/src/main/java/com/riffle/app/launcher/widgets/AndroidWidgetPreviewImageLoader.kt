package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.WidgetPreviewImageLoader
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

class AndroidWidgetPreviewImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetPreviewImageLoader {
    private val previewCache = WidgetPreviewCache<ImageBitmap>()

    override suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        previewCache[identity]?.let { return it }
        val preview =
            try {
                loadPreview(identity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                null
            }
        preview?.let { previewCache[identity] = it }
        return preview
    }

    override fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        return previewCache[identity]
    }

    private suspend fun loadPreview(identity: WidgetProviderIdentity): ImageBitmap? {
        val provider =
            withContext(Dispatchers.Default) {
                appWidgetManager.installedProviders.firstOrNull { candidate -> candidate.matches(identity) }
            } ?: return null

        return loadGeneratedPreview(provider)
            ?: withContext(Dispatchers.Default) {
                provider
                    .loadPreviewImage(context, WIDGET_PREVIEW_DENSITY)
                    ?.toWidgetPreviewBitmap()
            }
    }

    private suspend fun loadGeneratedPreview(provider: AppWidgetProviderInfo): ImageBitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            loadAndroid15GeneratedPreview(provider)
        } else {
            null
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun loadAndroid15GeneratedPreview(provider: AppWidgetProviderInfo): ImageBitmap? =
        if (canLoadGeneratedWidgetPreview(Build.VERSION.SDK_INT, provider.generatedPreviewCategories)) {
            provider.profile?.let { profile ->
                previewCallOrNull {
                    withContext(Dispatchers.Default) {
                        appWidgetManager.getWidgetPreview(
                            provider.provider,
                            profile,
                            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        )
                    }
                }?.let { remoteViews ->
                    previewCallOrNull {
                        withContext(Dispatchers.Main.immediate) {
                            remoteViews.toWidgetPreviewBitmap(context, provider)
                        }
                    }
                }
            }
        } else {
            null
        }
}

private suspend fun <T> previewCallOrNull(block: suspend () -> T?): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
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

private fun RemoteViews.toWidgetPreviewBitmap(
    context: Context,
    provider: AppWidgetProviderInfo,
): ImageBitmap {
    val size =
        widgetPreviewBitmapSize(
            intrinsicWidth = provider.minWidth,
            intrinsicHeight = provider.minHeight,
        )
    val parent = FrameLayout(context)
    val previewView = apply(parent.context, parent)
    parent.addView(previewView)
    previewView.measure(
        View.MeasureSpec.makeMeasureSpec(size.width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(size.height, View.MeasureSpec.EXACTLY),
    )
    previewView.layout(0, 0, size.width, size.height)
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    previewView.draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

internal fun canLoadGeneratedWidgetPreview(
    sdkInt: Int,
    generatedPreviewCategories: Int,
): Boolean =
    sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0

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
