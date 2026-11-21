package com.riffle.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.AFTER_UPDATE_COPY_TEST_TAG
import com.riffle.app.launcher.AFTER_UPDATE_TEXT_TEST_TAG
import com.riffle.app.launcher.SettingsAfterUpdateContent
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateStart
import com.riffle.core.domain.launcher.diagnostics.StartMilestone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > About > After-update starts: the row, the recorded entries and the Copy button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class AfterUpdatePageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val entry =
        AfterUpdateStart(
            atMillis = 1_767_225_600_000,
            fromVersionCode = 41,
            toVersionCode = 42,
            intentText = "act=MAIN cat=[LAUNCHER] flg=0x10200000",
            isTaskRoot = true,
            homeRoleHeld = true,
            homeResolvesTo = "com.example.app/.Main",
            firstFrameMillis = 812,
            homeIntentsAtMillis = listOf(1_767_225_660_000),
            afterUpdate = true,
            cold = true,
            sdkInt = 34,
            taskBaseIntent = "act=MAIN cat=[LAUNCHER] flg=0x10000000",
            milestones =
                listOf(
                    StartMilestone("application", 140),
                    StartMilestone("MainActivity.onCreate entered", 260),
                    StartMilestone("home layout and pool read", 410),
                    StartMilestone("settings loaded", 470),
                    StartMilestone("workspaces loaded", 520),
                    StartMilestone("setContent called", 640),
                    StartMilestone("onResume", 700),
                    StartMilestone("window focus", 880),
                ),
        )

    private val normalEntry =
        entry.copy(
            atMillis = 1_767_225_700_000,
            fromVersionCode = 42,
            afterUpdate = false,
            firstFrameMillis = 690,
            taskBaseIntent = "act=MAIN cat=[HOME] flg=0x10000000",
            milestones = listOf(StartMilestone("application", 90)),
            homeIntentsAtMillis = emptyList(),
        )

    @Test
    fun theAboutPageListsTheDiagnosticAndOpensIt() {
        val state = ScreenshotFixtures.homeState().settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                SettingsSurface(state = state, initialPage = SettingsPage.VERSION, onAction = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("After-update starts").performScrollTo().assertIsDisplayed().performClick()
        // The entries are read from the device-local store off the main thread; nothing is recorded in a fresh test.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Nothing recorded yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Nothing recorded yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun theRecordedEntriesShowEveryFactAndCopySendsTheSameText() {
        val copied = mutableListOf<String>()
        composeRule.setContent {
            ScreenshotBackdrop {
                SettingsAfterUpdateContent(
                    entries = listOf(entry, normalEntry),
                    onCopy = { copied += it },
                )
            }
        }
        composeRule.captureScreen()

        composeRule.onNodeWithTag(AFTER_UPDATE_TEXT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("41 -> 42", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("first frame: 812 ms", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("(after update)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("setContent called: 640 ms", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AFTER_UPDATE_COPY_TEST_TAG).performClick()

        assertEquals(1, copied.size)
        assertTrue(copied.single().contains("home role held: true"))
        composeRule.onNodeWithText("Copied").assertIsDisplayed()
    }

    @Test
    fun anAfterUpdateStartAndANormalStartAreToldApartAndCopiedTogether() {
        composeRule.setContent {
            ScreenshotBackdrop { SettingsAfterUpdateContent(entries = listOf(normalEntry, entry), onCopy = {}) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AFTER_UPDATE_TEXT_TEST_TAG).assertTextContains("(normal start)", substring = true)
        composeRule.onNodeWithTag(AFTER_UPDATE_TEXT_TEST_TAG).assertTextContains("(after update)", substring = true)
        composeRule.onNodeWithTag(
            AFTER_UPDATE_TEXT_TEST_TAG,
        ).assertTextContains("start: cold, Android SDK 34", substring = true)
    }

    @Test
    fun noEntriesSaysSoAndOffersNoCopy() {
        composeRule.setContent { ScreenshotBackdrop { SettingsAfterUpdateContent(entries = emptyList(), onCopy = {}) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nothing recorded yet", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AFTER_UPDATE_COPY_TEST_TAG).assertDoesNotExist()
    }
}
