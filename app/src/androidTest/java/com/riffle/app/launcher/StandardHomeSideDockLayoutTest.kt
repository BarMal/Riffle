package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

    // A filled dock, so the strip is clearly longer than it is thick: with the dynamic background
    // a one-item dock is a small square on any edge, which would tell us nothing about its run.
    private val docked = (0 until 5).map { index -> shortcut("docked$index") }

    @Test
    fun aLeadingDockIsANarrowStripDownTheLeadingEdge() {
        setContent(DockPosition.LEADING)

        val root = rootBounds()
        val dock = dockBounds()

        // Fractions of the root rather than exact edges: window insets and the grid margin both
        // sit between the dock and the screen edge, and neither is what this is about.
        assertTrue("expected it at the leading edge, dock=$dock root=$root", dock.left < root.left + root.width / 10f)
        assertTrue("expected a narrow strip, dock=$dock root=$root", dock.width < root.width / 3f)
        assertTrue("expected a tall strip, dock=$dock root=$root", dock.height > root.height / 2f)
    }

    @Test
    fun aTrailingDockTakesTheOtherEdge() {
        setContent(DockPosition.TRAILING)

        val root = rootBounds()
        val dock = dockBounds()

        assertTrue("expected it at the trailing edge, dock=$dock root=$root", dock.right > root.right - root.width / 10f)
        assertTrue("expected a narrow strip, dock=$dock root=$root", dock.width < root.width / 3f)
        assertTrue("expected a tall strip, dock=$dock root=$root", dock.height > root.height / 2f)
    }

    @Test
    fun aBottomDockIsStillAWideStripAcrossTheBottom() {
        setContent(DockPosition.BOTTOM)

        val root = rootBounds()
        val dock = dockBounds()

        assertTrue("expected a wide strip, dock=$dock root=$root", dock.width > root.width / 2f)
        assertTrue("expected a short strip, dock=$dock root=$root", dock.height < root.height / 3f)
        assertTrue("expected it near the bottom, dock=$dock root=$root", dock.top > root.top + root.height / 2f)
    }

    private fun rootBounds() = composeRule.onNodeWithTag(ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot

    private fun dockBounds() = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot

    private fun setContent(position: DockPosition) {
        val layout = layoutWithDockAt(position)
        val installed = docked.map { item -> item.installedApp() }
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(420.dp).testTag(ROOT_TEST_TAG)) {
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
                standard.copy(dock = standard.dock.copy(items = docked))
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

    private companion object {
        private const val ROOT_TEST_TAG = "side-dock-layout-root"
    }
}
