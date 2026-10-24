package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement

/**
 * The additive workspace keys of S7: the dock's `"presentation"` (11.2) and `"newApps"` (9.8). Both are optional, so a
 * decoder that predates them ignores them (it draws the shared dock and keeps today's new-app behaviour); the
 * workspace schema version stays 1. Decoding never throws: a bad edge or placement is dropped and an out-of-range icon
 * size is clamped.
 */
internal object WorkspaceExtrasCodec {
    /** Null for an override that overrides nothing, so "follow" is stored as no key. */
    fun encodePresentation(presentation: DockPresentation): StoredValue.Obj? =
        if (presentation.followsShared) {
            null
        } else {
            obj(
                "hidden" to StoredValue.Bool(true).takeIf { presentation.hidden },
                "edge" to presentation.edge?.let { str(it.name) },
                "iconSize" to presentation.iconSizeDp?.let(::num),
            )
        }

    fun decodePresentation(value: StoredValue.Obj?): DockPresentation? {
        val root = value ?: return null
        val presentation =
            DockPresentation(
                hidden = root.bool("hidden") == true,
                edge = enumOrNull<DockPosition>(root.string("edge")),
                iconSizeDp = DockPresentation.clampedIconSize(root.long("iconSize")?.toIntClamped()),
            )
        return presentation.takeUnless { it.followsShared }
    }

    fun encodeNewApps(newApps: WorkspaceNewApps): StoredValue.Obj =
        obj(
            "placement" to str(newApps.placement.name),
            "autoPages" to StoredValue.Bool(newApps.autoPages),
        )

    /** Null (follow the preset) when the key is missing or names no placement. */
    fun decodeNewApps(value: StoredValue.Obj?): WorkspaceNewApps? =
        value?.let { root ->
            enumOrNull<NewAppPlacement>(root.string("placement"))?.let { placement ->
                WorkspaceNewApps(placement, autoPages = root.bool("autoPages") == true)
            }
        }

    private fun Long.toIntClamped(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
