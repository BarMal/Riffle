package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The dock's per-layout expansion settings, from the surface's side: whether the shelf can be
 * opened at all, and which affordance opens it.
 */
class DockExpansionInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val primary = shortcut("primary")
    private val overflow = shortcut("overflow")

    @Test
    fun aButtonExpandedDockOpensFromItsButtonAndLeavesTheSwipeAlone() {
        setContent(dockLayout(expandAffordance = DockExpandAffordance.BUTTON))

        composeRule.onAllNodesWithContentDescription(EXPAND_LABEL).assertCountEquals(1)

        // The swipe belongs to the dock's own gesture action now, so it must not open the shelf.
        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription(EXPAND_LABEL).assertCountEquals(1)
        composeRule.onAllNodesWithTag(dockItemTestTag(overflow.id)).assertCountEquals(0)

        composeRule.onNodeWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription(COLLAPSE_LABEL).assertCountEquals(1)
        val primaryBounds = composeRule.onNodeWithTag(dockItemTestTag(primary.id)).fetchSemanticsNode().boundsInRoot
        val overflowBounds = composeRule.onNodeWithTag(dockItemTestTag(overflow.id)).fetchSemanticsNode().boundsInRoot
        assertTrue(overflowBounds.center.y < primaryBounds.center.y)
    }

    @Test
    fun aDockThatIsNotExpandableOffersNeitherAffordance() {
        setContent(dockLayout(isExpandable = false))

        composeRule.onAllNodesWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).assertCountEquals(0)

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(dockItemTestTag(overflow.id)).assertCountEquals(0)
    }

    @Test
    fun theSwipeStillOpensADockLeftOnItsDefaults() {
        // The companion to the two above: nothing here changes for an install that never touches
        // these settings.
        setContent(dockLayout())

        composeRule.onAllNodesWithTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).assertCountEquals(0)

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }
        composeRule.waitForIdle()

        val primaryBounds = composeRule.onNodeWithTag(dockItemTestTag(primary.id)).fetchSemanticsNode().boundsInRoot
        val overflowBounds = composeRule.onNodeWithTag(dockItemTestTag(overflow.id)).fetchSemanticsNode().boundsInRoot
        assertTrue(overflowBounds.center.y < primaryBounds.center.y)
    }

    private fun dockLayout(
        isExpandable: Boolean = true,
        expandAffordance: DockExpandAffordance = DockExpandAffordance.GESTURE,
    ): HomeLayout =
        HomeLayoutDefaults.standard().let { standardLayout ->
            standardLayout.copy(
                dock =
                    standardLayout.dock.copy(
                        capacity = 1,
                        items = listOf(primary, overflow),
                        isExpandable = isExpandable,
                        expandAffordance = expandAffordance,
                    ),
            )
        }

    private fun setContent(layout: HomeLayout) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = listOf(primary.installedApp(), overflow.installedApp()),
                        interactions = StandardHomeInteractions(),
                        presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
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

    private companion object {
        const val EXPAND_LABEL = "Expand dock"
        const val COLLAPSE_LABEL = "Collapse dock"
    }
}
