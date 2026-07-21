package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.AppIconLoader
import com.riffle.core.domain.launcher.apps.AppIdentity
import kotlin.math.roundToInt

class PackageManagerAppIconLoader(
    private val packageManager: PackageManager,
) : AppIconLoader {
    private val icons = BoundedIconCache<AppIdentity, ImageBitmap>(MAX_CACHED_LAUNCHER_ICONS)

    override fun iconFor(identity: AppIdentity): ImageBitmap? =
        icons[identity] ?: loadIcon(identity)?.also { icon -> icons[identity] = icon }

    override fun cachedIconFor(identity: AppIdentity): ImageBitmap? = icons[identity]

    override fun preloadIcons(identities: List<AppIdentity>) {
        identities.forEach { identity -> iconFor(identity) }
    }

    private fun loadIcon(identity: AppIdentity): ImageBitmap? =
        runCatching {
            packageManager
                .getActivityIcon(identity.componentName)
                .toLauncherImageBitmap(iconBitmapSizePx(identity))
        }.getOrNull()

    private fun iconBitmapSizePx(identity: AppIdentity): Int =
        runCatching {
            packageManager
                .getResourcesForApplication(identity.packageName.value)
                .displayMetrics
                .density
        }.getOrDefault(DEFAULT_DISPLAY_DENSITY).let(::launcherIconBitmapSizePx)
}

private val AppIdentity.componentName: ComponentName
    get() = ComponentName(packageName.value, activityName.value)

private fun Drawable.toLauncherImageBitmap(sizePx: Int): ImageBitmap =
    toBitmap(
        width = sizePx,
        height = sizePx,
    ).asImageBitmap()

internal class BoundedIconCache<Key : Any, Value : Any>(
    private val maxEntries: Int,
) {
    private val entries = LinkedHashMap<Key, Value>(maxEntries, LOAD_FACTOR, true)

    init {
        require(maxEntries > 0)
    }

    @Synchronized
    operator fun get(key: Key): Value? = entries[key]

    @Synchronized
    operator fun set(
        key: Key,
        value: Value,
    ) {
        entries[key] = value
        if (entries.size > maxEntries) {
            entries.remove(entries.entries.iterator().next().key)
        }
    }

    @get:Synchronized
    val size: Int
        get() = entries.size
}

internal fun launcherIconBitmapSizePx(displayDensity: Float): Int =
    (MAX_LAUNCHER_ICON_SIZE_DP * displayDensity)
        .roundToInt()
        .coerceIn(MIN_LAUNCHER_ICON_BITMAP_SIZE_PX, MAX_LAUNCHER_ICON_BITMAP_SIZE_PX)

private const val MAX_LAUNCHER_ICON_SIZE_DP = 80
private const val DEFAULT_DISPLAY_DENSITY = 1f
private const val MIN_LAUNCHER_ICON_BITMAP_SIZE_PX = 96
private const val MAX_LAUNCHER_ICON_BITMAP_SIZE_PX = 320
private const val MAX_CACHED_LAUNCHER_ICONS = 48
private const val LOAD_FACTOR = 0.75f
