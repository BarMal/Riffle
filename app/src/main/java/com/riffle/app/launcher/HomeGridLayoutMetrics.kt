package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings

class HomeGridLayoutMetrics {
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
            return HOME_ICON_SIZE_DP
        }

        val labelTextHeightDp = (labelSettings.textSizeSp + HOME_LABEL_LINE_HEIGHT_EXTRA_DP) * labelSettings.maxLines

        return HOME_ICON_SIZE_DP +
            HOME_ICON_LABEL_SPACING_DP +
            HOME_LABEL_VERTICAL_PADDING_DP * 2 +
            labelTextHeightDp
    }
}

internal const val HOME_ICON_SIZE_DP = 44

private const val HOME_ICON_LABEL_SPACING_DP = 6
private const val HOME_LABEL_VERTICAL_PADDING_DP = 2
private const val HOME_LABEL_LINE_HEIGHT_EXTRA_DP = 3
