package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.Workspace

/**
 * How much room a layout has. Each layout holds its own workspaces (no live link), so a preset carries
 * one workspace per posture. Mirrors the device classes: phone shapes are compact; the unfolded
 * foldable, tablet and desktop are expanded.
 */
enum class PresetPosture {
    COMPACT,
    EXPANDED,
    ;

    companion object {
        fun of(deviceClass: HomeLayoutDeviceClass): PresetPosture =
            when (deviceClass) {
                HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.PHONE_LANDSCAPE -> COMPACT
                HomeLayoutDeviceClass.FOLDABLE, HomeLayoutDeviceClass.TABLET, HomeLayoutDeviceClass.DESKTOP -> EXPANDED
            }
    }
}

/**
 * A named starting point: plain data describing two ordinary [Workspace]s (lenses and containers only,
 * no item content). Nothing about a preset survives installation: [WorkspacePresets.installPreset]
 * hands back a deep copy with fresh ids, which the user then edits like any workspace.
 *
 * @property id stable catalog id.
 * @property skinHintId opaque id of the skin this preset looks best with; a suggestion for the UI,
 * never applied to the installed workspace (its skin override stays null and follows the global skin).
 */
data class WorkspacePreset(
    val id: String,
    val name: String,
    val description: String,
    val compact: Workspace,
    val expanded: Workspace,
    val skinHintId: String? = null,
) {
    fun variant(posture: PresetPosture): Workspace =
        when (posture) {
            PresetPosture.COMPACT -> compact
            PresetPosture.EXPANDED -> expanded
        }
}
