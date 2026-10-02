package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.ICS_FEEDS_EMPTY_TEST_TAG
import com.riffle.app.launcher.ICS_FEEDS_REFRESH_TEST_TAG
import com.riffle.app.launcher.IcsFeedsListContent
import com.riffle.app.launcher.icsFeedRowTestTag
import com.riffle.app.launcher.ics.IcsFeedRow
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.app.launcher.ics.IcsRefreshReport
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Calendar feeds (ICS) over fixed states. Only the static list is rendered: the add form has text
 * fields, and a focused text field's cursor blink hangs the screenshot run, so its logic is tested on the JVM
 * (`IcsFeedsControllerTest`) instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class IcsFeedsSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toggles = mutableListOf<Pair<IcsFeedId, Boolean>>()
    private val removed = mutableListOf<IcsFeedId>()
    private var refreshes = 0

    private val work = row("work", "Work", "calendar.example.com", lastUpdated = 1_773_144_000_000L)
    private val broken = row("broken", "Team", "teams.example.org", lastFailure = FeedRefreshFailure.TIMEOUT)
    private val off = row("off", "Holidays", "holidays.example.net", enabled = false)

    @Test
    fun emptyCompact() {
        render(IcsFeedsUiState(loaded = true))
    }

    @Test
    fun feedsCompact() {
        render(IcsFeedsUiState(loaded = true, rows = listOf(work, broken, off)))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun feedsCompactDark() {
        render(IcsFeedsUiState(loaded = true, rows = listOf(work, broken, off)))
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun feedsCompactLargeFont() {
        render(
            IcsFeedsUiState(
                loaded = true,
                rows = listOf(work),
                lastReport = IcsRefreshReport(mapOf(work.id to FeedRefreshOutcome.Updated(1, 1)), emptyMap()),
            ),
        )
    }

    @Test
    fun refreshingDisablesTheButtonAndShowsProgress() {
        render(IcsFeedsUiState(loaded = true, rows = listOf(work), refreshing = true))
        composeRule.onNodeWithTag(ICS_FEEDS_REFRESH_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun noFeedsShowsTheEmptyStateAndRefreshIsDisabled() {
        render(IcsFeedsUiState(loaded = true))
        composeRule.onNodeWithTag(ICS_FEEDS_EMPTY_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(ICS_FEEDS_REFRESH_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun refreshReportsATapAndRowsOnlyShowTheHost() {
        render(IcsFeedsUiState(loaded = true, rows = listOf(work)))

        composeRule.onNodeWithTag(ICS_FEEDS_REFRESH_TEST_TAG).performScrollTo().performClick()

        assertEquals(1, refreshes)
        composeRule.onNodeWithTag(icsFeedRowTestTag(work.id)).assertExists()
    }

    private fun render(state: IcsFeedsUiState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    IcsFeedsListContent(
                        state = state,
                        onRefresh = { refreshes++ },
                        onToggle = { id, enabled -> toggles += id to enabled },
                        onRemove = { removed += it },
                    )
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun row(
        id: String,
        name: String,
        host: String,
        enabled: Boolean = true,
        lastUpdated: Long? = null,
        lastFailure: FeedRefreshFailure? = null,
    ) = IcsFeedRow(IcsFeedId(id), name, host, enabled, false, lastUpdated, lastFailure)
}
