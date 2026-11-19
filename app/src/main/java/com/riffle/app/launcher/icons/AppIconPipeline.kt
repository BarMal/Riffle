package com.riffle.app.launcher.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconPrecedence
import com.riffle.core.domain.launcher.icons.IconRecipe
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the places that draw app icons outside the loader (Stream cards, the Floating dock, widget stacks) ask for. */
interface AppIconBitmaps {
    /** Changes whenever the icon style does; part of every cache key and read in composition so icons redraw. */
    val styleVersion: Int

    fun forActivity(
        identity: AppIdentity,
        sizePx: Int,
    ): Bitmap?

    fun forPackage(
        packageName: String,
        sizePx: Int,
    ): Bitmap?
}

/** Told when an icon is resolved on the main thread, where it must never be. */
fun interface MainThreadGuard {
    fun resolvedOnMainThread()

    companion object {
        /** Logs the call: a warning in the field, never a crash. */
        val Warn: MainThreadGuard =
            MainThreadGuard { Log.w("AppIconPipeline", "An icon was resolved on the main thread") }
    }
}

/**
 * The one icon resolver (ADR 0003, Amendment 19). Every place that draws an app icon asks it, with the app, a size and
 * (implicitly) the current style: Home, the Dock, folders, the Library, search, Stream cards, pair cards, the widget
 * stack editor, the Floating dock and Settings. It resolves by [IconPrecedence]: a per-app override, then the icon
 * pack, then themed icons, then the shape, and draws the first recipe that works. With the default style the result
 * is the platform's own icon, drawn as before.
 *
 * Resolution is blocking and decodes drawables: it runs off the main thread (see [MainThreadGuard]). [styleVersion]
 * and [versionFor] are what caches key on, so a change of style invalidates them without anything being cleared.
 */
