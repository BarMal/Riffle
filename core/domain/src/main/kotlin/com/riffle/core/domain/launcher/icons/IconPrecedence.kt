package com.riffle.core.domain.launcher.icons

import com.riffle.core.domain.launcher.apps.AppIdentity

/** One way to draw an app's icon, tried in order by [IconPrecedence.candidates]. */
sealed interface IconRecipe {
    /** The platform's own icon: today's pixels. */
    data object Platform : IconRecipe

    /** A named drawable of an icon pack ([fromOverride]: the user picked it for this app). */
    data class PackDrawable(
        val ref: IconPackDrawableRef,
        val fromOverride: Boolean,
    ) : IconRecipe

    /** The app has no drawable in the pack: its icon is put on the pack's back plate, masked, with its overlay. */
    data class PackComposite(val packPackage: String) : IconRecipe

    /** The platform's icon through the themed-icon and shape choices. */
    data class Styled(
        val shape: IconShape,
        val legacyIcons: LegacyIconHandling,
        val themed: Boolean,
        val themeAll: Boolean,
    ) : IconRecipe
}

/** What the pure rules need to know about the installed packs. */
interface IconPackIndex {
    /** Whether [packPackage] is installed and was read. */
    fun isAvailable(packPackage: String): Boolean

    /** The drawable name the pack maps [identity] to, null when it has none for it. */
    fun drawableFor(
        packPackage: String,
        identity: AppIdentity,
    ): String?

    /** Whether the pack describes how to dress apps it has no drawable for (a back plate, a mask or an overlay). */
    fun dressesUnthemedApps(packPackage: String): Boolean
}

/** The order of precedence: per-app override, then icon pack, then themed icons, then the shape. */
object IconPrecedence {
    /**
     * The recipes for [identity] in the order to try them. The caller draws the first that works (a drawable can be
     * missing from the installed pack); the last is always one that cannot fail. An override whose pack is not
     * installed is skipped and kept in the settings.
     */
    fun candidates(
        style: IconStyleSettings,
        identity: AppIdentity,
        packs: IconPackIndex,
    ): List<IconRecipe> {
        val recipes = mutableListOf<IconRecipe>()
        style.overrideFor(identity)?.icon?.takeIf { packs.isAvailable(it.packPackage) }?.let { ref ->
            recipes += IconRecipe.PackDrawable(ref, fromOverride = true)
        }
        style.iconPack?.takeIf(packs::isAvailable)?.let { pack ->
            packs.drawableFor(pack, identity)?.let { name ->
                recipes += IconRecipe.PackDrawable(IconPackDrawableRef(pack, name), fromOverride = false)
            }
            if (packs.dressesUnthemedApps(pack)) recipes += IconRecipe.PackComposite(pack)
        }
        recipes +=
            if (style.withoutOverrides().let { it.shape == IconShape.SYSTEM && !it.themedIcons }) {
                IconRecipe.Platform
            } else {
                IconRecipe.Styled(style.shape, style.legacyIcons, style.themedIcons, style.themeAllIcons)
            }
        return recipes
    }
}
