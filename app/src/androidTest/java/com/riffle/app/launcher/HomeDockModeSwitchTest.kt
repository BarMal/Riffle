package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dock is drawn once, outside the mode surface (#1205, Decision 2), so switching mode keeps the
 * very same dock -- the same node, on the same edge at the same thickness -- while the grid and Cards
 * swap beneath it.
 *
 * Its run along the edge is not compared: that follows what the dynamic side holds, which is the
 * mode's to supply through its interpreter. In Cards the section is the stage selector and always
 * offers "All" (#1212), so the strip grows by that section there, exactly as it does in a grid mode
 * when a notification arrives.
 */
@RunWith(AndroidJUnit4::class)
class HomeDockModeSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until 4).map { index -> shortcut("docked$index") }

    @Test
    fun switchingBetweenLibraryAndCardsKeepsTheSameDockInTheSamePlace() {
        var state by mutableStateOf(shellState(LauncherViewMode.HOME_SCREEN_LIBRARY))
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    onAction = {},
                )
            }
        }
        val inLibrary = dockNode()

        composeRule.runOnIdle { state = shellState(LauncherViewMode.CARD_INTERFACE) }
        val inCards = dockNode()

        assertEquals("the dock was rebuilt entering Cards", inLibrary.id, inCards.id)
        assertSameEdgePlacement("entering Cards", inLibrary, inCards)

        composeRule.runOnIdle { state = shellState(LauncherViewMode.STANDARD_APP_DRAWER) }
        val inStandard = dockNode()

        assertEquals("the dock was rebuilt leaving Cards", inLibrary.id, inStandard.id)
        assertEquals("the dock moved leaving Cards", inLibrary.boundsInRoot, inStandard.boundsInRoot)
    }

    /** Same edge, same thickness: the bottom dock's bottom and height hold across the switch. */
    private fun assertSameEdgePlacement(
        transition: String,
        before: SemanticsNode,
        after: SemanticsNode,
    ) {
        assertEquals("the dock left its edge $transition", before.boundsInRoot.bottom, after.boundsInRoot.bottom)
        assertEquals("the dock changed thickness $transition", before.boundsInRoot.height, after.boundsInRoot.height)
    }

    private fun dockNode() = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode()

    /** Every mode's layout from the same defaults, so only the mode differs between them. */
    private fun shellState(viewMode: LauncherViewMode): LauncherShellState {
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(viewMode = viewMode, dock = standard.dock.copy(items = docked))
            }
        return LauncherShellState(
            homeLayout = layout,
            installedApps = docked.map { item -> item.installedApp() },
        )
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
