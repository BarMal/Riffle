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

    override val previewRevision: Long
        get() = previewCache.revision

    override suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        previewCache[identity]?.let { return it }
        val expectedRevision = previewCache.revision
        val preview =
            try {
                loadPreview(identity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                null
            }
        return preview?.takeIf {
            previewCache.putIfRevision(identity, it, expectedRevision)
        }
    }

    override fun cachedPreviewFor(identity: WidgetProviderIdentity): ImageBitmap? {
        return previewCache[identity]
    }

    override fun invalidatePreviews() {
        previewCache.invalidate()
    }

    private suspend fun loadPreview(identity: WidgetProviderIdentity): ImageBitmap? {
        val provider =
            withContext(Dispatchers.Default) {
                appWidgetManager.installedProviders.firstOrNull { candidate -> candidate.matches(identity) }
            } ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            loadAndroid15Preview(provider)
        } else {
            loadLegacyPreview(provider)
        }
    }

    private suspend fun loadLegacyPreview(provider: AppWidgetProviderInfo): ImageBitmap? =
        withContext(Dispatchers.Default) {
            provider
                .loadPreviewImage(context, WIDGET_PREVIEW_DENSITY)
                ?.toWidgetPreviewBitmap()
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun loadAndroid15Preview(provider: AppWidgetProviderInfo): ImageBitmap? {
        val generatedPreviewLoader =
            provider.profile
                ?.takeIf {
                    canLoadGeneratedWidgetPreview(
                        sdkInt = Build.VERSION.SDK_INT,
                        generatedPreviewCategories = provider.generatedPreviewCategories,
                    )
                }?.let { profile ->
                    suspend {
                        withContext(Dispatchers.Default) {
                            appWidgetManager.getWidgetPreview(
                                provider.provider,
                                profile,
                                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                            )
                        }
                    }
                }

        return loadWidgetPreviewWithFallback(
            loadGeneratedPreview = generatedPreviewLoader,
            renderGeneratedPreview = { remoteViews ->
                withContext(Dispatchers.Main.immediate) {
                    renderWidgetPreviewRemoteViews(
                        context = context,
                        remoteViews = remoteViews,
                        intrinsicWidth = provider.minWidth,
                        intrinsicHeight = provider.minHeight,
                    )
                }
            },
            loadLegacyPreview = { loadLegacyPreview(provider) },
        )
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

internal suspend fun loadWidgetPreviewWithFallback(
    loadGeneratedPreview: (suspend () -> RemoteViews?)?,
    renderGeneratedPreview: suspend (RemoteViews) -> ImageBitmap,
    loadLegacyPreview: suspend () -> ImageBitmap?,
): ImageBitmap? {
    val generatedPreview =
        loadGeneratedPreview
            ?.let { loader -> previewCallOrNull { loader() } }
            ?.let { remoteViews -> previewCallOrNull { renderGeneratedPreview(remoteViews) } }
    return generatedPreview ?: previewCallOrNull { loadLegacyPreview() }
}

internal class WidgetPreviewCache<T>(
    private val maxEntries: Int = MAX_PREVIEW_CACHE_ENTRIES,
) {
    private val lock = Any()
    private var currentRevision = 0L
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

    fun putIfRevision(
        identity: WidgetProviderIdentity,
        preview: T,
        expectedRevision: Long,
    ): Boolean =
        synchronized(lock) {
            if (expectedRevision == currentRevision) {
                previews[identity] = preview
                true
            } else {
                false
            }
        }

    fun invalidate() {
        synchronized(lock) {
            previews.clear()
            currentRevision += 1
        }
    }

    val revision: Long
        get() = synchronized(lock) { currentRevision }

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

internal fun renderWidgetPreviewRemoteViews(
    context: Context,
    remoteViews: RemoteViews,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
): ImageBitmap {
    val size =
        widgetPreviewBitmapSize(
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
        )
    val parent = FrameLayout(context)
    val previewView = remoteViews.apply(parent.context, parent)
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
