package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun homeItemContentHeightUsesConfiguredIconSize() {
        assertEquals(
            88,
            metrics.homeItemContentHeightDp(
                HomeLabelSettings(iconSizeDp = 64),
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

    @Test
    fun fixedHomeLabelContainerWidthUsesConfiguredMaxWidth() {
        assertEquals(
            112,
            metrics.fixedHomeLabelContainerWidthDp(
                HomeLabelSettings(maxWidthDp = 112),
            ),
        )
    }

    @Test
    fun dynamicHomeLabelContainerWidthIsContentSized() {
        assertNull(
            metrics.fixedHomeLabelContainerWidthDp(
                HomeLabelSettings(
                    maxWidthDp = 112,
                    sizing = HomeLabelSizing.DYNAMIC,
                ),
            ),
        )
    }

    @Test
    fun folderPreviewLayoutFitsAtMinimumIconSize() {
        assertEquals(FolderPreviewLayout(paddingDp = 3, spacingDp = 1, childIconSizeDp = 12), folderPreviewLayout(32))
    }

    @Test
    fun folderPreviewLayoutScalesAtMaximumIconSize() {
        assertEquals(FolderPreviewLayout(paddingDp = 6, spacingDp = 2, childIconSizeDp = 25), folderPreviewLayout(64))
    }
}
