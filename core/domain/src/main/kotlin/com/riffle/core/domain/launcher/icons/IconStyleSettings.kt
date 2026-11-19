package com.riffle.core.domain.launcher.icons

import com.riffle.core.domain.launcher.apps.AppIdentity

/** What happens to an app that ships no adaptive icon when a shape is chosen (Settings > Icons > "Legacy icons"). */
enum class LegacyIconHandling(
    val storedId: String,
) {
    /** The icon is scaled to fit inside the shape and drawn on a neutral plate. */
    PLATE("plate"),

    /** The icon is drawn as it is, unmasked. */
    AS_IS("as_is"),
    ;

    companion object {
        val DEFAULT: LegacyIconHandling = PLATE

        fun decode(stored: String?): LegacyIconHandling = entries.firstOrNull { it.storedId == stored } ?: DEFAULT
    }
}

/** A drawable of an installed icon pack, by name: never a bitmap. The pack is its package name. */
data class IconPackDrawableRef(
    val packPackage: String,
    val drawableName: String,
)

/**
 * What the user changed for one app. [icon] is a drawable of an icon pack (kept even when that pack is not installed,
 * so installing it again restores the choice); [label] is a custom name. Neither set means no override.
 */
data class AppIconOverride(
    val icon: IconPackDrawableRef? = null,
    val label: String? = null,
) {
    val isEmpty: Boolean get() = icon == null && label == null
}

/**
 * Settings > Appearance > Icons. With the defaults, an icon is drawn exactly as the platform draws it ([isDefault]).
 * Order of precedence for one app: its [overrides] entry, then the [iconPack], then the themed icon, then the [shape].
 */
data class IconStyleSettings(
    val shape: IconShape = IconShape.DEFAULT,
    val legacyIcons: LegacyIconHandling = LegacyIconHandling.DEFAULT,
    val themedIcons: Boolean = false,
    /** Apps without a monochrome layer get a generated one (only read while [themedIcons] is on). */
    val themeAllIcons: Boolean = false,
    /** The package name of the chosen icon pack, null for none. Kept while the pack is not installed. */
    val iconPack: String? = null,
    val overrides: Map<AppIdentity, AppIconOverride> = emptyMap(),
) {
    /** Whether the style leaves every icon as the platform draws it. A label rename is not an icon change. */
    val isDefault: Boolean
        get() = withoutOverrides() == IconStyleSettings() && overrides.values.none { it.icon != null }

    /** The settings that apply to every app: the part of the cache key that changes with a setting. */
    fun withoutOverrides(): IconStyleSettings = if (overrides.isEmpty()) this else copy(overrides = emptyMap())

    fun overrideFor(identity: AppIdentity): AppIconOverride? = overrides[identity]

    fun withOverride(
        identity: AppIdentity,
        override: AppIconOverride,
    ): IconStyleSettings =
        copy(overrides = if (override.isEmpty) overrides - identity else overrides + (identity to override))

    fun withIconOverride(
        identity: AppIdentity,
        icon: IconPackDrawableRef?,
    ): IconStyleSettings = withOverride(identity, (overrides[identity] ?: AppIconOverride()).copy(icon = icon))

    fun withLabelOverride(
        identity: AppIdentity,
        label: String?,
    ): IconStyleSettings =
        withOverride(identity, (overrides[identity] ?: AppIconOverride()).copy(label = label?.trim()?.ifEmpty { null }))

    /** "Reset icon changes": every per-app icon choice is cleared; custom names stay. */
    fun withoutIconOverrides(): IconStyleSettings =
        copy(overrides = overrides.mapValues { (_, value) -> value.copy(icon = null) }.filterValues { !it.isEmpty })

    /** The label to show for [identity], [fallback] when it has not been renamed. */
    fun labelFor(
        identity: AppIdentity,
        fallback: String,
    ): String = overrides[identity]?.label ?: fallback

    /** Cache identity of everything that applies to all apps; changes whenever a global choice changes. */
    fun styleVersion(packVersion: Long = 0L): Int = 31 * withoutOverrides().hashCode() + packVersion.hashCode()

    /** What one app's icon additionally depends on: its own override. */
    fun overrideVersion(identity: AppIdentity): Int = overrides[identity]?.icon?.hashCode() ?: 0
}
