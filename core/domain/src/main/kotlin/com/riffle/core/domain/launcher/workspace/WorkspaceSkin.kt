package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.settings.LauncherThemePreset

/**
 * Per-workspace skin override (Q15, design 8.7): a skin is a [LauncherThemePreset]. [Workspace.skinOverrideId]
 * stores the preset's enum name, a stored contract that is never renamed; null follows the global appearance
 * setting, and an id that names no preset (an older placeholder, a removed preset, hostile data) also follows it,
 * never an error.
 *
 * Only the theme preset is overridden. Theme mode, accent, custom colours, corner style and typography stay global
 * (Settings > Appearance), so a workspace never needs its own copy of them.
 */
object WorkspaceSkin {
    /** The id stored for [preset]. */
    fun idOf(preset: LauncherThemePreset): String = preset.name

    /** The preset [id] names, or null when it names none (follow global). */
    fun presetOf(id: String?): LauncherThemePreset? =
        id?.let { stored -> LauncherThemePreset.entries.firstOrNull { it.name == stored } }

    /** The override [workspace] actually applies, or null when it follows the global preset. */
    fun overrideOf(workspace: Workspace?): LauncherThemePreset? = presetOf(workspace?.skinOverrideId)

    /** Resolution order: the workspace's valid override, else the [global] appearance preset. */
    fun resolve(
        global: LauncherThemePreset,
        workspace: Workspace?,
    ): LauncherThemePreset = overrideOf(workspace) ?: global
}