@Suppress("TooManyFunctions", "LongParameterList", "ReturnCount")
class AppIconPipeline internal constructor(
    private val packageManager: PackageManager,
    internal val packs: IconPackStore,
    private val activityIconFor: (AppIdentity) -> Drawable? = { identity ->
        packageManager.getActivityIcon(ComponentName(identity.packageName.value, identity.activityName.value))
    },
    private val packageIconFor: (
        String,
    ) -> Drawable? = { packageName -> packageManager.getApplicationIcon(packageName) },
    private val launchActivityFor: (String) -> AppIdentity? = { packageName ->
        packageManager.getLaunchIntentForPackage(packageName)?.component?.let { component ->
            AppIdentity(AppPackageName(component.packageName), AppActivityName(component.className))
        }
    },
    private val guard: MainThreadGuard = MainThreadGuard.Warn,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val isMainThread: () -> Boolean = { Looper.getMainLooper().thread === Thread.currentThread() },
) : AppIconBitmaps {
    private var requested by mutableStateOf(IconStyleSettings())
    private var colors by mutableStateOf(DEFAULT_COLORS)

    /** The style as drawn: themed icons need a platform with monochrome layers, and "all" only applies to themed. */
    val effectiveStyle: IconStyleSettings
        get() {
            val themed = requested.themedIcons && sdkInt >= Build.VERSION_CODES.TIRAMISU
            return requested.copy(themedIcons = themed, themeAllIcons = themed && requested.themeAllIcons)
        }

    private val globalVersion by derivedStateOf {
        val style = effectiveStyle
        var version = style.styleVersion(packVersion = 31L * packs.versionOf(style.iconPack) + packs.catalogVersion)
        if (style.themedIcons) version = 31 * version + colors.hashCode()
        version
    }

    override val styleVersion: Int get() = globalVersion

    /** The cache version of one app's icon: the global style plus that app's own override. */
    fun versionFor(identity: AppIdentity): Int = 31 * globalVersion + requested.overrideVersion(identity)

    /** Whether [identity]'s icon is drawn cut to its own shape: a chosen shape, a pack or a picked icon. */
    fun drawsOwnShape(identity: AppIdentity): Boolean {
        val style = effectiveStyle
        return style.shape != IconShape.SYSTEM || style.iconPack != null || style.overrideFor(identity)?.icon != null
    }

    /** The style in effect, for the Settings preview and the picker. */
    val style: IconStyleSettings get() = requested

    /**
     * Applies [style] and the theme's [colors] (null keeps the last). Cheap: the packs it names are looked at (and the
     * chosen one read) on a background thread, and everything that drew with the old state redraws when the version
     * changes.
     */
    fun applyStyle(
        style: IconStyleSettings,
        colors: ThemedIconColors?,
    ) {
        val newColors = colors ?: this.colors
        if (requested == style && this.colors == newColors) return
        val packsChanged = requested.iconPack != style.iconPack || requested.overrides != style.overrides
        requested = style
        this.colors = newColors
        if (packsChanged || style.iconPack != null) watchPacks(style)
    }

    /** A package was installed, updated or removed (the broadcast the launcher already receives). */
    fun onCatalogChanged() {
        scope.launch { packs.onCatalogChanged(requested.iconPack) }
    }

    private fun watchPacks(style: IconStyleSettings) {
        val named = style.overrides.values.mapNotNull { it.icon?.packPackage } + listOfNotNull(style.iconPack)
        scope.launch { packs.watch(named.toSet(), style.iconPack) }
    }

    override fun forActivity(
        identity: AppIdentity,
        sizePx: Int,
    ): Bitmap? {
        if (isMainThread()) guard.resolvedOnMainThread()
        val style = effectiveStyle
        val platform = lazy { runCatching { activityIconFor(identity) }.getOrNull() }
        for (recipe in IconPrecedence.candidates(style, identity, packs)) {
            val bitmap = runCatching { render(recipe, identity, platform, sizePx) }.getOrNull()
            if (bitmap != null) return bitmap
        }
        return null
    }

    override fun forPackage(
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        if (isMainThread()) guard.resolvedOnMainThread()
        val style = effectiveStyle
        // A pack or a per-app choice is looked up by the app's launcher activity; otherwise the application's own icon
        // is drawn, through the shape and themed choices, as it always was.
        if (style.iconPack != null || style.overrides.values.any { it.icon != null }) {
            launchActivityFor(packageName)?.let { identity ->
                forActivity(identity, sizePx)?.let { return it }
            }
        }
        val drawable = runCatching { packageIconFor(packageName) }.getOrNull() ?: return null
        return runCatching { renderStyled(drawable, sizePx, style) }.getOrNull()
    }

    /** The platform's own icon, unstyled: what an app's colour is taken from, whatever the style. */
    fun platformBitmap(
        identity: AppIdentity,
        sizePx: Int,
    ): Bitmap? {
        if (isMainThread()) guard.resolvedOnMainThread()
        return runCatching { activityIconFor(identity)?.let { IconRenderer.platform(it, sizePx) } }.getOrNull()
    }

    private fun render(
        recipe: IconRecipe,
        identity: AppIdentity,
        platform: Lazy<Drawable?>,
        sizePx: Int,
    ): Bitmap? =
        when (recipe) {
            IconRecipe.Platform -> platform.value?.let { IconRenderer.platform(it, sizePx) }
            is IconRecipe.PackDrawable -> packDrawable(recipe.ref)?.let { IconRenderer.platform(it, sizePx) }
            is IconRecipe.PackComposite -> composite(recipe.packPackage, identity, platform.value, sizePx)
            is IconRecipe.Styled -> platform.value?.let { renderStyled(it, sizePx, effectiveStyle) }
        }

    private fun renderStyled(
        drawable: Drawable,
        sizePx: Int,
        style: IconStyleSettings,
    ): Bitmap =
        if (style.shape == IconShape.SYSTEM && !style.themedIcons) {
            IconRenderer.platform(drawable, sizePx)
        } else {
            IconRenderer.styled(
                drawable,
                sizePx,
                IconLook(style.shape, style.legacyIcons, style.themedIcons, style.themeAllIcons, colors),
            )
        }

    private fun packDrawable(ref: IconPackDrawableRef): Drawable? = packs.drawable(ref.packPackage, ref.drawableName)

    private fun composite(
        pack: String,
        identity: AppIdentity,
        icon: Drawable?,
        sizePx: Int,
    ): Bitmap? {
        val mapping = packs.mappingOf(pack) ?: return null
        icon ?: return null
        val back = mapping.backFor(identity.packageName.value)?.let { packs.drawable(pack, it) }
        val mask = mapping.iconMask?.let { packs.drawable(pack, it) }
        val upon = mapping.iconUpon?.let { packs.drawable(pack, it) }
        return IconRenderer.composite(icon, sizePx, back, mask, upon, mapping.scale)
    }

    companion object {
        private val DEFAULT_COLORS = ThemedIconColors(plateArgb = 0xFF2F3A4A.toInt(), glyphArgb = 0xFFDCE6F5.toInt())

        @Volatile
        private var instance: AppIconPipeline? = null

        /** The one pipeline of the process: the launcher's windows and the Floating dock service draw through it. */
        fun shared(context: Context): AppIconPipeline =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        fun create(context: Context): AppIconPipeline {
            val applicationContext = context.applicationContext
            val source =
                PackageManagerIconPackSource(applicationContext.packageManager) { pack ->
                    runCatching { applicationContext.createPackageContext(pack, 0) }.getOrNull()
                }
            return AppIconPipeline(applicationContext.packageManager, IconPackStore(source))
        }
    }
}
