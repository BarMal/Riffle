package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.BoundedCache
import com.riffle.core.domain.launcher.apps.AppIdentity
import kotlin.math.roundToInt

class PackageManagerAppIconLoader(
    private val packageManager: PackageManager,
    private val activityIconFor: (AppIdentity) -> Drawable? = { identity ->
        packageManager.getActivityIcon(identity.componentName)
    },
    private val displayDensityFor: (AppIdentity) -> Float = { identity ->
        packageManager
            .getResourcesForApplication(identity.packageName.value)
            .displayMetrics
            .density
    },
) : AppIconLoader {
    private val icons = BoundedCache<AppIdentity, ImageBitmap>(MAX_CACHED_LAUNCHER_ICONS)

    // Parallel cache, same key set and eviction bound as [icons]: populated alongside the icon by
    // [loadIcon] so the icon bitmap is only ever decoded once. Wrapped in [DominantColorEntry] so a
    // legitimately absent color (icon has no sufficiently saturated pixels) is cached as a present
    // "null" entry rather than being indistinguishable from "not yet loaded".
    private val colors = BoundedCache<AppIdentity, DominantColorEntry>(MAX_CACHED_LAUNCHER_ICONS)

    override fun iconFor(identity: AppIdentity): ImageBitmap? = icons[identity] ?: loadIcon(identity)

    override fun cachedIconFor(identity: AppIdentity): ImageBitmap? = icons[identity]

    override fun colorFor(identity: AppIdentity): Color? =
        colors[identity]?.color
            ?: loadIcon(identity).let { colors[identity]?.color }

    override fun cachedColorFor(identity: AppIdentity): Color? = colors[identity]?.color

    override fun preloadIcons(identities: List<AppIdentity>) {
        identities.forEach { identity -> iconFor(identity) }
    }

    private fun loadIcon(identity: AppIdentity): ImageBitmap? =
        runCatching {
            activityIconFor(identity)?.toLauncherImageBitmap(iconBitmapSizePx(identity))
        }.getOrNull()?.also { icon ->
            icons[identity] = icon
            colors[identity] = DominantColorEntry(runCatching { dominantColorOf(icon) }.getOrNull())
        }

    private fun iconBitmapSizePx(identity: AppIdentity): Int =
        runCatching { displayDensityFor(identity) }
            .getOrDefault(DEFAULT_DISPLAY_DENSITY)
            .let(::launcherIconBitmapSizePx)
}

/** Cache payload wrapper so a computed-but-absent dominant color is distinguishable from a cache miss. */
private data class DominantColorEntry(val color: Color?)

private val AppIdentity.componentName: ComponentName
    get() = ComponentName(packageName.value, activityName.value)

private fun Drawable.toLauncherImageBitmap(sizePx: Int): ImageBitmap =
    toBitmap(
        width = sizePx,
        height = sizePx,
    ).asImageBitmap()

internal fun launcherIconBitmapSizePx(displayDensity: Float): Int =
    (MAX_LAUNCHER_ICON_SIZE_DP * displayDensity)
        .roundToInt()
        .coerceIn(MIN_LAUNCHER_ICON_BITMAP_SIZE_PX, MAX_LAUNCHER_ICON_BITMAP_SIZE_PX)

private const val MAX_LAUNCHER_ICON_SIZE_DP = 80
private const val DEFAULT_DISPLAY_DENSITY = 1f
private const val MIN_LAUNCHER_ICON_BITMAP_SIZE_PX = 96
private const val MAX_LAUNCHER_ICON_BITMAP_SIZE_PX = 320
private const val MAX_CACHED_LAUNCHER_ICONS = 48
