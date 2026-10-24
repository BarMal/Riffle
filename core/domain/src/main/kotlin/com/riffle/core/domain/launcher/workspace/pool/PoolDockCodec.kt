package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/**
 * The `dock` entry of a pool. Decoding never throws: a missing field takes the `PoolDockSettings` default, an
 * unreadable pin or panel is dropped (`PoolValidation.repair` then removes dangling references).
 */
internal object PoolDockCodec {
    fun encode(dock: PoolDock): StoredValue.Obj =
        obj(
            "pins" to arr(dock.pins.map { str(it.value) }),
            "panel" to dock.panel?.let(PoolCodec::encodePage),
            "settings" to encodeSettings(dock.settings),
        )

    fun decode(root: StoredValue.Obj): PoolDock =
        PoolDock(
            pins =
                root.array("pins").mapNotNull { value ->
                    (value as? StoredValue.Str)?.value?.let { guarded { PoolItemId(it) } }
                },
            panel = root.obj("panel")?.let { guarded { PoolCodec.decodePage(it) } },
            settings = root.obj("settings")?.let(::decodeSettings) ?: PoolDockSettings(),
        )

    private fun encodeSettings(s: PoolDockSettings): StoredValue.Obj =
        obj(
            "capacity" to num(s.capacity),
            "enabled" to StoredValue.Bool(s.isEnabled),
            "cards" to StoredValue.Bool(s.showNotificationCards),
            "cardSlots" to num(s.notificationSlotCount),
            "iconSize" to num(s.iconSizeDp),
            "backgroundAlpha" to num(s.backgroundAlphaPercent),
            "visualEffect" to str(s.visualEffect.name),
            "backgroundSizing" to str(s.backgroundSizing.name),
            "alignment" to str(s.alignment.name),
            "itemSpacing" to num(s.itemSpacingDp),
            "cornerRadius" to num(s.cornerRadiusDp),
            "homeControlsSpacing" to num(s.homeControlsSpacingDp),
            "expandable" to StoredValue.Bool(s.isExpandable),
            "expandAffordance" to str(s.expandAffordance.name),
            "position" to s.position?.let { str(it.name) },
            "libraryPosition" to s.libraryPosition?.let { str(it.name) },
        )

    private fun decodeSettings(root: StoredValue.Obj): PoolDockSettings {
        val d = PoolDockSettings()
        return decodeAppearance(root, d).copy(
            capacity = root.int("capacity") ?: d.capacity,
            isEnabled = root.bool("enabled") ?: d.isEnabled,
            showNotificationCards = root.bool("cards") ?: d.showNotificationCards,
            notificationSlotCount = root.int("cardSlots") ?: d.notificationSlotCount,
            isExpandable = root.bool("expandable") ?: d.isExpandable,
            expandAffordance =
                enumOrNull<DockExpandAffordance>(root.string("expandAffordance")) ?: d.expandAffordance,
            position = enumOrNull<DockPosition>(root.string("position")),
            libraryPosition = enumOrNull<DockPosition>(root.string("libraryPosition")),
        )
    }

    private fun decodeAppearance(
        root: StoredValue.Obj,
        d: PoolDockSettings,
    ): PoolDockSettings =
        d.copy(
            iconSizeDp = root.int("iconSize") ?: d.iconSizeDp,
            backgroundAlphaPercent = root.int("backgroundAlpha") ?: d.backgroundAlphaPercent,
            visualEffect = enumOrNull<DockVisualEffect>(root.string("visualEffect")) ?: d.visualEffect,
            backgroundSizing = enumOrNull<DockBackgroundSizing>(root.string("backgroundSizing")) ?: d.backgroundSizing,
            alignment = enumOrNull<DockAlignment>(root.string("alignment")) ?: d.alignment,
            itemSpacingDp = root.int("itemSpacing") ?: d.itemSpacingDp,
            cornerRadiusDp = root.int("cornerRadius") ?: d.cornerRadiusDp,
            homeControlsSpacingDp = root.int("homeControlsSpacing") ?: d.homeControlsSpacingDp,
        )
}
