package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.BoundedCache
import com.riffle.app.launcher.ByteBoundedCache
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.IconPackStore
import com.riffle.app.launcher.icons.PackageManagerIconPackSource
import com.riffle.app.launcher.icons.ThemedIconColors
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The loader every Compose surface draws app icons with. It keeps what the one icon pipeline ([AppIconPipeline])
 * resolved, in a cache bounded by bytes whose key holds the app, the size and the style version: a change of style
 * makes the old entries unreachable and they age out, nothing is cleared at once, and an icon already on screen
 * stays until its replacement is ready.
 */
@Suppress("TooManyFunctions")
class PackageManagerAppIconLoader(
    packageManager: PackageManager,
    activityIconFor: (AppIdentity) -> Drawable? = { identity ->
        packageManager.getActivityIcon(ComponentName(identity.packageName.value, identity.activityName.value))
    },
    private val displayDensityFor: (AppIdentity) -> Float = { identity ->
        packageManager
            .getResourcesForApplication(identity.packageName.value)
            .displayMetrics
            .density
    },
    private val pipeline: AppIconPipeline =
        AppIconPipeline(
            packageManager = packageManager,
            packs = IconPackStore(PackageManagerIconPackSource(packageManager) { null }),
            activityIconFor = activityIconFor,
        ),
) : AppIconLoader {
    private data class IconKey(
        val identity: AppIdentity,
        val sizePx: Int,
        val version: Int,
    )

    private val icons =
        ByteBoundedCache<IconKey, ImageBitmap>(
            MAX_CACHED_ICON_BYTES,
        ) { bitmap -> bitmap.width * bitmap.height * BYTES_PER_PIXEL }

    // The icon's dominant color does not depend on the style (an app's colour is its brand's, not the theme's): one
    // entry per app, wrapped in [DominantColorEntry] so a legitimately absent color (icon has no sufficiently
    // saturated pixels) is cached as a present "null" entry rather than being indistinguishable from "not yet loaded".
    private val colors = BoundedCache<AppIdentity, DominantColorEntry>(MAX_CACHED_COLORS)
    private val sizes = ConcurrentHashMap<String, Int>()

    /** The bytes of icon bitmaps held now: always at most [MAX_CACHED_ICON_BYTES] (see the cache test). */
    internal val cachedIconBytes: Int get() = icons.bytes

    override val styleVersion: Int get() = pipeline.styleVersion

    override fun iconVersionFor(identity: AppIdentity): Int = pipeline.versionFor(identity)

    override fun applyStyle(
        style: IconStyleSettings,
        colors: ThemedIconColors,
    ) = pipeline.applyStyle(style, colors)

    override fun onCatalogChanged() = pipeline.onCatalogChanged()

    override fun drawsOwnShape(identity: AppIdentity): Boolean = pipeline.drawsOwnShape(identity)

    override fun iconFor(identity: AppIdentity): ImageBitmap? = cachedIconFor(identity) ?: loadIcon(identity)

    override fun cachedIconFor(identity: AppIdentity): ImageBitmap? = icons[keyFor(identity)]

    override fun colorFor(identity: AppIdentity): Color? =
        colors[identity]?.color
            ?: loadColor(identity).color

    override fun cachedColorFor(identity: AppIdentity): Color? = colors[identity]?.color

    override fun preloadIcons(identities: List<AppIdentity>) {
        identities.forEach { identity -> iconFor(identity) }
    }

    private fun keyFor(identity: AppIdentity) = IconKey(identity, sizeFor(identity), pipeline.versionFor(identity))

    private fun loadIcon(identity: AppIdentity): ImageBitmap? {
        val key = keyFor(identity)
        return runCatching { pipeline.forActivity(identity, key.sizePx)?.asImageBitmap() }.getOrNull()?.also { icon ->
            icons[key] = icon
            if (colors[identity] == null) {
                // With the default style the icon is the platform's own: its color is read from it, not drawn twice.
                colors[identity] = DominantColorEntry(runCatching { dominantColorOf(icon) }.getOrNull())
            }
        }
    }

    private fun loadColor(identity: AppIdentity): DominantColorEntry {
        val sizePx = sizeFor(identity)
        val entry =
            DominantColorEntry(
                runCatching { pipeline.platformBitmap(identity, sizePx)?.asImageBitmap()?.let(::dominantColorOf) }
                    .getOrNull(),
            )
        colors[identity] = entry
        return entry
    }

    // One size per package: the density is the display's, so it is asked once, not on every lookup.
    private fun sizeFor(identity: AppIdentity): Int =
        sizes.getOrPut(identity.packageName.value) {
            runCatching { displayDensityFor(identity) }
                .getOrDefault(DEFAULT_DISPLAY_DENSITY)
                .let(::launcherIconBitmapSizePx)
        }
}

/** Cache payload wrapper so a computed-but-absent dominant color is distinguishable from a cache miss. */
private data class DominantColorEntry(val color: Color?)

internal fun launcherIconBitmapSizePx(displayDensity: Float): Int =
    (MAX_LAUNCHER_ICON_SIZE_DP * displayDensity)
        .roundToInt()
        .coerceIn(MIN_LAUNCHER_ICON_BITMAP_SIZE_PX, MAX_LAUNCHER_ICON_BITMAP_SIZE_PX)

private const val MAX_LAUNCHER_ICON_SIZE_DP = 80
private const val DEFAULT_DISPLAY_DENSITY = 1f
private const val MIN_LAUNCHER_ICON_BITMAP_SIZE_PX = 96
private const val MAX_LAUNCHER_ICON_BITMAP_SIZE_PX = 320
private const val BYTES_PER_PIXEL = 4

// The cache held at most 48 icons of at most 320 px (about 19.6 MB); the bound is now in bytes, at the same ceiling
// for the largest icons and room for more of the usual, smaller ones.
internal const val MAX_CACHED_ICON_BYTES = 24 * 1024 * 1024
private const val MAX_CACHED_COLORS = 512
