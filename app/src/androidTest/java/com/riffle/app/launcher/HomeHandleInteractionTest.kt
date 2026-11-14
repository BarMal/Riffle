package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Home handle (ADR 0003, Amendment 1) on a device, through the real [HomeDestination]: a tap on it asks the shell
 * for search, dragging along its dots asks for the last page, and the shell's search destination draws the Dock's search
 * field. The shell is a recording lambda.
 */
@RunWith(AndroidJUnit4::class)
class HomeHandleInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

    @Test
    fun tappingTheHandleOpensSearch() {
        val actions = setContent()

        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()

        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions) }
    }

    @Test
    fun draggingAlongTheDotsScrubsToTheLastPage() {
        val actions = setContent()

        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(1f, height / 2f), end = Offset(width - 1f, height / 2f))
        }

        composeRule.runOnIdle {
            assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-$PAGE_COUNT")), actions.last())
            assertTrue("a drag along the dots opened search: $actions", LauncherShellAction.OpenSearch !in actions)
        }
    }

    @Test
    fun theSearchDestinationDrawsTheFieldOnTheDock() {
        setContent(destination = ShellDestination.SEARCH)

        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).assertExists()
    }

    private fun setContent(destination: ShellDestination = ShellDestination.HOME): MutableList<LauncherShellAction> {
        val actions = mutableListOf<LauncherShellAction>()
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(capacity = DOCKED_COUNT, items = docked),
                    pages =
                        standard.pages +
                            (2..PAGE_COUNT).map { index ->
                                LauncherPage(id = LauncherPageId("page-$index"), grid = standard.pages.first().grid)
                            },
                )
            }
        val state =
            LauncherShellState(
                homeLayout = layout,
                destination = destination,
                installedApps = docked.map { item -> InstalledApp(identity = item.appIdentity, label = item.label) },
            )
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        return actions
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

    private companion object {
        const val DOCKED_COUNT = 4
        const val PAGE_COUNT = 3
    }
}
