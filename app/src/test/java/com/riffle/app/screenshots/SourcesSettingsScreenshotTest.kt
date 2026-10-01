package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.SourcesSettingsContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Sources over fixed statuses (ready, loading, needs permission, off): compact and unfolded (two
 * columns). Nothing here requests a permission: the Allow buttons only report that they were tapped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SourcesSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toggles = mutableListOf<Pair<SourceId, Boolean>>()
    private val allowed = mutableListOf<SourceId>()

    @Test
    fun sourcesCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun sourcesCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun sourcesCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun sourcesUnfolded() {
        render()
    }

    @Test
    fun nothingRequestsAPermissionUntilAnAllowButtonIsTapped() {
        render()

        assertEquals(emptyList<SourceId>(), allowed)
        composeRule.onNodeWithText("Hidden items and rules (coming soon)").assertExists()
    }

    @Test
    fun allowOnlyReportsTheTappedSourceToTheExistingFlow() {
        render()

        composeRule.onAllNodesWithText("Allow notification access")[0].performScrollTo().performClick()

        assertEquals(listOf(SourceIds.NOTIFICATIONS), allowed)
    }

    @Test
    fun theSwitchTurnsASourceOnOrOff() {
        render()

        composeRule.onNodeWithText("Apps").performScrollTo().performClick()
        composeRule.onNodeWithText("Calendar").performScrollTo().performClick()

        assertEquals(listOf(SourceIds.ALL_APPS to false, SourceIds.CALENDAR to true), toggles)
    }

    private fun render() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    SourcesSettingsContent(
                        rows = WorkspacesSettingsFixtures.sources,
                        calendarAccess = CalendarAccessStatus.NOT_GRANTED,
                        onToggle = { id, enabled -> toggles += id to enabled },
                        onAllow = { allowed += it },
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}
