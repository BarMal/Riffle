package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the dock position control offers, which is what the layout can place.
 *
 * A Cards layout is still offered all four. Note that nothing acts on the choice there yet: the
 * rail it used to place is gone, and the dock Cards mode draws is still bottom-pinned -- see
 * `dockInteractionRegionHeightDp`, which reserves a height and only a height. Letting a Cards
 * dock take an edge is the follow-up; these only pin what the control shows.
 */
@RunWith(AndroidJUnit4::class)
class DockPositionSettingOptionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aStandardLayoutIsNotOfferedTheTopEdge() {
        setContent(LauncherViewMode.STANDARD_APP_DRAWER)

        composeRule.onNodeWithTag(positionTag(DockPosition.BOTTOM)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(positionTag(DockPosition.LEADING)).assertIsDisplayed()
        composeRule.onNodeWithTag(positionTag(DockPosition.TRAILING)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(positionTag(DockPosition.TOP)).assertCountEquals(0)
    }

    @Test
    fun aCardsLayoutIsOfferedAllFourEdges() {
        setContent(LauncherViewMode.CARD_INTERFACE)

        DockPosition.entries.forEach { candidate ->
            composeRule.onNodeWithTag(positionTag(candidate)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun allFourEdgesStayReachableAtCompactWidthWithLargeFont() {
        // Four of these do not fit across a phone. A plain row measured the last one to nothing,
        // which is how a Cards layout ended up with an edge it could see named but never tap.
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        DockSetting(
                            dock = DockModel(capacity = 4),
                            viewMode = LauncherViewMode.CARD_INTERFACE,
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            onAction = {},
                        )
                    }
                }
            }
        }

        DockPosition.entries.forEach { candidate ->
            composeRule
                .onNodeWithTag(positionTag(candidate))
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun anEdgeTheLayoutCannotPlaceSaysSoRatherThanClaimingIt() {
        // A layout can still hold the top edge from before the two sets were separated. Saying
        // "Top edge" while the dock sits at the bottom is the papercut; naming it is not.
        setContent(LauncherViewMode.STANDARD_APP_DRAWER, position = DockPosition.TOP)

        composeRule
            .onNodeWithText("Top edge is not available on this layout, so the dock is on the bottom edge")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun positionTag(position: DockPosition) = "dock-position-${position.name}"

    private fun setContent(
        viewMode: LauncherViewMode,
        position: DockPosition? = null,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DockSetting(
                        dock = DockModel(capacity = 4, position = position),
                        viewMode = viewMode,
                        notificationAccessStatus = NotificationAccessStatus.GRANTED,
                        onAction = {},
                    )
                }
            }
        }
    }
}
