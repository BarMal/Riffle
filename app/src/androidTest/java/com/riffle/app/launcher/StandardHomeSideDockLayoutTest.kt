package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A dock on a side edge sits beside the workspace and reserves its width, rather than stacking
 * under it. The grid gives up a column for that width, which is covered in the domain tests.
 */
@RunWith(AndroidJUnit4::class)
class StandardHomeSideDockLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = shortcut("docked")

    @Test
    fun aLeadingDockSitsBesideTheWorkspaceRatherThanUnderIt() {
        setContent(DockPosition.LEADING)

        val dock = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("expected the dock left of the grid, dock=$dock grid=$grid", dock.right <= grid.left)
    }

    @Test
    fun aTrailingDockTakesTheOtherSide() {
        setContent(DockPosition.TRAILING)

        val dock = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("expected the dock right of the grid, dock=$dock grid=$grid", dock.left >= grid.right)
    }

    @Test
    fun aBottomDockStillStacksUnderTheWorkspace() {
        setContent(DockPosition.BOTTOM)

        val dock = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("expected the dock below the grid, dock=$dock grid=$grid", dock.top >= grid.bottom)
    }

    private fun setContent(position: DockPosition) {
        val layout = layoutWithDockAt(position)
        val installed = listOf(docked.installedApp())
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(420.dp)) {
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

    /** Through the engine, so the pages carry the grid a real position change would leave them. */
    private fun layoutWithDockAt(position: DockPosition): HomeLayout {
        val seeded =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(dock = standard.dock.copy(items = listOf(docked)))
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
