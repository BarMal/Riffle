package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.PRIVACY_LEVELS_PREVIEW_OFF_TAG
import com.riffle.app.launcher.PRIVACY_OPEN_EXCLUSIONS_TAG
import com.riffle.app.launcher.PRIVACY_OPEN_SOURCES_TAG
import com.riffle.app.launcher.PRIVACY_SCREEN_SWITCH_TAG
import com.riffle.app.launcher.SettingsPrivacySections
import com.riffle.app.launcher.contentLevelTestTag
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Privacy over fixed values (no runtime, no items: the page never shows item content). Preview on draws
 * the content levels and the links; preview off draws only the screenshot switch and the notes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PrivacySettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val switched = mutableListOf<Boolean>()
    private val picked = mutableListOf<Pair<SourceId, SourceContentLevel>>()
    private var openedExclusions = 0
    private var openedSources = 0

    private val rows =
        listOf(
            ContentLevelRow(SourceIds.NOTIFICATIONS, "Notifications", SourceContentLevel.HIDE_BODY),
            ContentLevelRow(SourceIds.MEDIA, "Media", SourceContentLevel.SHOW),
            ContentLevelRow(SourceIds.CALENDAR, "Calendar", SourceContentLevel.HIDE_TEXT),
        )

    private fun render(
        protect: Boolean = false,
        preview: Boolean = true,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsPrivacySections(
                        protectScreenContent = protect,
                        onProtectScreenContent = { switched += it },
                        contentLevels = if (preview) rows else null,
                        onSelectLevel = { id, level -> picked += id to level },
                        onOpenExclusions = if (preview) ({ openedExclusions++ }) else null,
                        onOpenSources = if (preview) ({ openedSources++ }) else null,
                    )
                }
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun previewOnCompact() {
        render()

        composeRule.onNodeWithTag(PRIVACY_SCREEN_SWITCH_TAG).assertExists()
        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_BODY))
            .performScrollTo()
            .assertIsSelected()
        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.NOTIFICATIONS, SourceContentLevel.SHOW))
            .assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun screenProtectionOnCompactDark() {
        render(protect = true)
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun previewOnLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun previewOnUnfolded() {
        render()
    }

    @Test
    fun previewOffShowsOnlyTheSwitchAndSaysWhereLevelsAre() {
        render(preview = false)

        composeRule.onNodeWithTag(PRIVACY_LEVELS_PREVIEW_OFF_TAG).performScrollTo().assertExists()
        composeRule.onNodeWithTag(PRIVACY_OPEN_EXCLUSIONS_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PRIVACY_OPEN_SOURCES_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.NOTIFICATIONS, SourceContentLevel.SHOW))
            .assertDoesNotExist()
    }

    @Test
    fun theSwitchIsOffByDefaultAndReportsATap() {
        render()

        composeRule.onNodeWithTag(PRIVACY_SCREEN_SWITCH_TAG).performClick()

        assertEquals(listOf(true), switched)
    }

    @Test
    fun choosingALevelReportsTheSourceAndLevelAndTheCurrentOneIsNotReported() {
        render()

        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.CALENDAR, SourceContentLevel.SHOW))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.CALENDAR, SourceContentLevel.HIDE_TEXT))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(SourceIds.CALENDAR to SourceContentLevel.SHOW), picked)
    }

    @Test
    fun optionsAreSpokenWithTheirSource() {
        render()

        composeRule.onNodeWithTag(contentLevelTestTag(SourceIds.MEDIA, SourceContentLevel.HIDE_TEXT))
            .performScrollTo()
            .assert(hasContentDescription("Media, App icons only", substring = true))
    }

    @Test
    fun linksOpenHiddenItemsAndSources() {
        render()

        composeRule.onNodeWithTag(PRIVACY_OPEN_EXCLUSIONS_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(PRIVACY_OPEN_SOURCES_TAG).performScrollTo().performClick()

        assertEquals(1, openedExclusions)
        assertEquals(1, openedSources)
    }
}
