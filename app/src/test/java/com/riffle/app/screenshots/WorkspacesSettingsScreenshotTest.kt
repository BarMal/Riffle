package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.WORKSPACES_FALLBACK_TEST_TAG
import com.riffle.app.launcher.WorkspacesPageCallbacks
import com.riffle.app.launcher.WorkspacesSettingsContent
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Workspaces over fixed fake workspaces (no repository): the list at compact and unfolded widths
 * (list-detail), the fall-back notice, another layout being viewed, the last workspace, and the confirm,
 * preset-picker and copy dialogs. The page is drawn directly (it is only reachable with the preview on).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WorkspacesSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<WorkspacesSettingsAction>()
    private val edited = mutableListOf<WorkspaceId>()
    private val callbacks =
        WorkspacesPageCallbacks(onAction = { actions += it }, onEdit = { edited += it })

    @Test
    fun workspacesCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun workspacesCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun workspacesCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun workspacesUnfolded() {
        render()
    }

    @Test
    fun workspacesFallbackNotice() {
        render(WorkspacesSettingsFixtures.withFallback)

        composeRule.onNodeWithTag(WORKSPACES_FALLBACK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun workspacesOtherLayoutCannotBeEdited() {
        render(WorkspacesSettingsFixtures.otherLayout, viewed = WorkspacesSettingsFixtures.foldable)
    }

    @Test
    fun workspacesLastWorkspace() {
        render(WorkspacesSettingsFixtures.lastWorkspace)
    }

    @Test
    fun workspacesNotLoadedYet() {
        render(null)
    }

    @Test
    fun tappingARowSwitchesToIt() {
        render()

        composeRule.onNodeWithContentDescription("Work, ", substring = true).performClick()

        assertEquals(
            listOf<WorkspacesSettingsAction>(WorkspacesSettingsAction.Activate(WorkspacesSettingsFixtures.workId)),
            actions,
        )
    }

    @Test
    fun deleteAsksForConfirmationBeforeDoingAnything() {
        render(capture = false)

        composeRule.onNodeWithContentDescription("More actions for Work").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Delete workspace?").assertIsDisplayed()
        assertEquals(emptyList<WorkspacesSettingsAction>(), actions)
        composeRule.captureScreen()

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(
            listOf<WorkspacesSettingsAction>(WorkspacesSettingsAction.Delete(WorkspacesSettingsFixtures.workId)),
            actions,
        )
    }

    @Test
    fun theInstallPresetPickerListsEveryPresetWithNovaAsTheDefault() {
        render(capture = false)

        composeRule.onNodeWithText("Install preset...").performClick()

        // Some names also label rows of the page behind the dialog, so look only inside the dialog.
        listOf("Nova", "iOS", "TimeScape", "Niagara", "Kvaesitso").forEach { name ->
            composeRule.onNode(hasText(name) and hasAnyAncestor(isDialog())).assertExists()
        }
        composeRule.onNode(hasText("Default") and hasAnyAncestor(isDialog())).assertExists()
        composeRule.captureScreen()

        composeRule.onNodeWithText("Install").performClick()

        assertEquals(listOf<WorkspacesSettingsAction>(WorkspacesSettingsAction.InstallPreset("nova", false)), actions)
    }

    @Test
    fun theCopyDialogExplainsThatItIsAOneTimeCopy() {
        render(capture = false)

        composeRule.onNodeWithText("Copy from other layout...").performClick()

        composeRule.onNode(hasText("one-time copy", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        composeRule.captureScreen()

        composeRule.onNodeWithText("Replace and copy").performClick()

        assertEquals(
            listOf<WorkspacesSettingsAction>(WorkspacesSettingsAction.CopyFromLayout(HomeLayoutDeviceClass.FOLDABLE)),
            actions,
        )
    }

    private fun render(
        model: WorkspacesSettingsModel? = WorkspacesSettingsFixtures.compact,
        viewed: HomeLayoutDeviceClass = WorkspacesSettingsFixtures.phone,
        capture: Boolean = true,
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
                    WorkspacesSettingsContent(
                        model = model,
                        viewed = viewed,
                        tabs = WorkspacesSettingsFixtures.tabs,
                        callbacks = callbacks,
                    )
                }
            }
        }
        if (capture) composeRule.captureScreen()
    }
}
