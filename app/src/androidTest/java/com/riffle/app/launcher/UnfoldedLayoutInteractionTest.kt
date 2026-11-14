package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
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
 * An unfolded Home (ADR 0003, Amendment 2) on a device, through the real [HomeDestination]: the Dock on the right edge
 * by default with the Home handle standing in front of it as a column of dots, a drag down the dots asking for the last
 * page, and a vertical swipe on the pages asking for the next one. The shell is a recording lambda, and the device class
 * comes from the state, so this holds on any screen the device has.
 */
@RunWith(AndroidJUnit4::class)
class UnfoldedLayoutInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

    @Test
    fun theDockIsOnTheRightEdgeWithAColumnHandleInFrontOfIt() {
        setContent()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val handle = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("the Dock is not on the right edge: $dock in $root", dock.center.x > root.center.x)
        assertTrue("the handle is not in front of the Dock: $handle $dock", handle.right <= dock.left + 1f)
        assertTrue("the handle is not a column: $handle", handle.height > handle.width)
        assertTrue(
            "the handle is not in front of the Dock's middle: $handle $dock",
            kotlin.math.abs(handle.center.y - dock.center.y) < MIDDLE_TOLERANCE_PX,
        )
    }

    @Test
    fun draggingDownTheDotsAsksForTheLastPage() {
        val actions = setContent()

        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(center.x, 1f), end = Offset(center.x, height - 1f))
        }

        composeRule.runOnIdle {
            assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-$PAGE_COUNT")), actions.last())
            assertTrue("a drag along the dots opened search: $actions", LauncherShellAction.OpenSearch !in actions)
        }
    }

    @Test
    fun aVerticalSwipeOnThePagesAsksForTheNextPage() {
        val actions = setContent()

        composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).onFirst().performTouchInput {
            swipe(
                start = Offset(center.x, height * SWIPE_FROM),
                end = Offset(center.x, height * SWIPE_TO),
                durationMillis = SWIPE_MILLIS,
            )
        }

        composeRule.runOnIdle {
            assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-2")), actions.last())
        }
    }

    private fun setContent(): MutableList<LauncherShellAction> {
        val actions = mutableListOf<LauncherShellAction>()
        val deviceClass = HomeLayoutDeviceClass.FOLDABLE
        val layout =
            HomeLayoutDefaults.standard(deviceClass).let { standard ->
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
        val key = HomeLayoutKey(viewMode = layout.viewMode, deviceClass = deviceClass)
        val state =
            LauncherShellState(
                homeLayout = layout,
                homeLayoutSet = HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout)),
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
        const val MIDDLE_TOLERANCE_PX = 8f
        const val SWIPE_FROM = 0.85f
        const val SWIPE_TO = 0.15f
        const val SWIPE_MILLIS = 200L
    }
}
