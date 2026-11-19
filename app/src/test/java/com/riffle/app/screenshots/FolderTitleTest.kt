package com.riffle.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.FOLDER_TITLE_FIELD_TEST_TAG
import com.riffle.app.launcher.FOLDER_TITLE_TEXT_TEST_TAG
import com.riffle.app.launcher.FolderSurface
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Naming a folder: in place in the open folder, and a new folder opens with its name ready to type. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class FolderTitleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val folder = FolderItem(LauncherItemId("f"), "Social", emptyList())

    private fun show(editOnOpen: Boolean = false) {
        composeRule.setContent {
            ScreenshotBackdrop {
                FolderSurface(
                    folder = folder,
                    layout = HomeLayoutDefaults.standard(),
                    installedApps = emptyList(),
                    appIconLoader = EmptyAppIconLoader,
                    onDismiss = {},
                    onAction = { actions += it },
                    editTitleOnOpen = editOnOpen,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aNewFolderOpensWithItsTitleInEditMode() {
        show(editOnOpen = true)
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun tappingTheTitleEditsItInPlaceAndDoneCommitsTheNewName() {
        show()
        composeRule.onNodeWithTag(FOLDER_TITLE_TEXT_TEST_TAG).performClick()
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).performTextInput("Chat")
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).performImeAction()
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.RenameHomeFolder(folder.id, "Chat")), actions)
        composeRule.onNodeWithTag(FOLDER_TITLE_TEXT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun anEmptyNameRevertsAndSendsNothing() {
        show()
        composeRule.onNodeWithTag(FOLDER_TITLE_TEXT_TEST_TAG).performClick()
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(FOLDER_TITLE_FIELD_TEST_TAG).performImeAction()
        composeRule.waitForIdle()

        assertEquals(emptyList<LauncherShellAction>(), actions)
        composeRule.onNodeWithTag(FOLDER_TITLE_TEXT_TEST_TAG).assertTextEquals("Social")
    }

    @Test
    fun thereIsNoSeparateRenameButtonOrNameField() {
        show()
        composeRule.onAllNodesWithTextCount("Save")
        composeRule.onAllNodesWithTextCount("Rename")
        composeRule.onAllNodesWithTextCount("Name")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) {
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)
    }
}
