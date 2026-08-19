package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockConfigurationEngine
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shelf grows out of the edge the dock is on: its panel sits beside a side dock's strip and
 * above a bottom dock's, and the pull that opens it points away from that edge either way.
 */
@RunWith(AndroidJUnit4::class)
class DockShelfEdgeLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = shortcut("docked")
    private val panelled = shortcut("clock")

    @Test
    fun aLeadingDocksShelfOpensSidewaysAndPutsItsPanelBesideTheStrip() {
        setContent(DockPosition.LEADING)

        pullAwayFromTheEdge(Offset(96f, 0f))

        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).assertIsDisplayed()
        val panel = composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val strip = composeRule.onNodeWithTag(dockItemTestTag(docked.id)).fetchSemanticsNode().boundsInRoot

        assertTrue("expected the panel right of the strip, panel=$panel strip=$strip", panel.left >= strip.right)
    }

    @Test
    fun aBottomDocksShelfStillOpensUpwardAndPutsItsPanelAboveTheStrip() {
        setContent(DockPosition.BOTTOM)

        pullAwayFromTheEdge(Offset(0f, -96f))

        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).assertIsDisplayed()
        val panel = composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val strip = composeRule.onNodeWithTag(dockItemTestTag(docked.id)).fetchSemanticsNode().boundsInRoot

        assertTrue("expected the panel above the strip, panel=$panel strip=$strip", panel.bottom <= strip.top)
    }

    @Test
    fun aSwipeUpDoesNotOpenALeadingDocksShelf() {
        // Up is along a side dock's run, not away from its edge, so it is not the shelf's gesture.
        setContent(DockPosition.LEADING)

        pullAwayFromTheEdge(Offset(0f, -96f))

        composeRule.onAllNodesWithTag(DOCK_PANEL_TEST_TAG).assertCountEquals(0)
    }

    private fun pullAwayFromTheEdge(delta: Offset) {
        composeRule.onNodeWithTag(dockItemTestTag(docked.id)).performTouchInput {
            down(center)
            moveBy(delta / 4f)
            updatePointerBy(pointerId = 0, delta = delta)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun setContent(position: DockPosition) {
        val layout = layoutWithDockAt(position)
        val installed = listOf(docked.installedApp(), panelled.installedApp())
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(460.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = installed,
                        interactions = StandardHomeInteractions(),
                        presentation =
                            StandardHomePresentation(
                                installedApps = installed,
                                appShortcutsByApp = emptyMap(),
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }
    }

    private fun layoutWithDockAt(position: DockPosition): HomeLayout {
        val seeded =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    dock =
                        standard.dock.copy(
                            items = listOf(docked),
                            showNotificationCards = false,
                            panel =
                                LauncherPage(
                                    id = LauncherPageId("dock-panel"),
                                    grid = GridDimensions(columns = 2, rows = 2),
                                    items =
                                        listOf(
                                            panelled.copy(
                                                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                            ),
                                        ),
                                ),
                        ),
                )
            }
        val result = DockConfigurationEngine().setDockPosition(layout = seeded, position = position)
        return (result as DockEditResult.Updated).layout
    }

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private fun AppShortcutItem.installedApp(): InstalledApp =
        InstalledApp(
            identity = appIdentity,
            label = label,
        )
}
