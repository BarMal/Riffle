package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HostedWidgetAddCompletionTest {
    @Test
    fun completesHostedWidgetAddAndClosesWidgetPicker() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())
        runBlocking {
            viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)?.join()
        }

        val message =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(7),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 2),
                ),
            )

        val widget = viewModel.state.value.homeLayout.selectedPage.items.single() as WidgetItem
        assertEquals(HostedWidgetId(7), widget.appWidgetId)
        assertEquals(GridSpan(columns = 2, rows = 2), widget.placement?.span)
        assertFalse(viewModel.state.value.isWidgetPickerOpen)
        assertNull(message)
    }

    @Test
    fun returnsAdjustmentMessageWhenWidgetIsShrunkFromPreferredSpan() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        val message =
            viewModel.completeWidgetAdd(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(9),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 6, rows = 1),
                ),
            )

        assertEquals("Calendar ideal size is 6x1; added as 4x1", message)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }
}
