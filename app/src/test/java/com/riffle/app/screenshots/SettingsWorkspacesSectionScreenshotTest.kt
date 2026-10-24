package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LocalPoolHomeSetting
import com.riffle.app.launcher.LocalWorkspaceSettingsHost
import com.riffle.app.launcher.PoolHomeSetting
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsWorkspacesSection
import com.riffle.app.launcher.WorkspaceSettingsHost
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.app.launcher.workspace.WorkspacesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.SourceStatusMonitor
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Workspaces section at the top of Settings (S9): no "Workspaces (preview)" switch any more, the pages are always
 * offered where the shell provides them, and the one Developer fallback ("Home from placed items") stays.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SettingsWorkspacesSectionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()

    private fun render(
        host: WorkspaceSettingsHost?,
        poolHome: PoolHomeSetting? = null,
        onPageSelected: (SettingsPage) -> Unit = {},
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalWorkspaceSettingsHost provides host,
                    LocalPoolHomeSetting provides poolHome,
                ) {
                    SettingsWorkspacesSection(onPageSelected = onPageSelected)
                }
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun thePagesAreOfferedWithoutAnyPreviewSwitch() {
        val pages = mutableListOf<SettingsPage>()
        render(settingsHost(), onPageSelected = { pages += it })

        composeRule.onNodeWithText("Workspaces (preview)").assertDoesNotExist()
        composeRule.onNodeWithText("Open Workspaces (preview)").assertDoesNotExist()
        composeRule.onNodeWithText("Switch, rename, duplicate, delete, presets and copying between layouts")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Sources").assertIsDisplayed().performClick()

        assertEquals(listOf(SettingsPage.WORKSPACES, SettingsPage.SOURCES), pages)
    }

    @Test
    fun theDeveloperFallbackIsTheOnlyDeveloperRow() {
        render(
            settingsHost(),
            PoolHomeSetting(enabled = true, activeThisProcess = true, onEnabledChange = { changes += it }),
        )

        composeRule.onNodeWithText("Developer").assertIsDisplayed()
        composeRule.onNodeWithText("Home from placed items").assertIsDisplayed().performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun withoutAHostNothingIsDrawn() {
        render(null)

        composeRule.onNodeWithText("Sources").assertDoesNotExist()
        composeRule.onNodeWithText("Developer").assertDoesNotExist()
    }

    private fun settingsHost() =
        WorkspaceSettingsHost(
            workspaces = WorkspacesSettingsController(InMemoryWorkspaceRepository()),
            sources =
                SourcesSettingsController(
                    monitorFor = {
                            onChange ->
                        SourceStatusMonitor(FakeSourceRegistry(emptyList()), emptyList(), onChange)
                    },
                    enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore()),
                    descriptors = { emptyList() },
                ),
            version = MutableStateFlow(0),
            currentLayout = HomeLayoutDeviceClass.PHONE,
            onEdit = {},
            onRequestSourceAccess = {},
        )
}
