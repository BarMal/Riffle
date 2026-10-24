package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode

/**
 * The layout a Workspaces value is stored under for this device class (owner decision N10): a phone in landscape
 * is the same layout as the phone in portrait, so rotating only changes geometry, never which workspaces, saved
 * lenses, hidden items or placed items are shown. Every other class maps to itself. The classic `HomeLayoutSet`
 * keeps its own landscape layouts and does not use this.
 */
fun HomeLayoutDeviceClass.workspaceLayoutClass(): HomeLayoutDeviceClass =
    when (this) {
        HomeLayoutDeviceClass.PHONE_LANDSCAPE -> HomeLayoutDeviceClass.PHONE
        HomeLayoutDeviceClass.PHONE,
        HomeLayoutDeviceClass.FOLDABLE,
        HomeLayoutDeviceClass.TABLET,
        HomeLayoutDeviceClass.DESKTOP,
        -> this
    }

/**
 * The mode this [HomeLayoutSet] shows on the Workspaces layout of its active device class: the active mode, or in
 * landscape the mode the phone layout last showed (so a rotation never changes which migrated arrangement a preset
 * workspace borrows). Falls back to the active mode.
 */
fun HomeLayoutSet.workspaceShownMode(): LauncherViewMode {
    val layout = activeKey.deviceClass.workspaceLayoutClass()
    return if (layout == activeKey.deviceClass) {
        activeKey.viewMode
    } else {
        preferredModesByDeviceClass[layout] ?: activeKey.viewMode
    }
}

/** The layouts Workspaces values exist for: every device class that [workspaceLayoutClass] maps to itself. */
object WorkspaceLayoutClasses {
    val entries: List<HomeLayoutDeviceClass> =
        HomeLayoutDeviceClass.entries.filter { it.workspaceLayoutClass() == it }

    /** [classes] mapped through [workspaceLayoutClass], without repeats, in [HomeLayoutDeviceClass] order. */
    fun of(classes: Collection<HomeLayoutDeviceClass>): Set<HomeLayoutDeviceClass> =
        classes.map { it.workspaceLayoutClass() }.sortedBy { it.ordinal }.toCollection(LinkedHashSet())
}

/**
 * Folds stored per-layout Workspaces data into the [workspaceLayoutClass] keys, once on load (N10). Pure and
 * idempotent; never throws. When both a `PHONE` and a `PHONE_LANDSCAPE` value exist, `PHONE`'s wins by default.
 */
object WorkspaceLayoutFold {
    /** True when [keys] hold a class that is not its own [workspaceLayoutClass] (so folding would change something). */
    fun needsFold(keys: Collection<HomeLayoutDeviceClass>): Boolean = keys.any { it.workspaceLayoutClass() != it }

    /**
     * [values] re-keyed by [workspaceLayoutClass]. A folded key's value is used only when its target has none,
     * else [merge] decides (default: the target's own value wins).
     */
    fun <V> foldMap(
        values: Map<HomeLayoutDeviceClass, V>,
        merge: (own: V, folded: V) -> V = { own, _ -> own },
    ): Map<HomeLayoutDeviceClass, V> {
        if (!needsFold(values.keys)) return values
        val result = LinkedHashMap<HomeLayoutDeviceClass, V>()
        values.entries.filter { it.key.workspaceLayoutClass() == it.key }.forEach { result[it.key] = it.value }
        values.entries.filter { it.key.workspaceLayoutClass() != it.key }.forEach { (key, value) ->
            val target = key.workspaceLayoutClass()
            result[target] = if (target in result) merge(result.getValue(target), value) else value
        }
        return result.entries.sortedBy { it.key.ordinal }.associate { it.key to it.value }
    }

    /** [set] with its landscape layout folded into the phone layout (the phone layout wins when both exist). */
    fun fold(set: WorkspaceSet): WorkspaceSet =
        if (needsFold(set.layouts.keys)) WorkspaceSet(foldMap(set.layouts)) else set
}
