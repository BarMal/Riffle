package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmediateHomePagerTest {
    @Test
    fun mapsWidgetPickerDropPositionWithinOffsetWorkspaceBounds() {
        assertEquals(
            GridCell(column = 2, row = 3),
            widgetPickerDropCell(
                position = Offset(300f, 550f),
                gridBounds = Rect(left = 100f, top = 200f, right = 500f, bottom = 700f),
                grid = GridDimensions(columns = 4, rows = 5),
            ),
        )
    }

    @Test
    fun separatesWorkspaceAndDockUsingMeasuredWorkspaceBounds() {
        val bounds = Rect(left = 80f, top = 120f, right = 720f, bottom = 920f)
        val dockBounds = Rect(left = 160f, top = 960f, right = 640f, bottom = 1080f)

        assertEquals(WidgetAddTarget.HOME, widgetPickerDropTarget(Offset(400f, 500f), bounds, dockBounds))
        assertEquals(WidgetAddTarget.DOCK, widgetPickerDropTarget(Offset(400f, 980f), bounds, dockBounds))
        assertEquals(null, widgetPickerDropTarget(Offset(400f, 940f), bounds, dockBounds))
        assertEquals(null, widgetPickerDropTarget(Offset(20f, 500f), bounds, dockBounds))
    }

    @Test
    fun selectsStandardSpringPageSettlePolicyWhenReducedMotionIsOff() {
        assertEquals(
            HomePageSettleMotionPolicy.StandardSpring,
            homePageSettleMotionPolicy(reducedMotion = false),
        )
    }

    @Test
    fun selectsShortTweenPageSettlePolicyWhenReducedMotionIsOn() {
        assertEquals(
            HomePageSettleMotionPolicy.ReducedShortTween,
            homePageSettleMotionPolicy(reducedMotion = true),
        )
        assertEquals(80, REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS)
    }
}
