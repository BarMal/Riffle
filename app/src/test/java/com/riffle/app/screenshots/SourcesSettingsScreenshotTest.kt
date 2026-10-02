package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.ADD_CALENDAR_FEED_ROW_TEST_TAG
import com.riffle.app.launcher.ADD_RSS_ROW_TEST_TAG
import com.riffle.app.launcher.HIDDEN_ITEMS_ROW_TEST_TAG
import com.riffle.app.launcher.SourceDetailText
import com.riffle.app.launcher.SourcesSettingsContent
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.app.launcher.sourceRowTestTag
import com.riffle.app.launcher.sourceSwitchTestTag
import com.riffle.app.launcher.sourceUsedByTestTag
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceUsagePlanner
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
    private var openedHiddenItems = 0
    private val opened = mutableListOf<SourceId>()
    private var addedRss = 0
    private var addedCalendar = 0
    private val usage =
        SourceUsagePlanner.plan(
            mapOf(
                WorkspacesSettingsFixtures.phone to
                    WorkspacesSettingsFixtures.set.workspacesFor(WorkspacesSettingsFixtures.phone),
            ),
        )

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
        composeRule.onNodeWithTag(HIDDEN_ITEMS_ROW_TEST_TAG).assertExists()
        composeRule.onNodeWithText("Hidden items and rules (coming soon)").assertDoesNotExist()
    }

    @Test
    fun theHiddenItemsRowOpensTheRulesPage() {
        render()

        composeRule.onNodeWithTag(HIDDEN_ITEMS_ROW_TEST_TAG).performScrollTo().performClick()

        assertEquals(1, openedHiddenItems)
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

        composeRule.onNodeWithTag(sourceSwitchTestTag(SourceIds.ALL_APPS)).performScrollTo().performClick()
        composeRule.onNodeWithTag(sourceSwitchTestTag(SourceIds.CALENDAR)).performScrollTo().performClick()

        assertEquals(listOf(SourceIds.ALL_APPS to false, SourceIds.CALENDAR to true), toggles)
        assertEquals(emptyList<SourceId>(), opened)
    }

    @Test
    fun tappingARowOpensItsDetailPageWithoutTogglingIt() {
        render()

        composeRule.onNodeWithTag(sourceRowTestTag(SourceIds.NOTIFICATIONS)).performScrollTo().performClick()
        composeRule.onNodeWithTag(sourceRowTestTag(SourceIds.RSS)).performScrollTo().performClick()

        assertEquals(listOf(SourceIds.NOTIFICATIONS, SourceIds.RSS), opened)
        assertEquals(emptyList<Pair<SourceId, Boolean>>(), toggles)
    }

    @Test
    fun everyRowSaysHowManyPlacesUseIt() {
        render()

        SourceIds.BUILT_IN.forEach { id ->
            composeRule.onNodeWithTag(sourceUsedByTestTag(id), useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag(sourceRowTestTag(id)).assert(
                hasContentDescription(SourceDetailText.usedBy(usage.countOf(id)), substring = true),
            )
        }
    }

    @Test
    fun withoutLoadedWorkspacesRowsShowNoUsedByLine() {
        render(withUsage = false)

        composeRule.onNodeWithTag(sourceUsedByTestTag(SourceIds.ALL_APPS), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theAddSourceEntriesReuseTheExistingFlows() {
        render()

        composeRule.onNodeWithTag(ADD_RSS_ROW_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(ADD_CALENDAR_FEED_ROW_TEST_TAG).performScrollTo().performClick()

        assertEquals(1, addedRss)
        assertEquals(1, addedCalendar)
    }

    @Test
    fun addCalendarFeedIsOfferedOnlyWhereCalendarFeedsExist() {
        render(withFeeds = false)

        composeRule.onNodeWithTag(ADD_RSS_ROW_TEST_TAG).performScrollTo().assertExists()
        composeRule.onNodeWithTag(ADD_CALENDAR_FEED_ROW_TEST_TAG).assertDoesNotExist()
    }

    private fun render(
        withUsage: Boolean = true,
        withFeeds: Boolean = true,
    ) {
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
                        onOpenHiddenItems = { openedHiddenItems++ },
                        usage = usage.takeIf { withUsage },
                        onOpenSource = { opened += it },
                        onAddRssFeed = { addedRss++ },
                        onAddCalendarFeed = { addedCalendar++ },
                        calendarFeeds = IcsFeedsUiState(loaded = true).takeIf { withFeeds },
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}
