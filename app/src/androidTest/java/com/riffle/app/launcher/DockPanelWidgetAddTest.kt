package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Reaching the widget picker from the panel.
 *
 * The panel cannot be dragged onto -- the picker covers the shelf it lives on -- so the way in has
 * to say where the widget is going before the picker opens.
 */
class DockPanelWidgetAddTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = shortcut("docked")

    @Test
    fun longPressingThePanelsEmptySpaceOpensThePickerAimedAtThePanel() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)
        openTheShelf()

        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).performTouchInput { longClick() }

        composeRule.onNodeWithText("Add widget").assertIsDisplayed()
        composeRule.onNodeWithText("Add widget").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.OpenWidgetPickerForDockPanel), actions)
        }
    }

    @Test
    fun thePanelDoesNotOfferTheHomePageActions() {
        // Page management and folders belong to home pages, which the panel is not.
        setContent(mutableListOf())
        openTheShelf()

        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).performTouchInput { longClick() }

        composeRule.onNodeWithText("Manage pages").assertDoesNotExist()
        composeRule.onNodeWithText("Create folder").assertDoesNotExist()
    }

    private fun openTheShelf() {
        composeRule.onNodeWithTag(dockItemTestTag(docked.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DOCK_PANEL_TEST_TAG).assertIsDisplayed()
    }

    private fun panelLayout(): HomeLayout =
        HomeLayoutDefaults.standard().let { standardLayout ->
            standardLayout.copy(
                dock =
                    standardLayout.dock.copy(
                        items = listOf(docked),
                        showNotificationCards = false,
                        panel =
                            LauncherPage(
                                id = LauncherPageId("dock-panel"),
                                grid = GridDimensions(columns = 4, rows = 2),
                            ),
                    ),
            )
        }

    private fun setContent(actions: MutableList<LauncherShellAction>) {
        val layout = panelLayout()
        val installed = listOf(docked.installedApp())
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
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
                        onAction = { action -> actions += action },
                    )
                }
            }
        }
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
