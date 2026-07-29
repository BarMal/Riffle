package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandardHomeGridLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun squareCellGridIsCenteredWithinWorkspace() {
        setContent(page = standardPage())

        assertGridIsCenteredWithSquareCells()
    }

    @Test
    fun generatedOverflowAffordanceKeepsGridCenteredInWorkspace() {
        setContent(
            page =
                standardPage().copy(
                    type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
                    generatedContentOverflowCount = 1,
                ),
        )

        composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG).assertExists()
        val root = composeRule.onNodeWithTag(ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val overflow =
            composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density

        with(density) {
            assertEquals(320.dp.toPx(), grid.width, 1f)
        }
        assertEquals(root.center.x, grid.center.x, 1f)
        assertEquals((root.top + overflow.top) / 2f, grid.center.y, 4f)
        assertFalse("overflow affordance must not cover the grid", grid.overlaps(overflow))
    }

    private fun setContent(page: LauncherPage) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp).testTag(ROOT_TEST_TAG)) {
                    WorkspaceGrid(
                        page = page,
                        gridState =
                            HomeGridState(
                                isEditing = false,
                                pageCount = 1,
                                selectedPageIndex = 0,
                                dragSession = null,
                            ),
                        presentation =
                            HomeGridPresentation(
                                notificationGroupsByApp = emptyList(),
                                appShortcutsByApp = emptyMap(),
                                labelSettings = HomeLabelSettings.standard(),
                                widgetViewFactory = EmptyHomeWidgetViewFactory,
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        modifier = Modifier.fillMaxSize(),
                        actions =
                            HomeWorkspaceActions(
                                onFolderOpen = {},
                                onDragSessionChanged = {},
                                haptics = NoopLauncherHaptics,
                                onAction = {},
                            ),
                    )
                }
            }
        }
    }

    private fun assertGridIsCenteredWithSquareCells() {
        val root = composeRule.onNodeWithTag(ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density

        with(density) {
            assertEquals(320.dp.toPx(), grid.width, 1f)
            assertEquals(400.dp.toPx(), grid.height, 1f)
        }
        assertEquals(root.center.x, grid.center.x, 1f)
        assertEquals(root.center.y, grid.center.y, 1f)
    }

    private fun standardPage(): LauncherPage =
        LauncherPage(
            id = LauncherPageId("home"),
            grid = GridDimensions(columns = 4, rows = 5),
        )
}

private const val ROOT_TEST_TAG = "home-workspace-grid-root"
