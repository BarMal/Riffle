package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.BeforeImportCopies
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalBeforeImportCopies
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Settings > Backup with the "Undo last import" row, and the "Backup imported" announcement with Undo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class BackupUndoScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    @Before
    fun fixedZone() {
        // The row prints the date of the copy: the golden must not depend on the machine's zone.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }

    private fun copies(kept: Boolean): BeforeImportCopies {
        val file = File.createTempFile("before", ".json")
        file.delete()
        return BeforeImportCopies(file).also {
            if (kept) it.keep("""{"type":"riffleLauncherBackup","version":1,"exportedAtEpochMillis":1700000000000}""")
        }
    }

    private fun show(copies: BeforeImportCopies) {
        val surface = ScreenshotFixtures.homeState().settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalBeforeImportCopies provides copies) {
                    SettingsSurface(
                        state = surface,
                        initialPage = SettingsPage.BACKUP,
                        onAction = { actions += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun backupPageWithTheUndoLastImportRow() {
        show(copies(kept = true))
        composeRule.captureScreen()
        composeRule.onNodeWithText("Undo last import").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun backupPageHasNoUndoRowWithoutACopy() {
        show(copies(kept = false))
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTextCount("Undo last import"))
    }

    @Test
    fun theUndoRowSendsTheUndoAction() {
        show(copies(kept = true))
        composeRule.onNodeWithText("Undo last import").performClick()
        assertEquals(listOf(LauncherShellAction.UndoLastBackupImport), actions)
    }

    @Test
    fun anImportIsAnnouncedWithUndoAndUndoSendsTheAction() {
        val kept = copies(kept = true)
        show(kept)
        kept.announceImport()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Backup imported").assertIsDisplayed()
        composeRule.captureScreen()
        // The row's own Undo button is there too; the snackbar's is the last one.
        composeRule.onAllNodesWithText("Undo").onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(LauncherShellAction.UndoLastBackupImport), actions)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
