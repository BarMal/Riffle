package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LocalWorkspacePreviewSetting
import com.riffle.app.launcher.LocalWorkspaceSettingsHost
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsWorkspacePreviewSection
import com.riffle.app.launcher.WorkspacePreviewSetting
import com.riffle.app.launcher.WorkspaceSettingsHost
import com.riffle.app.launcher.workspace.WorkspacesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Workspaces (preview) row at the top of Settings: absent without a preview host, off by default. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WorkspacePreviewSettingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private var opened = 0

    private fun render(setting: WorkspacePreviewSetting?) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalWorkspacePreviewSetting provides setting) {
                    SettingsWorkspacePreviewSection()
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun setting(enabled: Boolean) =
        WorkspacePreviewSetting(enabled = enabled, onEnabledChange = { changes += it }, onOpen = { opened++ })

    @Test
    fun offShowsOnlyTheSwitch() {
        render(setting(enabled = false))

        composeRule.onNodeWithText("Workspaces (preview)").assertIsDisplayed()
        composeRule.onNodeWithText("Open Workspaces (preview)").assertDoesNotExist()
    }

    @Test
    fun onShowsTheOpenRowAndItOpensThePreview() {
        render(setting(enabled = true))

        composeRule.onNodeWithText("Open Workspaces (preview)").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun tappingTheSwitchRowTurnsItOn() {
        render(setting(enabled = false))

        composeRule.onNodeWithText("Workspaces (preview)").performClick()

        assertEquals(listOf(true), changes)
    }

    @Test
    fun onWithTheSettingsHostOffersTheWorkspacesPage() {
        val pages = mutableListOf<SettingsPage>()
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalWorkspacePreviewSetting provides setting(enabled = true),
                    LocalWorkspaceSettingsHost provides settingsHost(),
                ) {
                    SettingsWorkspacePreviewSection(onPageSelected = { pages += it })
                }
            }
        }
        composeRule.captureScreen()

        composeRule.onNodeWithText("Workspaces").assertIsDisplayed().performClick()

        assertEquals(listOf(SettingsPage.WORKSPACES), pages)
    }

    @Test
    fun offTheSettingsPageIsNotOffered() {
        renderSection(setting(enabled = false), settingsHost())

        composeRule.onNodeWithText("Workspaces").assertDoesNotExist()
    }

    @Test
    fun withoutTheSettingsHostThePageIsNotOffered() {
        renderSection(setting(enabled = true), null)

        composeRule.onNodeWithText("Open Workspaces (preview)").assertIsDisplayed()
        composeRule.onNodeWithText("Workspaces").assertDoesNotExist()
    }

    private fun renderSection(
        setting: WorkspacePreviewSetting,
        host: WorkspaceSettingsHost?,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalWorkspacePreviewSetting provides setting,
                    LocalWorkspaceSettingsHost provides host,
                ) {
                    SettingsWorkspacePreviewSection()
                }
            }
        }
    }

    private fun settingsHost() =
        WorkspaceSettingsHost(
            workspaces = WorkspacesSettingsController(InMemoryWorkspaceRepository()),
            version = MutableStateFlow(0),
            currentLayout = HomeLayoutDeviceClass.PHONE,
            onEdit = {},
        )

    @Test
    fun withoutAHostNothingIsDrawn() {
        render(null)

        composeRule.onNodeWithText("Workspaces (preview)").assertDoesNotExist()
    }
}
