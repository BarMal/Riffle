package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridLayoutMetricsTest {
    private val metrics = HomeGridLayoutMetrics()

    @Test
    fun cellSizeUsesWidthBoundWhenWidthIsTighter() {
        assertEquals(
            100f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 400f,
                maxHeightPx = 800f,
            ),
        )
    }

    @Test
    fun cellSizeUsesHeightBoundWhenHeightIsTighter() {
        assertEquals(
            80f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 800f,
                maxHeightPx = 400f,
            ),
        )
    }

    @Test
    fun cellSizeCanShrinkIntoTinyWorkspace() {
        assertEquals(
            2f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 10f,
                maxHeightPx = 10f,
            ),
        )
    }

    @Test
    fun homeItemContentHeightUsesOnlyIconWhenLabelsAreHidden() {
        assertEquals(
            44,
            metrics.homeItemContentHeightDp(
                HomeLabelSettings.standard().copy(showText = false),
            ),
        )
    }

    @Test
    fun homeItemContentHeightReservesDefaultSingleLineLabel() {
        assertEquals(
            68,
            metrics.homeItemContentHeightDp(HomeLabelSettings.standard()),
        )
    }

    @Test
    fun homeLabelContainerHeightReservesDefaultSingleLineLabel() {
        assertEquals(
            18,
            metrics.homeLabelContainerHeightDp(HomeLabelSettings.standard()),
        )
    }

    @Test
    fun homeItemContentHeightReservesConfiguredMultilineLabel() {
        assertEquals(
            92,
            metrics.homeItemContentHeightDp(
                HomeLabelSettings(
                    textSizeSp = 16,
                    maxLines = 2,
                ),
            ),
        )
    }

    @Test
    fun homeLabelContainerHeightReservesConfiguredMultilineLabel() {
        assertEquals(
            42,
            metrics.homeLabelContainerHeightDp(
                HomeLabelSettings(
                    textSizeSp = 16,
                    maxLines = 2,
                ),
            ),
        )
    }
}
