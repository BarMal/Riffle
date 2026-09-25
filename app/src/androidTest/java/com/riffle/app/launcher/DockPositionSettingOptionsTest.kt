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
import com.riffle.core.domain.launcher.home.sharedDockPositions
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the dock position control offers, which is what the layout can place.
 *
 * There is one dock per device class, shared by every mode (#1205), so it is offered only the edges
 * every mode can draw it on. Cards used to be offered the top edge as well; that premise was removed
 * deliberately, because a top dock would sit in a different place depending on the mode.
 */
@RunWith(AndroidJUnit4::class)
class DockPositionSettingOptionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSharedDockIsNotOfferedTheTopEdge() {
        setContent()

        composeRule.onNodeWithTag(positionTag(DockPosition.BOTTOM)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(positionTag(DockPosition.LEFT)).assertIsDisplayed()
        composeRule.onNodeWithTag(positionTag(DockPosition.RIGHT)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(positionTag(DockPosition.TOP)).assertCountEquals(0)
    }

    @Test
    fun everyOfferedEdgeStaysReachableAtCompactWidthWithLargeFont() {
        // A plain row measured the last edge to nothing at this width, which is how a layout ended
        // up with an edge it could see named but never tap.
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        DockSetting(
                            dock = DockModel(capacity = 4),
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            onAction = {},
                        )
                    }
                }
            }
        }

        sharedDockPositions.forEach { candidate ->
            composeRule
                .onNodeWithTag(positionTag(candidate))
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun anEdgeTheLayoutCannotPlaceSaysSoRatherThanClaimingIt() {
        // A dock can still hold the top edge from before it was shared. Saying "Top edge" while the
        // dock sits at the bottom is the papercut; naming it is not.
        setContent(position = DockPosition.TOP)

        composeRule
            .onNodeWithText("Top edge is not available on this layout, so the dock is on the bottom edge")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun positionTag(position: DockPosition) = "dock-position-${position.name}"

    private fun setContent(position: DockPosition? = null) {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DockSetting(
                        dock = DockModel(capacity = 4, position = position),
                        notificationAccessStatus = NotificationAccessStatus.GRANTED,
                        onAction = {},
                    )
                }
            }
        }
    }
}
