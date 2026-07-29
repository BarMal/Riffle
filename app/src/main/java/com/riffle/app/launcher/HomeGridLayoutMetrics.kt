package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing

class HomeGridLayoutMetrics {
    internal fun gridSizePx(
        grid: GridDimensions,
        maxWidthPx: Float,
        maxHeightPx: Float,
    ): HomeGridSizePx {
        val cellSizePx = cellSizePx(grid, maxWidthPx, maxHeightPx)
        return HomeGridSizePx(
            widthPx = cellSizePx * grid.columns,
            heightPx = cellSizePx * grid.rows,
            cellSizePx = cellSizePx,
        )
    }

    fun cellSizePx(
        grid: GridDimensions,
        maxWidthPx: Float,
        maxHeightPx: Float,
    ): Float {
        val widthBoundPx = maxWidthPx / grid.columns
        val heightBoundPx = maxHeightPx / grid.rows

        return minOf(widthBoundPx, heightBoundPx).coerceAtLeast(0f)
    }

    fun homeItemContentHeightDp(labelSettings: HomeLabelSettings): Int {
        if (!labelSettings.showText) {
            return labelSettings.iconSizeDp
        }

        return labelSettings.iconSizeDp +
            HOME_ICON_LABEL_SPACING_DP +
            homeLabelContainerHeightDp(labelSettings)
    }

    fun homeLabelContainerHeightDp(labelSettings: HomeLabelSettings): Int {
        val labelTextHeightDp = (labelSettings.textSizeSp + HOME_LABEL_LINE_HEIGHT_EXTRA_DP) * labelSettings.maxLines

        return HOME_LABEL_VERTICAL_PADDING_DP * 2 + labelTextHeightDp
    }

    fun fixedHomeLabelContainerWidthDp(labelSettings: HomeLabelSettings): Int? =
        when (labelSettings.sizing) {
            HomeLabelSizing.FIXED -> labelSettings.maxWidthDp
            HomeLabelSizing.DYNAMIC -> null
        }
}

internal data class HomeGridSizePx(
    val widthPx: Float,
    val heightPx: Float,
    val cellSizePx: Float,
)

private const val HOME_ICON_LABEL_SPACING_DP = 6
private const val HOME_LABEL_VERTICAL_PADDING_DP = 2
private const val HOME_LABEL_LINE_HEIGHT_EXTRA_DP = 3
