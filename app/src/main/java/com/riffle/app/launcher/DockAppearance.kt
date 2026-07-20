package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect

internal data class DockAppearance(
    val elevationDp: Int,
    val outlineWidthDp: Int,
)

internal fun dockAppearanceSpec(effect: DockVisualEffect): DockAppearance =
    when (effect) {
        DockVisualEffect.FLAT -> DockAppearance(elevationDp = 0, outlineWidthDp = 0)
        DockVisualEffect.ELEVATED -> DockAppearance(elevationDp = 6, outlineWidthDp = 0)
        DockVisualEffect.OUTLINED -> DockAppearance(elevationDp = 0, outlineWidthDp = 1)
    }

@Composable
internal fun dockSurfaceColor(dock: DockModel): Color {
    val baseColor =
        if (dock.visualEffect == DockVisualEffect.ELEVATED) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val selectedColor = LocalLauncherThemeColorOverrides.current.dock ?: baseColor
    return selectedColor.copy(alpha = selectedColor.alpha * dock.backgroundAlphaPercent / 100f)
}

@Composable
internal fun Modifier.dockSurfaceAppearance(dock: DockModel): Modifier {
    val shape = LocalLauncherPanelShape.current
    val spec = dockAppearanceSpec(dock.visualEffect)
    var result = this
    if (spec.elevationDp > 0) result = result.shadow(spec.elevationDp.dp, shape)
    result = result.clip(shape).background(dockSurfaceColor(dock))
    if (spec.outlineWidthDp > 0) {
        result = result.border(spec.outlineWidthDp.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    return result
}
